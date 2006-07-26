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
import org.safehaus.penrose.mapping.Entry;
import org.safehaus.penrose.mapping.EntryMapping;
import org.safehaus.penrose.cache.EntryCache;
import org.safehaus.penrose.util.EntryUtil;
import org.ietf.ldap.LDAPException;
import org.ietf.ldap.LDAPDN;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Iterator;

/**
 * @author Endi S. Dewata
 */
public class ModRdnHandler {

    Logger log = LoggerFactory.getLogger(getClass());

    public Handler handler;

	public ModRdnHandler(Handler handler) {
        this.handler = handler;
	}

	public int modrdn(PenroseSession session, String dn, String newRdn)
			throws Exception {

        int rc;
        try {

            log.warn("ModRDN \""+dn+"\" to \""+newRdn+"\".");

            log.debug("-------------------------------------------------------------------------------");
            log.debug("MODRDN:");
            if (session != null && session.getBindDn() != null) log.debug(" - Bind DN: " + session.getBindDn());
            log.debug(" - DN: " + dn);
            log.debug(" - New RDN: " + newRdn);

            String ndn = LDAPDN.normalize(dn);

            Entry entry = handler.getFindHandler().find(session, ndn);
            if (entry == null) return LDAPException.NO_SUCH_OBJECT;

            rc = performModRdn(session, entry, newRdn);
            if (rc != LDAPException.SUCCESS) return rc;

            // refreshing entry cache

            String parentDn = EntryUtil.getParentDn(dn);
            String newDn = newRdn+","+parentDn;

            PenroseSearchResults results = new PenroseSearchResults();

            PenroseSearchControls sc = new PenroseSearchControls();
            sc.setScope(PenroseSearchControls.SCOPE_SUB);

            handler.getSearchHandler().search(
                    null,
                    newDn,
                    "(objectClass=*)",
                    sc,
                    results
            );

            EntryCache entryCache = handler.getEngine().getEntryCache();
            for (Iterator i=results.iterator(); i.hasNext(); ) {
                Entry e = (Entry)i.next();
                entryCache.put(e);
            }

            handler.getEngine().getEntryCache().remove(entry);

        } catch (LDAPException e) {
            rc = e.getResultCode();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            rc = LDAPException.OPERATIONS_ERROR;
        }

        if (rc == LDAPException.SUCCESS) {
            log.warn("ModRDN operation succeded.");
        } else {
            log.warn("ModRDN operation failed. RC="+rc);
        }

        return rc;
	}

    public int performModRdn(
            PenroseSession session,
            Entry entry,
            String newRdn)
			throws Exception {

        int rc = handler.getACLEngine().checkModify(session, entry.getDn(), entry.getEntryMapping());
        if (rc != LDAPException.SUCCESS) return rc;

        EntryMapping entryMapping = entry.getEntryMapping();
        PartitionManager partitionManager = handler.getPartitionManager();
        Partition partition = partitionManager.getPartition(entryMapping);

        if (partition.isProxy(entryMapping)) {
            return handler.getEngine("PROXY").modrdn(partition, entry, newRdn);

        } else if (partition.isDynamic(entryMapping)) {
            return handler.getEngine().modrdn(partition, entry, newRdn);

        } else {
            return modRdnStaticEntry(partition, entry, newRdn);
        }
	}

    public int modRdnStaticEntry(
            Partition partition,
            Entry entry,
            String newRdn)
			throws Exception {

        EntryMapping entryMapping = entry.getEntryMapping();
        partition.renameEntryMapping(entryMapping, newRdn);

        return LDAPException.SUCCESS;
    }
}