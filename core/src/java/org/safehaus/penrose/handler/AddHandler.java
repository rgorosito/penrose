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
package org.safehaus.penrose.handler;

import org.safehaus.penrose.session.PenroseSession;
import org.safehaus.penrose.session.PenroseSearchResults;
import org.safehaus.penrose.session.PenroseSearchControls;
import org.safehaus.penrose.partition.Partition;
import org.safehaus.penrose.partition.PartitionManager;
import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.util.EntryUtil;
import org.ietf.ldap.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.NamingEnumeration;
import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class AddHandler {

    Logger log = LoggerFactory.getLogger(getClass());

    private Handler handler;

    public AddHandler(Handler handler) {
        this.handler = handler;
    }

    public int add(
            PenroseSession session,
            String dn,
            Attributes attributes)
    throws Exception {

        int rc;
        try {
            log.warn("Add entry \""+dn+"\".");
            log.debug("-------------------------------------------------");
            log.debug("ADD:");
            if (session != null && session.getBindDn() != null) log.debug(" - Bind DN: "+session.getBindDn());
            log.debug(" - Entry: "+dn);
            log.debug("");

            rc = performAdd(session, dn, attributes);
            if (rc != LDAPException.SUCCESS) return rc;

            // refreshing entry cache

            PenroseSession adminSession = handler.getPenrose().newSession();
            adminSession.setBindDn(handler.getPenroseConfig().getRootDn());

            PenroseSearchResults results = new PenroseSearchResults();

            PenroseSearchControls sc = new PenroseSearchControls();
            sc.setScope(PenroseSearchControls.SCOPE_SUB);

            adminSession.search(
                    dn,
                    "(objectClass=*)",
                    sc,
                    results
            );

            while (results.hasNext()) results.next();

        } catch (LDAPException e) {
            rc = e.getResultCode();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            rc = LDAPException.OPERATIONS_ERROR;
        }

        if (rc == LDAPException.SUCCESS) {
            log.warn("Add operation succeded.");
        } else {
            log.warn("Add operation failed. RC="+rc);
        }

        return rc;
    }

    public int performAdd(
            PenroseSession session,
            String dn,
            Attributes attributes)
    throws Exception {

        dn = LDAPDN.normalize(dn);

        // find parent entry
        String parentDn = EntryUtil.getParentDn(dn);
        Entry parent = getHandler().getFindHandler().find(session, parentDn);
        if (parent == null) {
            log.debug("Parent entry "+parentDn+" not found");
            return LDAPException.NO_SUCH_OBJECT;
        }

        int rc = handler.getACLEngine().checkAdd(session, parentDn, parent.getEntryMapping());
        if (rc != LDAPException.SUCCESS) {
            log.debug("Not allowed to add children under "+parentDn);
            return rc;
        }

        log.debug("Adding entry under "+parentDn);

        EntryMapping parentMapping = parent.getEntryMapping();
        PartitionManager partitionManager = handler.getPartitionManager();
        Partition partition = partitionManager.getPartition(parentMapping);

        if (partition.isProxy(parentMapping)) {
            return handler.getEngine("PROXY").add(partition, parent, parentMapping, dn, attributes);
        }

        Collection children = partition.getChildren(parentMapping);

        for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
            EntryMapping entryMapping = (EntryMapping)iterator.next();
            if (!partition.isDynamic(entryMapping)) continue;

            return handler.getEngine().add(partition, parent, entryMapping, dn, attributes);
        }

        return addStaticEntry(parentMapping, dn, attributes);
    }

    public Handler getHandler() {
        return handler;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public int addStaticEntry(EntryMapping parent, String dn, Attributes attributes) throws Exception {
        log.debug("Adding static entry "+dn);

        AttributeValues values = new AttributeValues();

        for (NamingEnumeration i=attributes.getAll(); i.hasMore(); ) {
            Attribute attribute = (Attribute)i.next();
            String attributeName = attribute.getID();

            for (NamingEnumeration j=attribute.getAll(); j.hasMore(); ) {
                Object value = j.next();
                values.set(attributeName, value);
            }
        }

        EntryMapping newEntry;

        Row rdn = EntryUtil.getRdn(dn);

        if (parent == null) {
            newEntry = new EntryMapping(dn);

        } else {
            newEntry = new EntryMapping(rdn.toString(), parent);
        }

        Partition partition = handler.getPartitionManager().getPartitionByDn(dn);
        if (partition == null) return LDAPException.NO_SUCH_OBJECT;

        partition.addEntryMapping(newEntry);

        Collection objectClasses = newEntry.getObjectClasses();
        //Collection attributes = newEntry.getAttributeMappings();

        for (Iterator iterator=values.getNames().iterator(); iterator.hasNext(); ) {
            String name = (String)iterator.next();
            Set set = (Set)values.get(name);

            if ("objectclass".equals(name.toLowerCase())) {
                for (Iterator j=set.iterator(); j.hasNext(); ) {
                    String value = (String)j.next();
                    if (!objectClasses.contains(name)) {
                        objectClasses.add(value);
                        log.debug("Add objectClass: "+value);
                    }
                }

                continue;
            }

            for (Iterator j=set.iterator(); j.hasNext(); ) {
                String value = (String)j.next();

                AttributeMapping newAttribute = new AttributeMapping();
                newAttribute.setName(name);
                newAttribute.setConstant(value);
                newAttribute.setRdn(rdn.contains(name));

                log.debug("Add attribute "+name+": "+value);
                newEntry.addAttributeMapping(newAttribute);
            }
        }

        log.debug("New entry "+dn+" has been added.");

        return LDAPException.SUCCESS;
    }
}