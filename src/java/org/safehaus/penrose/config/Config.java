/**
 * Copyright (c) 1998-2005, Verge Lab., LLC.
 * All rights reserved.
 */
package org.safehaus.penrose.config;

import java.io.Serializable;
import java.util.*;

import org.apache.log4j.Logger;
import org.safehaus.penrose.module.ModuleMapping;
import org.safehaus.penrose.module.Module;
import org.safehaus.penrose.module.ModuleConfig;
import org.safehaus.penrose.Penrose;
import org.safehaus.penrose.graph.Graph;
import org.safehaus.penrose.mapping.*;


/**
 * @author Endi S. Dewata
 * @author Adison Wongkar 
 */
public class Config implements Serializable {

    public Logger log = Logger.getLogger(Penrose.CONFIG_LOGGER);

    private Map entryDefinitions = new TreeMap();
    private Collection rootEntryDefinitions = new ArrayList();

    private Map graphs = new HashMap();
    private Map primarySources = new HashMap();

    private Map connectionConfigs = new LinkedHashMap();

    private Map moduleConfigs = new LinkedHashMap();
    private Map moduleMappings = new LinkedHashMap();
    private Map modules = new LinkedHashMap();

    public Config() {
    }

	public void addEntryDefinition(EntryDefinition entry) throws Exception {

        String dn = entry.getDn();

        if (entryDefinitions.get(dn) != null) throw new Exception("Entry "+dn+" already exists.");

        //System.out.println("Adding "+dn+".");

        int i = dn.indexOf(",");

        if (i >= 0) { // entry has parent

            String parentDn = dn.substring(i+1);
            EntryDefinition parent = (EntryDefinition)entryDefinitions.get(parentDn);

            if (parent != null) { // parent found
                //System.out.println("Found parent "+parentDn+".");
                parent.addChild(entry);
            }
        }

        entryDefinitions.put(dn, entry);

        if (entry.getParent() == null) {
        	rootEntryDefinitions.add(entry);
        }

        // connecting all references to source and field definitions
        for (Iterator j=entry.getSources().iterator(); j.hasNext(); ) {
            Source source = (Source)j.next();

            String sourceName = source.getSourceName();
            String connectionName = source.getConnectionName();

            ConnectionConfig connection = getConnectionConfig(connectionName);
            if (connection == null) throw new Exception("Connection "+connectionName+" undefined.");

            SourceDefinition sourceDefinition = connection.getSourceDefinition(sourceName);
            if (sourceDefinition == null) throw new Exception("Source "+sourceName+" undefined.");

            source.setConnectionConfig(connection);
            source.setSourceDefinition(sourceDefinition);

            Collection fieldConfigs = sourceDefinition.getFields();

            for (Iterator k=fieldConfigs.iterator(); k.hasNext(); ) {
                FieldDefinition fieldConfig = (FieldDefinition)k.next();
                String fieldName = fieldConfig.getName();

                // define any missing fields
                Field field = (Field)source.getField(fieldName);
                if (field == null) {
                    field = new Field();
                    field.setName(fieldName);
                    source.addField(field);
                }

                field.setFieldDefinition(fieldConfig);
                if (fieldConfig.isPrimaryKey()) source.addPrimaryKeyField(field);
            }
        }
    }

    public void analyze() throws Exception {

        for (Iterator i=rootEntryDefinitions.iterator(); i.hasNext(); ) {
            EntryDefinition entryDefinition = (EntryDefinition)i.next();
            analyze(entryDefinition);
        }
    }

    public void analyze(EntryDefinition entryDefinition) throws Exception {

        log.debug("Entry "+entryDefinition.getDn()+":");

        Source source = computePrimarySource(entryDefinition);
        primarySources.put(entryDefinition, source);
        log.debug(" - primary source: "+source);

        Graph graph = computeGraph(entryDefinition);
        graphs.put(entryDefinition, graph);
        log.debug(" - graph: "+graph);

        for (Iterator i=entryDefinition.getChildren().iterator(); i.hasNext(); ) {
            EntryDefinition childDefinition = (EntryDefinition)i.next();
            analyze(childDefinition);
        }
	}

    public Source getPrimarySource(EntryDefinition entryDefinition) {
        return (Source)primarySources.get(entryDefinition);
    }

    Source computePrimarySource(EntryDefinition entryDefinition) {

        Collection rdnAttributes = entryDefinition.getRdnAttributes();

        // TODO need to handle multiple rdn attributes
        AttributeDefinition rdnAttribute = (AttributeDefinition)rdnAttributes.iterator().next();
        String exp = rdnAttribute.getExpression();

        // TODO need to handle complex expression
        int index = exp.indexOf(".");
        if (index < 0) return null;

        String primarySourceName = exp.substring(0, index);

        for (Iterator i = entryDefinition.getSources().iterator(); i.hasNext();) {
            Source source = (Source) i.next();
            if (source.getName().equals(primarySourceName)) return source;
        }

        return null;
    }

    public Graph getGraph(EntryDefinition entryDefinition) throws Exception {

        return (Graph)graphs.get(entryDefinition);
    }

    Graph computeGraph(EntryDefinition entryDefinition) throws Exception {

        Graph graph = new Graph();

        Collection sources = entryDefinition.getEffectiveSources();
        if (sources.size() == 0) return null;

        for (Iterator i=sources.iterator(); i.hasNext(); ) {
            Source source = (Source)i.next();
            graph.addNode(source);
        }

        Collection relationships = entryDefinition.getRelationships();
        for (Iterator i=relationships.iterator(); i.hasNext(); ) {
            Relationship relationship = (Relationship)i.next();
            // System.out.println("Checking ["+relationship.getExpression()+"]");

            String lhs = relationship.getLhs();
            int li = lhs.indexOf(".");
            String lsourceName = lhs.substring(0, li);

            String rhs = relationship.getRhs();
            int ri = rhs.indexOf(".");
            String rsourceName = rhs.substring(0, ri);

            Source lsource = entryDefinition.getEffectiveSource(lsourceName);
            Source rsource = entryDefinition.getEffectiveSource(rsourceName);
            graph.addEdge(lsource, rsource, relationship);
        }

        // System.out.println("Graph: "+graph);

        return graph;
    }

    public EntryDefinition removeEntryDefinition(EntryDefinition entry) {
        if (entry.getParent() == null) {
            rootEntryDefinitions.remove(entry);

        } else {
            Collection siblings = entry.getParent().getChildren();
            siblings.remove(entry);
        }

        return (EntryDefinition)entryDefinitions.remove(entry.getDn());
    }
    
    public void renameEntryDefinition(EntryDefinition entry, String newDn) {
    	if (entry == null) return;

    	if (newDn.equals(entry.getDn())) return;
    	//System.out.println("Renaming "+entry.getDn()+" to "+newDn);

    	String oldDn = entry.getDn();
    	entry.setDn(newDn);

        entryDefinitions.put(newDn, entry);

        Collection children = entry.getChildren();
        for (Iterator i=children.iterator(); i.hasNext(); ) {
            EntryDefinition child = (EntryDefinition)i.next();
            String childNewDn = child.getRdn()+","+newDn;
            //System.out.println(" - renaming child "+child.getDn()+" to "+childNewDn);

            renameEntryDefinition(child, childNewDn);
        }

        entryDefinitions.remove(oldDn);
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

            int i = dn.indexOf(",");
            String rdn;
            String parentDn;

            if (i < 0) {
                rdn = dn;
                parentDn = null;

            } else {
                rdn = dn.substring(0, i);
                parentDn = dn.substring(i + 1);
            }

            Collection list;

            if (parentDn == null) {
                list = rootEntryDefinitions;

            } else {
                EntryDefinition parentDefinition = findEntryDefinition(parentDn);
                if (parentDefinition == null) {
                    return result;
                }

                list = parentDefinition.getChildren();
            }

            int j = rdn.indexOf("=");
            String rdnAttribute = rdn.substring(0, j);
            String rdnValue = rdn.substring(j + 1);

            for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
                EntryDefinition childDefinition = (EntryDefinition) iterator.next();
                String childRdn = childDefinition.getRdn();

                int k = childRdn.indexOf("=");
                String childRdnAttribute = childRdn.substring(0, k);
                String childRdnValue = childRdn.substring(k+1);

                if (!rdnAttribute.equals(childRdnAttribute)) continue;

                if (childDefinition.isDynamic()) {
                    result = childDefinition;
                    return result;
                }

                if (!rdnValue.toLowerCase().equals(childRdnValue.toLowerCase())) continue;

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
                for (Iterator k = sourceConfig.getFields().iterator(); k.hasNext(); ) {
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

		Map attributes = entry.getAttributes();
		for (Iterator i = attributes.values().iterator(); i.hasNext(); ) {
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

        Collection children = entry.getChildren();
        for (Iterator i = children.iterator(); i.hasNext();) {
            EntryDefinition child = (EntryDefinition) i.next();
            sb.append(toString(child));
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

    public Map getGraphs() {
        return graphs;
    }

    public void setGraphs(Map graphs) {
        this.graphs = graphs;
    }

    public Map getPrimarySources() {
        return primarySources;
    }

    public void setPrimarySources(Map primarySources) {
        this.primarySources = primarySources;
    }

    public void init() throws Exception {
        initModules();
        analyze();
    }

    public void initModules() throws Exception {

        for (Iterator i=getModuleConfigs().iterator(); i.hasNext(); ) {
            ModuleConfig moduleConfig = (ModuleConfig)i.next();

            Class clazz = Class.forName(moduleConfig.getModuleClass());
            Module module = (Module)clazz.newInstance();
            module.init(moduleConfig);

            modules.put(moduleConfig.getModuleName(), module);
        }

    }

    public Collection getModules(String dn) throws Exception {
        log.debug("Find matching module mapping for "+dn);

        Collection list = new ArrayList();

        for (Iterator i = getModuleMappings().iterator(); i.hasNext(); ) {
            Collection c = (Collection)i.next();

            for (Iterator j=c.iterator(); j.hasNext(); ) {
                ModuleMapping moduleMapping = (ModuleMapping)j.next();

                String moduleName = moduleMapping.getModuleName();
                Module module = (Module)modules.get(moduleName);

                if (moduleMapping.match(dn)) {
                    log.debug(" - "+moduleName);
                    list.add(module);
                }
            }
        }

        return list;
    }
}
