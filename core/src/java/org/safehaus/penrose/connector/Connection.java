/**
 * Copyright (c) 2000-2005, Identyx Corporation.
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
package org.safehaus.penrose.connector;

import org.safehaus.penrose.connector.Adapter;
import org.safehaus.penrose.session.PenroseSearchResults;
import org.safehaus.penrose.session.PenroseSearchControls;
import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.filter.FilterTool;
import org.safehaus.penrose.partition.SourceConfig;
import org.safehaus.penrose.partition.ConnectionConfig;
import org.ietf.ldap.LDAPException;

import java.util.Collection;
import java.util.Map;

/**
 * @author Endi S. Dewata
 */
public class Connection implements ConnectionMBean {

    private ConnectionConfig connectionConfig;
    private AdapterConfig adapterConfig;
    private Adapter adapter;

    public Connection(ConnectionConfig connectionConfig, AdapterConfig adapterConfig) {
        this.connectionConfig = connectionConfig;
        this.adapterConfig = adapterConfig;
    }

    public String getName() {
        return connectionConfig.getName();
    }

    public void init() throws Exception {

        String adapterClass = adapterConfig.getAdapterClass();
        Class clazz = Class.forName(adapterClass);
        adapter = (Adapter)clazz.newInstance();

        adapter.setAdapterConfig(adapterConfig);
        adapter.setConnection(this);

        adapter.init();
    }

    public void close() throws Exception {
        if (adapter != null) adapter.dispose();
    }
    
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }

    public String getParameter(String name) {
        return connectionConfig.getParameter(name);
    }

    public Map getParameters() {
        return connectionConfig.getParameters();
    }
    
    public Collection getParameterNames() {
        return connectionConfig.getParameterNames();
    }

    public String removeParameter(String name) {
        return connectionConfig.removeParameter(name);
    }

    public String getConnectionName() {
        return connectionConfig.getName();
    }

    public int bind(SourceConfig sourceConfig, Row pk, String password) throws Exception {
        if (adapter == null) return LDAPException.OPERATIONS_ERROR;
        return adapter.bind(sourceConfig, pk, password);
    }

    public void search(SourceConfig sourceConfig, Filter filter, PenroseSearchControls sc, PenroseSearchResults results) throws Exception {
        if (adapter == null) {
            results.setReturnCode(LDAPException.OPERATIONS_ERROR);
            results.close();
            return;
        }
        adapter.search(sourceConfig, filter, sc, results);
    }

    public void load(SourceConfig sourceConfig, Filter filter, PenroseSearchControls sc, PenroseSearchResults results) throws Exception {
        if (adapter == null) {
            results.setReturnCode(LDAPException.OPERATIONS_ERROR);
            return;
        }
        adapter.load(sourceConfig, filter, sc, results);
    }

    public int add(SourceConfig sourceConfig, Row pk, AttributeValues sourceValues) throws Exception {
        if (adapter == null) return LDAPException.OPERATIONS_ERROR;
        return adapter.add(sourceConfig, pk, sourceValues);
    }

    public AttributeValues get(SourceConfig sourceConfig, Row pk) throws Exception {
        if (adapter == null) return null;

        Filter filter = FilterTool.createFilter(pk);
        PenroseSearchControls sc = new PenroseSearchControls();
        PenroseSearchResults sr = new PenroseSearchResults();

        adapter.load(sourceConfig, filter, sc, sr);

        if (!sr.hasNext()) return null;
        return (AttributeValues)sr.next();
    }

    public int modify(SourceConfig sourceConfig, Row pk, Collection modifications) throws Exception {
        if (adapter == null) return LDAPException.OPERATIONS_ERROR;
        return adapter.modify(sourceConfig, pk, modifications);
    }

    public int delete(SourceConfig sourceConfig, Row pk) throws Exception {
        if (adapter == null) return LDAPException.OPERATIONS_ERROR;
        return adapter.delete(sourceConfig, pk);
    }

    public int getLastChangeNumber(SourceConfig sourceConfig) throws Exception {
        if (adapter == null) return -1;
        return adapter.getLastChangeNumber(sourceConfig);
    }

    public PenroseSearchResults getChanges(SourceConfig sourceConfig, int lastChangeNumber) throws Exception {
        if (adapter == null) return null;
        return adapter.getChanges(sourceConfig, lastChangeNumber);
    }

    public Object openConnection() throws Exception {
        if (adapter == null) return null;
        return adapter.openConnection();
    }

    public AdapterConfig getAdapterConfig() {
        return adapterConfig;
    }

    public void setAdapterConfig(AdapterConfig adapterConfig) {
        this.adapterConfig = adapterConfig;
    }
}