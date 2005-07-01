/**
 * Copyright (c) 1998-2005, Verge Lab., LLC.
 * All rights reserved.
 */
package org.safehaus.penrose.cache.impl;

import org.safehaus.penrose.mapping.Row;
import org.safehaus.penrose.mapping.AttributeDefinition;
import org.safehaus.penrose.mapping.EntryDefinition;
import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.cache.EntryCache;

import javax.sql.DataSource;
import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class DefaultEntryCache extends EntryCache {

    public DefaultCache cache;

    public Map homes = new HashMap();

    private DataSource ds;

    public void init() throws Exception {

        cache = (DefaultCache)super.getCache();
        ds = cache.getDs();

        Collection entries = getConfig().getEntryDefinitions();

        for (Iterator i=entries.iterator(); i.hasNext(); ) {
            EntryDefinition entryDefinition = (EntryDefinition)i.next();

            createTables(entryDefinition);
        }
    }

    public String getEntryTableName(EntryDefinition entry) {
        String dn = entry.getDn();
		dn = dn.replace('=', '_');
		dn = dn.replace(',', '_');
		dn = dn.replace(' ', '_');
		dn = dn.replace('.', '_');

        String hashCode = ""+entry.getDn().hashCode();
        hashCode = hashCode.replace('-', '_');
		return "entry"+hashCode;
	}

    public String getEntryAttributeTableName(EntryDefinition entry) {
        String hashCode = ""+entry.getDn().hashCode();
        hashCode = hashCode.replace('-', '_');
		return "attribute"+hashCode;
	}

    public void createTables(EntryDefinition entryDefinition) throws Exception {
        String entryTableName = getEntryTableName(entryDefinition);
        EntryHome entryHome = new EntryHome(ds, cache, entryDefinition, entryTableName);
        homes.put(entryTableName, entryHome);

        String entryAttributeTableName = getEntryAttributeTableName(entryDefinition);
        EntryAttributeHome entryAttributeHome = new EntryAttributeHome(ds, cache, entryDefinition, entryAttributeTableName);
        homes.put(entryAttributeTableName, entryAttributeHome);
    }

    public EntryHome getEntryHome(EntryDefinition entryDefinition) throws Exception {
        String tableName = getEntryTableName(entryDefinition);
        return (EntryHome)homes.get(tableName);
    }

    public EntryAttributeHome getEntryAttributeHome(EntryDefinition entryDefinition) throws Exception {
        String tableName = getEntryAttributeTableName(entryDefinition);
        return (EntryAttributeHome)homes.get(tableName);
    }

    public Entry get(EntryDefinition entryDefinition, Row rdn) throws Exception {

        EntryAttributeHome entryAttributeHome = getEntryAttributeHome(entryDefinition);
        log.debug("Getting entry cache for rdn: "+rdn);

        Collection rows = entryAttributeHome.search(rdn);
        if (rows.size() == 0) return null;

        log.debug("Attributes:");

        AttributeValues attributeValues = new AttributeValues();
        for (Iterator i = rows.iterator(); i.hasNext();) {
            Row row = (Row)i.next();

            String name = (String)row.getNames().iterator().next();
            Object value = row.get(name);

            log.debug(" - "+name+": "+value);

            Collection values = (Collection)attributeValues.get(name);
            if (values == null) {
                values = new ArrayList();
                attributeValues.set(name, values);
            }
            values.add(value);
        }

        Entry sr = new Entry(entryDefinition, attributeValues);
        return sr;
    }

    public Map get(EntryDefinition entryDefinition, Collection rdns) throws Exception {

        Map entries = new HashMap();

        for (Iterator i=rdns.iterator(); i.hasNext(); ) {
            Row rdn = (Row)i.next();

            Entry entry = get(entryDefinition, rdn);
            if (entry == null) continue;

            entries.put(rdn, entry);
        }

        return entries;
    }

    public Row getRdn(EntryDefinition entry, AttributeValues attributeValues) throws Exception {
        Row rdn = new Row();
        Collection rdnAttributes = entry.getRdnAttributes();

        for (Iterator i = rdnAttributes.iterator(); i.hasNext();) {
            AttributeDefinition attributeDefinition = (AttributeDefinition) i.next();
            String name = attributeDefinition.getName();
            Collection values = attributeValues.get(name);

            Object value = values.iterator().next();
            rdn.set(name, value);
        }

        return rdn;
    }

    public void put(EntryDefinition entryDefinition, AttributeValues values, Date date) throws Exception {
        Row pk = getRdn(entryDefinition, values);

        EntryHome entryHome = getEntryHome(entryDefinition);
        entryHome.insert(pk, date);

        EntryAttributeHome entryAttributeHome = getEntryAttributeHome(entryDefinition);

        for (Iterator i=values.getNames().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            Collection c = values.get(name);
            if (c == null) continue;

            for (Iterator j=c.iterator(); j.hasNext(); ) {
                Object value = j.next();
                if (value == null) continue;
                entryAttributeHome.insert(pk, name, value);
            }
        }
    }

    public void remove(EntryDefinition entryDefinition, AttributeValues values) throws Exception {
        Row pk = getRdn(entryDefinition, values);

        EntryHome entryHome = getEntryHome(entryDefinition);
        entryHome.delete(pk);

        EntryAttributeHome entryAttributeHome = getEntryAttributeHome(entryDefinition);
        entryAttributeHome.delete(pk);
    }

    /**
     * @param entryDefinition
     * @param rdns
     * @return loaded primary keys
     * @throws Exception
     */
    public Collection getRdns(
            EntryDefinition entryDefinition,
            Collection rdns,
            Date date)
            throws Exception {

        EntryHome entryHome = getEntryHome(entryDefinition);
        Collection expiredRdns = entryHome.getExpiredRdns(date);
        entryHome.delete(expiredRdns);

        EntryAttributeHome entryAttributeHome = getEntryAttributeHome(entryDefinition);
        entryAttributeHome.delete(expiredRdns);

        return entryHome.search(rdns);
    }
}
