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
package org.safehaus.penrose.engine;

import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.graph.GraphVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ietf.ldap.LDAPException;

import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class DeleteGraphVisitor extends GraphVisitor {

    Logger log = LoggerFactory.getLogger(getClass());

    public EngineContext engineContext;
    public EntryDefinition entryDefinition;

    private int returnCode = LDAPException.SUCCESS;

    private Stack stack = new Stack();

    public DeleteGraphVisitor(
            EngineContext engineContext,
            EntryDefinition entryDefinition,
            AttributeValues sourceValues
            ) throws Exception {

        this.engineContext = engineContext;
        this.entryDefinition = entryDefinition;

        stack.push(sourceValues);
    }

    public boolean preVisitNode(Object node, Object parameter) throws Exception {
        Source source = (Source)node;
        AttributeValues sourceValues = (AttributeValues)stack.peek();

        log.debug("Deleting "+source.getName()+" with: "+sourceValues);

        if (!source.isIncludeOnDelete()) {
            log.debug("Source "+source.getName()+" is not included on delete");
            return true;
        }

        if (entryDefinition.getSource(source.getName()) == null) {
            log.debug("Source "+source.getName()+" is not defined in entry "+entryDefinition.getDn());
            return false;
        }

        AttributeValues newSourceValues = new AttributeValues();
        for (Iterator i=sourceValues.getNames().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            Collection values = sourceValues.get(name);

            if (!name.startsWith(source.getName()+".")) continue;

            name = name.substring(source.getName().length()+1);
            newSourceValues.set(name, values);
        }

        returnCode = engineContext.getSyncService().delete(source, newSourceValues);

        if (returnCode == LDAPException.NO_SUCH_OBJECT) return true; // ignore
        if (returnCode != LDAPException.SUCCESS) return false;

        return true;
    }

    public boolean preVisitEdge(Object node1, Object node2, Object edge, Object parameter) throws Exception {
        Source source = (Source)node2;
        Relationship relationship = (Relationship)edge;

        log.debug("Relationship "+relationship);
        if (entryDefinition.getSource(source.getName()) == null) return false;

        AttributeValues sourceValues = (AttributeValues)stack.peek();

        String lhs = relationship.getLhs();
        String rhs = relationship.getRhs();

        if (lhs.startsWith(source.getName()+".")) {
            String exp = lhs;
            lhs = rhs;
            rhs = exp;
        }

        Collection lhsValues = sourceValues.get(lhs);
        log.debug(" - "+lhs+" -> "+rhs+": "+lhsValues);

        AttributeValues newSourceValues = new AttributeValues();
        newSourceValues.add(sourceValues);
        newSourceValues.set(rhs, lhsValues);

        stack.push(newSourceValues);

        return true;
    }

    public void postVisitEdge(Object node1, Object node2, Object edge, Object parameter) throws Exception {
        stack.pop();
    }

    public int getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }
}
