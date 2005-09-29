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
package org.safehaus.penrose.cache;

import org.safehaus.penrose.mapping.*;

import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class InMemorySourceDataCache extends SourceDataCache {

    Map dataMap = new TreeMap();
    Map expirationMap = new LinkedHashMap();

    public Object get(Row pk) throws Exception {
        AttributeValues attributeValues = (AttributeValues)dataMap.get(pk);
        if (attributeValues == null) return null;

        Date date = (Date)expirationMap.get(pk);
        if (date == null || date.getTime() <= System.currentTimeMillis()) return null;

        return attributeValues;
    }

    public Map search(Collection filters) throws Exception {

        Map results = new TreeMap();

        for (Iterator i=dataMap.keySet().iterator(); i.hasNext(); ) {
            Row pk = (Row)i.next();

            AttributeValues attributeValues = (AttributeValues)get(pk);
            if (attributeValues == null) continue;

            for (Iterator j=filters.iterator(); j.hasNext(); ) {
                Row filter = (Row)j.next();

                boolean found = cacheContext.getSchema().partialMatch(attributeValues, filter);

                if (!found) continue;

                results.put(pk, attributeValues);
                break;
            }

        }

        return results;
    }

    public void put(Row pk, Object object) throws Exception {
        AttributeValues values = (AttributeValues)object;
        Row key = cacheContext.getSchema().normalize(pk);

        while (dataMap.get(key) == null && dataMap.size() >= size) {
            log.debug("Trimming source cache ("+dataMap.size()+").");
            Object k = expirationMap.keySet().iterator().next();
            dataMap.remove(k);
            expirationMap.remove(k);
        }

        log.debug("Storing source cache ("+dataMap.size()+"): "+key);
        dataMap.put(key, values);
        expirationMap.put(key, new Date(System.currentTimeMillis() + expiration * 60 * 1000));
    }

    public void remove(Row pk) throws Exception {

        Row key = cacheContext.getSchema().normalize(pk);

        log.debug("Removing source cache ("+dataMap.size()+"): "+key);
        dataMap.remove(key);
        expirationMap.remove(key);
    }
}
