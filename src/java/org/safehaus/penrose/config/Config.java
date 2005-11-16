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
package org.safehaus.penrose.config;

import java.io.Serializable;
import java.util.*;

import org.apache.log4j.Logger;
import org.safehaus.penrose.module.ModuleMapping;
import org.safehaus.penrose.module.ModuleConfig;
import org.safehaus.penrose.mapping.*;

public class Config implements Serializable {

    Logger log = Logger.getLogger(getClass());

    private Map entryDefinitions = new TreeMap();
    private Collection rootEntryDefinitions = new ArrayList();
    private Map childrenMap = new TreeMap();

    private Map connectionConfigs = new LinkedHashMap();

    private Map moduleConfigs = new LinkedHashMap();
    private Map moduleMappings = new LinkedHashMap();

    public Config() {
    }

	public void addEntryDefinition(EntryDefinition entry) throws Exception {

        String dn = entry.getDn();

        if (entryDefinitions.get(dn) != null) throw new Exception("Entry "+dn+" already exists.");

        //System.out.println("Adding "+dn+".");

        EntryDefinition parent = getParent(entry);

        if (parent != null) { // parent found
            //System.out.println("Found parent "+parentDn+".");

            Collection children = getChildren(parent);
            if (children == null) {
                children = new ArrayList();
                setChildren(parent, children);
            }
            children.add(entry);
        }

        entryDefinitions.put(dn, entry);

        if (parent == null) {
        	rootEntryDefinitions.add(entry);
        }
/*
        for (Iterator j=entry.getSources().iterator(); j.hasNext(); ) {
            Source source = (Source)j.next();

            String sourceName = source.getSourceName();
            String connectionName = source.getConnectionName();

            ConnectionConfig connection = getConnectionConfig(connectionName);
            if (connection == null) throw new Exception("Connection "+connectionName+" undefined.");

            SourceDefinition sourceDefinition = connection.getSourceDefinition(sourceName);
            if (sourceDefinition == null) throw new Exception("Source "+sourceName+" undefined.");

            Collection fieldConfigs = sourceDefinition.getFieldDefinitions();

            for (Iterator k=fieldConfigs.iterator(); k.hasNext(); ) {
                FieldDefinition fieldConfig = (FieldDefinition)k.next();
                String fieldName = fieldConfig.getName();

                // define any missing fields
                Field field = (Field)source.getField(fieldName);
                if (field != null) continue;

                field = new Field();
                field.setName(fieldName);
                source.addField(field);
            }
        }
*/
    }

    public void modifyEntryDefinition(String dn, EntryDefinition newEntry) {
        EntryDefinition entry = getEntryDefinition(dn);
        entry.copy(newEntry);
    }

    public EntryDefinition removeEntryDefinition(EntryDefinition entry) {
        EntryDefinition parent = getParent(entry);
        if (parent == null) {
            rootEntryDefinitions.remove(entry);

        } else {
            Collection children = getChildren(parent);
            if (children != null) children.remove(entry);
        }

        return (EntryDefinition)entryDefinitions.remove(entry.getDn());
    }

    public void renameEntryDefinition(EntryDefinition entry, String newDn) {
    	if (entry == null) return;
    	if (entry.getDn().equals(newDn)) return;

        EntryDefinition oldParent = getParent(entry);
    	String oldDn = entry.getDn();

    	entry.setDn(newDn);
        entryDefinitions.put(newDn, entry);

        EntryDefinition newParent = getParent(entry);

        if (newParent != null) {
            Collection newSiblings = getChildren(newParent);
            if (newSiblings == null) {
                newSiblings = new ArrayList();
                setChildren(newParent, newSiblings);
            }
            newSiblings.add(entry);
        }

        Collection children = getChildren(oldDn);

        if (children != null) {
            setChildren(newDn, children);

            for (Iterator i=children.iterator(); i.hasNext(); ) {
                EntryDefinition child = (EntryDefinition)i.next();
                String childNewDn = child.getRdn()+","+newDn;
                //System.out.println(" - renaming child "+child.getDn()+" to "+childNewDn);

                renameChildren(child, childNewDn);
            }

            removeChildren(oldDn);
        }

        entryDefinitions.remove(oldDn);

        if (oldParent != null) {
            Collection oldSiblings = getChildren(oldParent);
            if (oldSiblings != null) oldSiblings.remove(entry);
        }

    }

    public void renameChildren(EntryDefinition entry, String newDn) {
    	if (entry == null) return;

    	if (newDn.equals(entry.getDn())) return;

        String oldDn = entry.getDn();

        entry.setDn(newDn);
        entryDefinitions.put(newDn, entry);

        Collection children = getChildren(oldDn);

        if (children != null) {
            setChildren(newDn, children);

            for (Iterator i=children.iterator(); i.hasNext(); ) {
                EntryDefinition child = (EntryDefinition)i.next();
                String childNewDn = child.getRdn()+","+newDn;
                //System.out.println(" - renaming child "+child.getDn()+" to "+childNewDn);

                renameChildren(child, childNewDn);
            }

            removeChildren(oldDn);
        }

        entryDefinitions.remove(oldDn);
    }

    public EntryDefinition getParent(EntryDefinition entryDefinition) {
        if (entryDefinition == null) return null;
        String parentDn = entryDefinition.getParentDn();
        return getEntryDefinition(parentDn);
    }

    public Collection getChildren(EntryDefinition entryDefinition) {
        return getChildren(entryDefinition.getDn());
    }

    public Collection getChildren(String dn) {
        return (Collection)childrenMap.get(dn);
    }

    public void setChildren(EntryDefinition entryDefinition, Collection children) {
        setChildren(entryDefinition.getDn(), children);
    }

    public void setChildren(String dn, Collection children) {
        childrenMap.put(dn, children);
    }

    public Collection removeChildren(EntryDefinition entry) {
        return removeChildren(entry.getDn());
    }

    public Collection removeChildren(String dn) {
        return (Collection)childrenMap.remove(dn);
    }

    public Collection getEffectiveSources(EntryDefinition entry) {
        Collection list = new ArrayList();
        list.addAll(entry.getSources());

        EntryDefinition parent = getParent(entry);
        if (parent != null) list.addAll(getEffectiveSources(parent));

        return list;
    }

    public Source getEffectiveSource(EntryDefinition entryDefinition, String name) {
        Source source = (Source)entryDefinition.getSource(name);
        if (source != null) return source;

        EntryDefinition parent = getParent(entryDefinition);
        if (parent != null) return getEffectiveSource(parent, name);

        return null;
    }

    public Collection getEffectiveRelationships(EntryDefinition entryDefinition) {
        Collection relationships = new ArrayList();
        relationships.addAll(entryDefinition.getRelationships());

        EntryDefinition parent = getParent(entryDefinition);
        if (parent != null) relationships.addAll(getEffectiveRelationships(parent));

        return relationships;
    }

    public void addModuleConfig(ModuleConfig moduleConfig) throws Exception {
        moduleConfigs.put(moduleConfig.getModuleName(), moduleConfig);
    }

    public ModuleConfig getModuleConfig(String name) {
        return (ModuleConfig)moduleConfigs.get(name);
    }

    public Collection getModuleMappings(String name) {
        return (Collection)moduleMappings.get(name);
    }

    public void addModuleMapping(ModuleMapping mapping) throws Exception {
        Collection c = (Collection)moduleMappings.get(mapping.getModuleName());
        if (c == null) {
            c = new ArrayList();
            moduleMappings.put(mapping.getModuleName(), c);
        }
        c.add(mapping);

        String moduleName = mapping.getModuleName();
        if (moduleName == null) throw new Exception("Missing module name");

        ModuleConfig moduleConfig = getModuleConfig(moduleName);
        if (moduleConfig == null) throw new Exception("Undefined module "+moduleName);

        mapping.setModuleConfig(moduleConfig);
    }

	/**
	 * Add connection object to this configuration
	 * 
	 * @param connectionConfig
	 */
	public void addConnectionConfig(ConnectionConfig connectionConfig) throws Exception {
		connectionConfigs.put(connectionConfig.getConnectionName(), connectionConfig);
	}
	
	public ConnectionConfig removeConnectionConfig(String connectionName) {
		return (ConnectionConfig)connectionConfigs.remove(connectionName);
	}

    public ConnectionConfig getConnectionConfig(String name) {
        return (ConnectionConfig)connectionConfigs.get(name);
    }

	/**
	 * @return Returns the root.
	 */
	public Collection getEntryDefinitions() {
		return entryDefinitions.values();
	}

	/**
	 * @param root
	 *            The root to set.
	 */
	public void setEntryDefinitions(Map root) {
		this.entryDefinitions = root;
	}

    public EntryDefinition getEntryDefinition(String dn) {
        if (dn == null) return null;
        return (EntryDefinition)entryDefinitions.get(dn);
    }

    public EntryDefinition findEntryDefinition(String dn) throws Exception {

        EntryDefinition result = null;

        try {
            log.debug("Finding "+dn);

            if (dn == null || "".equals(dn)) {
                return result;
            }
            
            EntryDefinition entryDefinition = (EntryDefinition)this.entryDefinitions.get(dn);
            if (entryDefinition != null) {
                result = entryDefinition;
                return result;
            }

            Row rdn = Entry.getRdn(dn);
            String parentDn = Entry.getParentDn(dn);

            Collection list;

            if (parentDn == null) {
                list = rootEntryDefinitions;

            } else {
                EntryDefinition parentDefinition = findEntryDefinition(parentDn);
                if (parentDefinition == null) {
                    return result;
                }

                list = getChildren(parentDefinition);
                if (list == null) return result;
            }

            for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
                EntryDefinition childDefinition = (EntryDefinition) iterator.next();
                Row childRdn = Entry.getRdn(childDefinition.getRdn());

                if (!rdn.getNames().equals(childRdn.getNames())) continue;

                if (childDefinition.isDynamic()) {
                    result = childDefinition;
                    return result;
                }

                if (!rdn.equals(childRdn)) continue;

                return childDefinition;
            }

            return result;

        } finally {
            //log.debug("result: "+result);
        }
    }

	/**
	 * @return Returns the connections.
	 */
	public Collection getConnectionConfigs() {
		return connectionConfigs.values();
	}
	/**
	 * @param connectionConfigs
	 *            The connections to set.
	 */
	public void setConnectionConfigs(Map connectionConfigs) {
		this.connectionConfigs = connectionConfigs;
	}

	public String toString() {

		String nl = System.getProperty("line.separator");
		StringBuffer sb = new StringBuffer();
		
        sb.append(nl);
        sb.append(nl);

        sb.append("CONNECTIONS:");
        sb.append(nl);
        sb.append(nl);

		for (Iterator i = connectionConfigs.keySet().iterator(); i.hasNext();) {
			String connectionName = (String) i.next();
			ConnectionConfig connection = (ConnectionConfig) connectionConfigs.get(connectionName);
			sb.append(connectionName + " (" + connection.getAdapterName() + ")" + nl);
			sb.append("Parameters:" + nl);
			for (Iterator j = connection.getParameterNames().iterator(); j.hasNext();) {
				String name = (String) j.next();
				String value = connection.getParameter(name);
				sb.append("- " + name + ": " + value + nl);
			}
			sb.append(nl);

            for (Iterator j = connection.getSourceDefinitions().iterator(); j.hasNext();) {
                SourceDefinition sourceConfig = (SourceDefinition)j.next();
                sb.append("Source "+sourceConfig.getName()+nl);
                for (Iterator k = sourceConfig.getFieldDefinitions().iterator(); k.hasNext(); ) {
                    FieldDefinition field = (FieldDefinition)k.next();
                    sb.append("- field: "+field.getName()+" "+(field.isPrimaryKey() ? "(primary key)" : "") + nl);
                }
                for (Iterator k = sourceConfig.getParameterNames().iterator(); k.hasNext(); ) {
                    String name = (String)k.next();
                    sb.append("- "+name+": "+sourceConfig.getParameter(name) + nl);
                }
                sb.append(nl);
            }
        }

		sb.append("ENTRIES:");
        sb.append(nl);
        sb.append(nl);

		for (Iterator i = rootEntryDefinitions.iterator(); i.hasNext();) {
			EntryDefinition entry = (EntryDefinition)i.next();
			sb.append(toString(entry));
		}

        sb.append("MODULES:");
        sb.append(nl);
        sb.append(nl);
        for (Iterator i = moduleConfigs.values().iterator(); i.hasNext(); ) {
            ModuleConfig moduleConfig = (ModuleConfig)i.next();
            sb.append(moduleConfig.getModuleName()+" ("+moduleConfig.getModuleClass() + ")" + nl);
            for (Iterator j = moduleConfig.getParameterNames().iterator(); j.hasNext(); ) {
                String name = (String)j.next();
                sb.append("- "+name+": "+moduleConfig.getParameter(name) + nl);
            }
            sb.append(nl);
        }

        sb.append("MODULE MAPPINGS:");
        sb.append(nl);
        sb.append(nl);
        for (Iterator i = moduleMappings.values().iterator(); i.hasNext(); ) {
            Collection c = (Collection)i.next();
            for (Iterator j = c.iterator(); j.hasNext(); ) {
                ModuleMapping moduleMapping = (ModuleMapping)j.next();
                sb.append(moduleMapping.getModuleName()+" -> "+ moduleMapping.getBaseDn() + nl);
            }
        }

		return sb.toString();
	}

	public String toString(EntryDefinition entry) {
		
		String nl = System.getProperty("line.separator");
		StringBuffer sb = new StringBuffer("dn: " + entry.getDn() + nl);
		Collection oc = entry.getObjectClasses();
		for (Iterator i = oc.iterator(); i.hasNext(); ) {
			String value = (String) i.next();
			sb.append("objectClass: " + value + nl);
		}

		Collection attributes = entry.getAttributeDefinitions();
		for (Iterator i = attributes.iterator(); i.hasNext(); ) {
			AttributeDefinition attribute = (AttributeDefinition) i.next();
			if (attribute.getName().equals("objectClass"))
				continue;

			sb.append(attribute.getName() + ": "
					+ attribute.getExpression() + nl);
		}

		Collection childDefinitions = entry.getChildDefinitions();
		for (Iterator i = childDefinitions.iterator(); i.hasNext();) {
			MappingRule child = (MappingRule) i.next();
			sb.append("=> "+child.getFile() + nl);
		}

        sb.append(nl);

        Collection children = getChildren(entry);
        if (children != null) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                EntryDefinition child = (EntryDefinition) i.next();
                sb.append(toString(child));
            }
        }

		return sb.toString();
	}

	public Collection getModuleMappings() {
		return moduleMappings.values();
	}
	public Collection getRootEntryDefinitions() {
		return rootEntryDefinitions;
	}

    public ModuleConfig removeModuleConfig(String moduleName) {
    	return (ModuleConfig)moduleConfigs.remove(moduleName);
    }
    
    public Collection getModuleConfigs() {
    	return moduleConfigs.values();
    }
    
    public Collection removeModuleMapping(String moduleName) {
    	return (Collection)moduleMappings.remove(moduleName);
    }

    public void removeModuleMapping(ModuleMapping mapping) {
        if (mapping == null) return;
        if (mapping.getModuleName() == null) return;
        
        Collection c = (Collection)moduleMappings.get(mapping.getModuleName());
        if (c != null) c.remove(mapping);
    }

    public void setModuleConfigs(Map moduleConfigs) {
        this.moduleConfigs = moduleConfigs;
    }

    public void setModuleMappings(Map moduleMappings) {
        this.moduleMappings = moduleMappings;
    }

    public void setRootEntryDefinitions(Collection rootEntryDefinitions) {
        this.rootEntryDefinitions = rootEntryDefinitions;
    }

    public Collection getSearchableFields(Source source) {
        ConnectionConfig connectionConfig = getConnectionConfig(source.getConnectionName());
        SourceDefinition sourceDefinition = connectionConfig.getSourceDefinition(source.getSourceName());

        Collection results = new ArrayList();
        for (Iterator i=source.getFields().iterator(); i.hasNext(); ) {
            Field field = (Field)i.next();
            FieldDefinition fieldDefinition = sourceDefinition.getFieldDefinition(field.getName());
            if (!fieldDefinition.isSearchable()) continue;
            results.add(field);
        }

        return results;
    }
}
