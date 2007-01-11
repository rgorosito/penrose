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
package org.safehaus.penrose.partition;

import org.safehaus.penrose.mapping.EntryMapping;
import org.safehaus.penrose.mapping.SourceMapping;
import org.safehaus.penrose.schema.SchemaManager;
import org.safehaus.penrose.cache.LRUCache;
import org.safehaus.penrose.cache.EntryCacheManager;
import org.safehaus.penrose.cache.SourceCache;
import org.safehaus.penrose.cache.SourceCacheManager;
import org.safehaus.penrose.graph.Graph;
import org.safehaus.penrose.interpreter.InterpreterManager;
import org.safehaus.penrose.connection.ConnectionManager;
import org.safehaus.penrose.connection.ConnectionConfig;
import org.safehaus.penrose.adapter.AdapterConfig;
import org.safehaus.penrose.source.SourceManager;
import org.safehaus.penrose.source.SourceConfig;
import org.safehaus.penrose.source.Source;
import org.safehaus.penrose.config.PenroseConfig;
import org.safehaus.penrose.util.Formatter;
import org.safehaus.penrose.util.EntryUtil;
import org.safehaus.penrose.module.ModuleConfig;
import org.safehaus.penrose.module.ModuleManager;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.*;
import java.io.File;

/**
 * @author Endi S. Dewata
 */
public class PartitionManager implements PartitionManagerMBean {

    Logger log = LoggerFactory.getLogger(getClass());

    private PenroseConfig penroseConfig;

    private InterpreterManager interpreterManager;
    private SchemaManager schemaManager;

    private ConnectionManager connectionManager;
    private SourceCacheManager sourceCacheManager;
    private SourceManager sourceManager;
    private EntryCacheManager entryCacheManager;
    private ModuleManager moduleManager;

    private Map partitions = new TreeMap();

    public LRUCache cache = new LRUCache(20);
    public PartitionAnalyzer analyzer;

    public PartitionManager() {
    }

    public void init() throws Exception {
        analyzer = new PartitionAnalyzer();
        analyzer.setPartitionManager(this);
        analyzer.setInterpreterManager(interpreterManager);
    }

    public Graph getGraph(Partition partition, EntryMapping entryMapping) throws Exception {
        return analyzer.getGraph(entryMapping);
    }

    public SourceMapping getPrimarySource(Partition partition, EntryMapping entryMapping) throws Exception {
        return analyzer.getPrimarySource(entryMapping);
    }

    public boolean isUnique(Partition partition, EntryMapping entryMapping) throws Exception {
        return analyzer.isUnique(partition, entryMapping);
    }

    public void loadPartitions(String partitionsDir) throws Exception {

        PartitionConfigReader partitionConfigReader = new PartitionConfigReader();

        File dir = new File(partitionsDir);
        File files[] = dir.listFiles();
        if (files == null) return;
        
        for (int i=0; i<files.length; i++) {
            File file = files[i];
            if (!file.isDirectory()) continue;

            String path = partitionsDir+File.separator+file.getName();
            PartitionConfig partitionConfig = partitionConfigReader.read(path);
            if (partitionConfig == null) continue;

            load(path, partitionConfig);
        }
    }

    public Partition load(String partitionDir, PartitionConfig partitionConfig) throws Exception {

        log.debug(Formatter.displaySeparator(80));
        log.debug(Formatter.displayLine("Loading partition "+partitionConfig.getName(), 80));
        log.debug(Formatter.displaySeparator(80));

        PartitionReader partitionReader = new PartitionReader();
        Partition partition = partitionReader.read(partitionDir, partitionConfig);

        addPartition(partition);

        log.debug(Formatter.displaySeparator(80));
        log.debug(Formatter.displayLine("Partition "+partitionConfig.getName()+" loaded", 80));
        log.debug(Formatter.displaySeparator(80));
        
        return partition;
    }

    public void storePartitions(String dir) throws Exception {
        for (Iterator i=partitions.keySet().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            if ("DEFAULT".equals(name)) continue;

            Partition partition = (Partition)partitions.get(name);
            PartitionConfig partitionConfig = partition.getPartitionConfig();
            
            String path = (dir == null ? "" : dir +File.separator)+partitionConfig.getName();
            store(path, partitionConfig);
        }
    }

    public void store(String path, PartitionConfig partitionConfig) throws Exception {

        log.debug("Storing "+partitionConfig.getName()+" partition into "+path+".");

        Partition partition = getPartition(partitionConfig.getName());

        PartitionConfigWriter partitionConfigWriter = new PartitionConfigWriter();
        partitionConfigWriter.write(path, partitionConfig);

        PartitionWriter partitionWriter = new PartitionWriter();
        partitionWriter.write(path, partition);
    }

    public Partition getPartition(String name) throws Exception {
        return (Partition)partitions.get(name);
    }

    public PartitionConfig getPartitionConfig(String name) throws Exception {
        Partition partition = getPartition(name);
        if (partition == null) return null;
        return partition.getPartitionConfig();
    }
    
    public void addPartition(Partition partition) throws Exception {
        partitions.put(partition.getName(), partition);
    }

    public Partition removePartition(String name) throws Exception {
        return (Partition)partitions.remove(name);
    }

    public void clear() throws Exception {
        partitions.clear();
    }

    public String getStatus(String name) throws Exception {
        Partition partition = getPartition(name);
        if (partition == null) return null;
        return partition.getStatus();
    }

    public void start() throws Exception {
        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();
            if (!partition.isEnabled()) continue;
            start(partition);
        }
    }

    public void start(String name) throws Exception {
        Partition partition = getPartition(name);
        if (partition == null) {
            log.debug("Partition "+name+" not found");
            return;
        }

        start(partition);
    }

    public void start(Partition partition) throws Exception {

        log.debug(Formatter.displaySeparator(80));
        log.debug(Formatter.displayLine("Starting partition "+partition.getName(), 80));
        log.debug(Formatter.displaySeparator(80));

        partition.setStatus(Partition.STARTING);

        Collection rootEntryMappings = partition.getRootEntryMappings();
        for (Iterator i=rootEntryMappings.iterator(); i.hasNext(); ) {
            EntryMapping entryMapping = (EntryMapping)i.next();
            analyzer.analyze(partition, entryMapping);
        }

        Collection connectionConfigs = partition.getConnectionConfigs();
        for (Iterator j=connectionConfigs.iterator(); j.hasNext(); ) {
            ConnectionConfig connectionConfig = (ConnectionConfig)j.next();

            String adapterName = connectionConfig.getAdapterName();
            if (adapterName == null) throw new Exception("Missing adapter name");

            AdapterConfig adapterConfig = penroseConfig.getAdapterConfig(adapterName);
            if (adapterConfig == null) throw new Exception("Undefined adapter "+adapterName);

            connectionManager.addConnection(partition, connectionConfig, adapterConfig);
            connectionManager.start(partition.getName(), connectionConfig.getName());
        }

        Collection sourceConfigs = partition.getSourceConfigs();
        for (Iterator i=sourceConfigs.iterator(); i.hasNext(); ) {
            SourceConfig sourceConfig = (SourceConfig)i.next();
            Source source = sourceManager.create(partition, sourceConfig);

            if (sourceCacheManager != null) {
                SourceCache sourceCache = sourceCacheManager.create(partition, sourceConfig);
                source.setSourceCache(sourceCache);
            }

            sourceManager.start(partition.getName(), sourceConfig.getName());
        }

        if (entryCacheManager != null) {
            Collection entryMappings = partition.getEntryMappings();
            for (Iterator i=entryMappings.iterator(); i.hasNext(); ) {
                EntryMapping entryMapping = (EntryMapping)i.next();
                entryCacheManager.create(partition, entryMapping);
            }
        }

        Collection moduleConfigs = partition.getModuleConfigs();
        for (Iterator i=moduleConfigs.iterator(); i.hasNext(); ) {
            ModuleConfig moduleConfig = (ModuleConfig)i.next();
            moduleManager.create(partition, moduleConfig);
            moduleManager.start(partition.getName(), moduleConfig.getName());
        }

        partition.setStatus(Partition.STARTED);

        log.debug(Formatter.displaySeparator(80));
        log.debug(Formatter.displayLine("Partition "+partition.getName()+" started", 80));
        log.debug(Formatter.displaySeparator(80));
    }

    public void stop() throws Exception {
        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();
            if (!partition.isEnabled()) continue;
            stop(partition);
        }
    }

    public void stop(String name) throws Exception {
        Partition partition = getPartition(name);
        if (partition == null) {
            log.debug("Partition "+name+" not found");
            return;
        }

        stop(partition);
    }

    public void stop(Partition partition) throws Exception {
        log.info("Stopping partition "+partition.getName()+".");

        Collection connectionConfigs = partition.getConnectionConfigs();
        for (Iterator j=connectionConfigs.iterator(); j.hasNext(); ) {
            ConnectionConfig connectionConfig = (ConnectionConfig)j.next();
            connectionManager.stop(partition.getName(), connectionConfig.getName());
        }

        partition.setStatus(Partition.STOPPED);
        log.info("Partition "+partition.getName()+" stopped.");
    }

    public void restart(String name) throws Exception {
        stop(name);
        start(name);
    }

    public Partition getPartition(SourceMapping sourceMapping) throws Exception {
        String sourceName = sourceMapping.getSourceName();
        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();
            if (partition.getSourceConfig(sourceName) != null) return partition;
        }
        return null;
    }

    public Partition getPartition(SourceConfig sourceConfig) throws Exception {
        String connectionName = sourceConfig.getConnectionName();
        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();
            if (partition.getConnectionConfig(connectionName) != null) return partition;
        }
        return null;
    }

    public Partition getPartition(ConnectionConfig connectionConfig) throws Exception {
        String connectionName = connectionConfig.getName();
        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();
            if (partition.getConnectionConfig(connectionName) != null) return partition;
        }
        return null;
    }

    public Partition getPartition(EntryMapping entryMapping) throws Exception {
        return getPartitionByDn(entryMapping.getDn());
    }

    /**
     * Find the closest partition matching the DN
     * @param dn
     * @return partition
     * @throws Exception
     */
    public Partition getPartitionByDn(String dn) throws Exception {
        Partition partition = (Partition)cache.get(dn);
        if (partition != null) return partition;

        String ndn = schemaManager.normalize(dn);
        ndn = ndn == null ? "" : ndn;

        String suffix = null;

        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition p = (Partition)i.next();
            if (!Partition.STARTED.equals(p.getStatus())) continue;

            for (Iterator j=p.getRootEntryMappings().iterator(); j.hasNext(); ) {
                EntryMapping entryMapping = (EntryMapping)j.next();

                String s = schemaManager.normalize(entryMapping.getDn());

                //log.debug("Checking "+ndn+" with "+suffix);
                if (ndn.equals(s)) {
                    partition = p;
                    suffix = s;
                    continue;
                }

                if ("".equals(s)) continue;

                if (ndn.endsWith(s) && (suffix == null || s.length() > suffix.length())) {
                    partition = p;
                    suffix = s;
                }
            }
        }

        cache.put(dn, partition);
        return partition;
    }

    /**
     * Find a partition exactly matching the DN.
     * @param dn
     * @return partition
     * @throws Exception
     */
    public Partition findPartition(String dn) throws Exception {
        Partition partition = (Partition)cache.get(dn);
        if (partition != null) return partition;

        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition p = (Partition)i.next();
            if (!Partition.STARTED.equals(p.getStatus())) continue;

            Collection list = findEntryMappings(p, dn);
            if (list == null || list.isEmpty()) continue;

            partition = p;
            cache.put(dn, partition);
            break;
        }

        return partition;
    }

    public Collection getPartitions() {
        Collection list = new ArrayList();
        for (Iterator i=partitions.values().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();
            if (!Partition.STARTED.equals(partition.getStatus())) continue;

            list.add(partition);
        }
        return list;
    }

    public Collection getAllPartitions() {
        return new ArrayList(partitions.values()); // return Serializable list
    }

    public Collection getPartitionNames() {
        return new ArrayList(partitions.keySet()); // return Serializable list
    }

    public SchemaManager getSchemaManager() {
        return schemaManager;
    }

    public void setSchemaManager(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    public InterpreterManager getInterpreterManager() {
        return interpreterManager;
    }

    public void setInterpreterManager(InterpreterManager interpreterManager) {
        this.interpreterManager = interpreterManager;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public PenroseConfig getPenroseConfig() {
        return penroseConfig;
    }

    public void setPenroseConfig(PenroseConfig penroseConfig) {
        this.penroseConfig = penroseConfig;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public void setModuleManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public SourceManager getSourceManager() {
        return sourceManager;
    }

    public void setSourceManager(SourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    public EntryCacheManager getEntryCacheManager() {
        return entryCacheManager;
    }

    public void setEntryCacheManager(EntryCacheManager entryCacheManager) {
        this.entryCacheManager = entryCacheManager;
    }

    public SourceCacheManager getSourceCacheManager() {
        return sourceCacheManager;
    }

    public void setSourceCacheManager(SourceCacheManager sourceCacheManager) {
        this.sourceCacheManager = sourceCacheManager;
    }

    public Collection findEntryMappings(String dn) throws Exception {
        Partition partition = findPartition(dn);
        if (partition == null) return null;

        return findEntryMappings(partition, dn);
    }

    public Collection findEntryMappings(Partition partition, String dn) throws Exception {

        log.debug("Finding entry mappings \""+dn+"\" in partition "+partition.getName());

        if (dn == null) return null;

        dn = dn.toLowerCase();

        // search for static mappings
        Collection c = partition.getEntryMappings(dn);
        if (c != null) {
            //log.debug("Found "+c.size()+" mapping(s).");
            return c;
        }

        // can't find exact match -> search for parent mappings

        String parentDn = EntryUtil.getParentDn(dn);

        Collection results = new ArrayList();
        Collection list;

        // if dn has no parent, check against root entries
        if (parentDn == null) {
            //log.debug("Check root mappings");
            list = partition.getRootEntryMappings();

        } else {
            log.debug("Search parent mappings for \""+parentDn+"\"");
            Collection parentMappings = findEntryMappings(partition, parentDn);

            // if no parent mappings found, the entry doesn't exist in this partition
            if (parentMappings == null || parentMappings.isEmpty()) {
                log.debug("Entry mapping \""+parentDn+"\" not found");
                return null;
            }

            list = new ArrayList();

            // for each parent mapping found
            for (Iterator i=parentMappings.iterator(); i.hasNext(); ) {
                EntryMapping parentMapping = (EntryMapping)i.next();
                log.debug("Found parent "+parentMapping.getDn());

                if (partition.isProxy(parentMapping)) { // if parent is proxy, include it in results
                    results.add(parentMapping);

                } else { // otherwise check for matching siblings
                    Collection children = partition.getChildren(parentMapping);
                    list.addAll(children);
                }
            }
        }

        // check against each mapping in the list
        for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
            EntryMapping entryMapping = (EntryMapping) iterator.next();

            log.debug("Checking DN pattern:");
            log.debug(" - "+dn);
            log.debug(" - "+entryMapping.getDn());
            if (!EntryUtil.match(dn, entryMapping.getDn())) continue;

            log.debug("Found "+entryMapping.getDn());
            results.add(entryMapping);
        }

        return results;
    }
}
