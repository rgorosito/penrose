/**
 * Copyright (c) 2000-2006, Identyx Corporation.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.safehaus.penrose.handler;

import org.safehaus.penrose.session.*;
import org.safehaus.penrose.acl.ACLManager;
import org.safehaus.penrose.filter.FilterTool;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.partition.Partition;
import org.safehaus.penrose.schema.SchemaManager;
import org.safehaus.penrose.schema.AttributeType;
import org.safehaus.penrose.schema.matchingRule.EqualityMatchingRule;
import org.safehaus.penrose.engine.Engine;
import org.safehaus.penrose.engine.EngineManager;
import org.safehaus.penrose.interpreter.InterpreterManager;
import org.safehaus.penrose.event.*;
import org.safehaus.penrose.mapping.EntryMapping;
import org.safehaus.penrose.entry.AttributeValues;
import org.safehaus.penrose.config.PenroseConfig;
import org.safehaus.penrose.thread.ThreadManager;
import org.safehaus.penrose.naming.PenroseContext;
import org.safehaus.penrose.entry.*;
import org.safehaus.penrose.cache.EntryCache;
import org.safehaus.penrose.cache.CacheConfig;
import org.safehaus.penrose.util.ExceptionUtil;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.ietf.ldap.LDAPException;

import javax.naming.directory.*;
import java.util.*;

/**
 * @author Endi S. Dewata
 */
public abstract class Handler {

    public Logger log = LoggerFactory.getLogger(getClass());

    public final static String STOPPED  = "STOPPED";
    public final static String STARTING = "STARTING";
    public final static String STARTED  = "STARTED";
    public final static String STOPPING = "STOPPING";

    protected PenroseConfig      penroseConfig;
    protected PenroseContext     penroseContext;

    protected HandlerConfig      handlerConfig;

    protected ThreadManager      threadManager;
    protected SchemaManager      schemaManager;
    protected InterpreterManager interpreterManager;

    protected SessionManager     sessionManager;
    protected HandlerManager     handlerManager;

    protected EngineManager      engineManager;
    protected ACLManager         aclManager;

    protected FilterTool         filterTool;
    protected EntryCache         entryCache;

    protected String status = STOPPED;

    public Handler() throws Exception {
    }

    public void init(HandlerConfig handlerConfig) throws Exception {
        this.handlerConfig = handlerConfig;

        threadManager      = penroseContext.getThreadManager();
        schemaManager      = penroseContext.getSchemaManager();
        interpreterManager = penroseContext.getInterpreterManager();

        sessionManager     = penroseContext.getSessionManager();
        handlerManager     = penroseContext.getHandlerManager();

        engineManager      = penroseContext.getEngineManager();
        aclManager         = penroseContext.getAclManager();


        CacheConfig cacheConfig = penroseConfig.getEntryCacheConfig();
        String cacheClass = cacheConfig.getCacheClass() == null ? EntryCache.class.getName() : cacheConfig.getCacheClass();

        //log.debug("Initializing entry cache "+cacheClass);
        Class clazz = Class.forName(cacheClass);
        entryCache = (EntryCache)clazz.newInstance();

        entryCache.setCacheConfig(cacheConfig);
        entryCache.setPenroseConfig(penroseConfig);
        entryCache.setPenroseContext(penroseContext);
        entryCache.init();
    }

    public void start() throws Exception {

        if (status != STOPPED) return;

        //log.debug("Starting SessionHandler...");

        try {
            status = STARTING;

            filterTool = new FilterTool();
            filterTool.setSchemaManager(schemaManager);

            status = STARTED;

            //log.debug("SessionHandler started.");

        } catch (Exception e) {
            status = STOPPED;
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    public void stop() throws Exception {

        if (status != STARTED) return;

        try {
            status = STOPPING;

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        status = STOPPED;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void add(PenroseSession session, Partition partition, EntryMapping entryMapping, DN dn, Attributes attributes) throws Exception {

        Engine engine = getEngine(entryMapping);

        if (engine == null) {
            log.debug("Engine "+entryMapping.getEngineName()+" not found.");
            throw ExceptionUtil.createLDAPException(LDAPException.OPERATIONS_ERROR);
        }

        Entry parent = null; //find(session, partition, entryMapping.getParent(), dn.getParentDn());
        engine.add(session, partition, parent, entryMapping, dn, attributes);
    }

    public void bind(
            PenroseSession session,
            Partition partition,
            EntryMapping entryMapping,
            DN dn,
            String password
    ) throws Exception {

        Engine engine = getEngine(entryMapping);

        if (engine == null) {
            log.debug("Engine "+entryMapping.getEngineName()+" not found.");
            throw ExceptionUtil.createLDAPException(LDAPException.OPERATIONS_ERROR);
        }

        Entry entry = null; //find(session, partition, entryMapping, dn);
        engine.bind(session, partition, entryMapping, dn, password);
    }

    public void unbind(PenroseSession session, Partition partition, EntryMapping entryMapping, DN bindDn) throws Exception {

        Engine engine = getEngine(entryMapping);

        if (engine == null) {
            log.debug("Engine "+entryMapping.getEngineName()+" not found.");
            throw ExceptionUtil.createLDAPException(LDAPException.OPERATIONS_ERROR);
        }

        Entry entry = null; //find(session, partition, entryMapping, dn);
        //engine.unbind(session, partition, entryMapping, bindDn);
    }

    public Entry find(PenroseSession session, Partition partition, EntryMapping entryMapping, DN dn) throws Exception {

        boolean debug = log.isDebugEnabled();

        PenroseSearchControls sc = new PenroseSearchControls();
        sc.setScope(PenroseSearchControls.SCOPE_BASE);

        PenroseSearchResults results = new PenroseSearchResults();

        search(
                session,
                partition,
                entryMapping,
                dn,
                null,
                sc,
                results
        );

        if (results.hasNext()) {
            if (debug) log.debug("Entry "+dn+" not found");
            throw ExceptionUtil.createLDAPException(LDAPException.NO_SUCH_OBJECT);
        }

        return (Entry)results.next();
    }

    public boolean compare(PenroseSession session, Partition partition, EntryMapping entryMapping, DN dn, String attributeName, Object attributeValue) throws Exception {

        boolean debug = log.isDebugEnabled();

        Entry entry = find(session, partition, entryMapping, dn);

        List attributeNames = new ArrayList();
        attributeNames.add(attributeName);

        AttributeValues attributeValues = entry.getAttributeValues();
        Collection values = attributeValues.get(attributeName);
        if (values == null) {
            if (debug) log.debug("Attribute "+attributeName+" not found.");
            return false;
        }

        AttributeType attributeType = schemaManager.getAttributeType(attributeName);

        String equality = attributeType == null ? null : attributeType.getEquality();
        EqualityMatchingRule equalityMatchingRule = EqualityMatchingRule.getInstance(equality);

        if (debug) log.debug("Comparing values:");
        for (Iterator i=values.iterator(); i.hasNext(); ) {
            Object value = i.next();

            boolean b = equalityMatchingRule.compare(value, attributeValue);
            if (debug) log.debug(" - ["+value+"] => "+b);

            if (b) return true;

        }

        return false;
    }

    public void delete(PenroseSession session, Partition partition, EntryMapping entryMapping, DN dn) throws Exception {

        Engine engine = getEngine(entryMapping);

        if (engine == null) {
            log.debug("Engine "+entryMapping.getEngineName()+" not found.");
            throw ExceptionUtil.createLDAPException(LDAPException.OPERATIONS_ERROR);
        }

        Entry entry = null; //find(session, partition, entryMapping, dn);
        engine.delete(session, partition, entry, entryMapping, dn);
    }

    public void modify(PenroseSession session, Partition partition, EntryMapping entryMapping, DN dn, Collection modifications) throws Exception {

        Engine engine = getEngine(entryMapping);

        if (engine == null) {
            log.debug("Engine "+entryMapping.getEngineName()+" not found.");
            throw ExceptionUtil.createLDAPException(LDAPException.OPERATIONS_ERROR);
        }

        Entry entry = null; //find(session, partition, entryMapping, dn);
        engine.modify(session, partition, entry, entryMapping, dn, modifications);
    }

    public void modrdn(PenroseSession session, Partition partition, EntryMapping entryMapping, DN dn, RDN newRdn, boolean deleteOldRdn) throws Exception {

        Engine engine = getEngine(entryMapping);

        if (engine == null) {
            log.debug("Engine "+entryMapping.getEngineName()+" not found.");
            throw ExceptionUtil.createLDAPException(LDAPException.OPERATIONS_ERROR);
        }

        Entry entry = null; //find(session, partition, entryMapping, dn);
        engine.modrdn(session, partition, entry, entryMapping, dn, newRdn, deleteOldRdn);
    }

    public void search(
            PenroseSession session,
            Partition partition,
            EntryMapping entryMapping,
            DN baseDn,
            Filter filter,
            PenroseSearchControls sc,
            Results results
    ) throws Exception {

        search(
                session,
                partition,
                entryMapping,
                entryMapping,
                baseDn,
                filter,
                sc,
                results
        );
    }

    public abstract void search(
            PenroseSession session,
            Partition partition,
            EntryMapping baseMapping,
            EntryMapping entryMapping,
            DN baseDn,
            Filter filter,
            PenroseSearchControls sc,
            Results results
    ) throws Exception;

    public PenroseConfig getPenroseConfig() {
        return penroseConfig;
    }

    public void setPenroseConfig(PenroseConfig penroseConfig) {
        this.penroseConfig = penroseConfig;
    }

    public EngineManager getEngineManager() {
        return engineManager;
    }

    public void setEngineManager(EngineManager engineManager) {
        this.engineManager = engineManager;
    }

    public FilterTool getFilterTool() {
        return filterTool;
    }

    public void setFilterTool(FilterTool filterTool) {
        this.filterTool = filterTool;
    }

    public Engine getEngine(EntryMapping entryMapping) {
        return engineManager.getEngine(entryMapping.getEngineName());
    }

    public SchemaManager getSchemaManager() {
        return schemaManager;
    }

    public void setSchemaManager(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public InterpreterManager getInterpreterFactory() {
        return interpreterManager;
    }

    public void setInterpreterFactory(InterpreterManager interpreterManager) {
        this.interpreterManager = interpreterManager;
    }

    public EntryCache getEntryCache() {
        return entryCache;
    }

    public void setEntryCache(EntryCache entryCache) {
        this.entryCache = entryCache;
    }

    public void addConnectionListener(ConnectionListener l) {
    }

    public void removeConnectionListener(ConnectionListener l) {
    }

    public void addBindListener(BindListener l) {
    }

    public void removeBindListener(BindListener l) {
    }

    public void addSearchListener(SearchListener l) {
    }

    public void removeSearchListener(SearchListener l) {
    }

    public void addCompareListener(CompareListener l) {
    }

    public void removeCompareListener(CompareListener l) {
    }

    public void addAddListener(AddListener l) {
    }

    public void removeAddListener(AddListener l) {
    }

    public void addDeleteListener(DeleteListener l) {
    }

    public void removeDeleteListener(DeleteListener l) {
    }

    public void addModifyListener(ModifyListener l) {
    }

    public void removeModifyListener(ModifyListener l) {
    }

    public HandlerConfig getHandlerConfig() {
        return handlerConfig;
    }

    public void setHandlerConfig(HandlerConfig handlerConfig) {
        this.handlerConfig = handlerConfig;
    }

    public PenroseContext getPenroseContext() {
        return penroseContext;
    }

    public void setPenroseContext(PenroseContext penroseContext) {
        this.penroseContext = penroseContext;
    }
}

