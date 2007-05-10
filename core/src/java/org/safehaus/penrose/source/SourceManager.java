package org.safehaus.penrose.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.safehaus.penrose.config.PenroseConfig;
import org.safehaus.penrose.naming.PenroseContext;
import org.safehaus.penrose.partition.Partition;
import org.safehaus.penrose.partition.SourceConfig;
import org.safehaus.penrose.connection.ConnectionManager;
import org.safehaus.penrose.connection.Connection;
import org.safehaus.penrose.mapping.EntryMapping;
import org.safehaus.penrose.mapping.SourceMapping;

import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class SourceManager {

    public Logger log = LoggerFactory.getLogger(getClass());

    public final static Collection<String> EMPTY_STRINGS = new ArrayList<String>();
    public final static Collection<Source> EMPTY_SOURCES = new ArrayList<Source>();
    public final static Collection<SourceRef> EMPTY_SOURCEREFS = new ArrayList<SourceRef>();

    private PenroseConfig penroseConfig;
    private PenroseContext penroseContext;

    public Map<String,Map<String,Source>> sources = new LinkedHashMap<String,Map<String,Source>>();
    public Map<String,Map<String,Map<String,SourceRef>>> sourceRefs = new LinkedHashMap<String,Map<String,Map<String,SourceRef>>>();

    public void init(Partition partition, SourceConfig sourceConfig) throws Exception {

        Source source = getSource(partition, sourceConfig.getName());
        if (source != null) return;

        log.debug("Initializing source "+sourceConfig.getName()+".");

        source = new Source(partition, sourceConfig);

        ConnectionManager connectionManager = penroseContext.getConnectionManager();
        Connection connection = connectionManager.getConnection(partition, sourceConfig.getConnectionName());
        source.setConnection(connection);

        addSource(partition, source);
    }

    public void init(Partition partition, EntryMapping entryMapping, SourceMapping sourceMapping) throws Exception {

        SourceRef sourceRef = getSourceRef(partition.getName(), entryMapping, sourceMapping.getName());
        if (sourceRef != null) return;

        log.debug("Initializing source mapping "+sourceMapping.getName()+".");

        Source source = getSource(partition, sourceMapping.getSourceName());
        sourceRef = new SourceRef(source, sourceMapping);

        addSourceRef(partition.getName(), entryMapping, sourceRef);
    }

    public void addSourceRef(String partitionName, EntryMapping entryMapping, SourceRef sourceRef) {
        Map<String,Map<String,SourceRef>> entryMappings = sourceRefs.get(partitionName);
        if (entryMappings == null) {
            entryMappings = new LinkedHashMap<String,Map<String,SourceRef>>();
            sourceRefs.put(partitionName, entryMappings);
        }

        Map<String,SourceRef> map = entryMappings.get(entryMapping.getId());
        if (map == null) {
            map = new LinkedHashMap<String,SourceRef>();
            entryMappings.put(entryMapping.getId(), map);
        }

        map.put(sourceRef.getAlias(), sourceRef);
    }

    public Collection<String> getSourceRefNames(Partition partition, EntryMapping entryMapping) {
        Map<String,Map<String,SourceRef>> entryMappings = sourceRefs.get(partition.getName());
        if (entryMappings == null) return EMPTY_STRINGS;

        Map<String,SourceRef> map = entryMappings.get(entryMapping.getId());
        if (map == null) return EMPTY_STRINGS;

        return new ArrayList<String>(map.keySet()); // return Serializable list
    }

    public Collection<SourceRef> getSourceRefs(Partition partition, EntryMapping entryMapping) {
        Map<String,Map<String,SourceRef>> entryMappings = sourceRefs.get(partition.getName());
        if (entryMappings == null) return EMPTY_SOURCEREFS;

        Map<String,SourceRef> map = entryMappings.get(entryMapping.getId());
        if (map == null) return EMPTY_SOURCEREFS;

        return map.values();
    }

    public SourceRef getSourceRef(String partitionName, EntryMapping entryMapping, String sourceName) {
        Map<String,Map<String,SourceRef>> entryMappings = sourceRefs.get(partitionName);
        if (entryMappings == null) return null;

        Map<String,SourceRef> map = entryMappings.get(entryMapping.getId());
        if (map == null) return null;

        return map.get(sourceName);
    }

    public void addSource(Partition partition, Source source) {
        Map<String,Source> map = sources.get(partition.getName());
        if (map == null) {
            map = new LinkedHashMap<String,Source>();
            sources.put(partition.getName(), map);
        }
        map.put(source.getName(), source);
    }

    public Collection<String> getSourceNames(String partitionName) {
        Map<String,Source> map = sources.get(partitionName);
        if (map == null) return EMPTY_STRINGS;
        return new ArrayList<String>(map.keySet()); // return Serializable list
    }

    public Collection<Source> getSources(Partition partition) {
        Map<String,Source> map = sources.get(partition.getName());
        if (map == null) return EMPTY_SOURCES;
        return map.values();
    }

    public Source getSource(Partition partition, String sourceName) {
        return getSource(partition.getName(), sourceName);
    }

    public Source getSource(String partitionName, String sourceName) {
        Map<String,Source> map = sources.get(partitionName);
        if (map == null) return null;
        return map.get(sourceName);
    }

    public PenroseConfig getPenroseConfig() {
        return penroseConfig;
    }

    public void setPenroseConfig(PenroseConfig penroseConfig) {
        this.penroseConfig = penroseConfig;
    }

    public PenroseContext getPenroseContext() {
        return penroseContext;
    }

    public void setPenroseContext(PenroseContext penroseContext) {
        this.penroseContext = penroseContext;
    }
}
