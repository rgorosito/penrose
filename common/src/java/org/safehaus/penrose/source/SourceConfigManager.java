package org.safehaus.penrose.source;

import org.safehaus.penrose.directory.EntrySourceConfig;
import org.safehaus.penrose.directory.EntryFieldConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.io.Serializable;

/**
 * @author Endi S. Dewata
 */
public class SourceConfigManager implements Serializable, Cloneable {

    static {
        log = LoggerFactory.getLogger(SourceConfigManager.class);
    }

    public static transient Logger log;
    public static boolean debug = log.isDebugEnabled();

    protected Map<String,SourceConfig> sourceConfigs                             = new LinkedHashMap<String,SourceConfig>();
    protected Map<String,Collection<SourceConfig>> sourceConfigsByConnectionName = new LinkedHashMap<String,Collection<SourceConfig>>();

    public void addSourceConfig(SourceConfig sourceConfig) {

        String sourceName = sourceConfig.getName();

        if (debug) {
            log.debug("Adding source "+sourceName+":");
            for (FieldConfig fieldConfig : sourceConfig.getFieldConfigs()) {
                log.debug(" - "+fieldConfig.getName()+": "+fieldConfig.getType()+(fieldConfig.isPrimaryKey() ? " (pk)" : ""));
            }
        }

        sourceConfigs.put(sourceName, sourceConfig);

        String connectionName = sourceConfig.getConnectionName();
        Collection<SourceConfig> list = sourceConfigsByConnectionName.get(connectionName);
        if (list == null) {
            list = new ArrayList<SourceConfig>();
            sourceConfigsByConnectionName.put(connectionName, list);
        }
        list.add(sourceConfig);
    }

    public Collection<String> getSourceNames() {
        return sourceConfigs.keySet();
    }

    public void renameSourceConfig(String name, String newName) throws Exception {
        SourceConfig sourceConfig = sourceConfigs.remove(name);
        sourceConfig.setName(newName);
        sourceConfigs.put(newName, sourceConfig);
    }

    public void updateSourceConfig(SourceConfig sourceConfig) throws Exception {

        String sourceName = sourceConfig.getName();
        String connectionName = sourceConfig.getConnectionName();

        SourceConfig oldSourceConfig = sourceConfigs.get(sourceName);
        if (oldSourceConfig == null) throw new Exception("Source "+sourceName+" not found.");

        String oldConnectionName = oldSourceConfig.getConnectionName();

        oldSourceConfig.copy(sourceConfig);

        if (!oldConnectionName.equals(connectionName)) {
            Collection<SourceConfig> list = sourceConfigsByConnectionName.get(oldConnectionName);
            if (list != null) {
                list.remove(oldSourceConfig);
                if (list.isEmpty()) sourceConfigsByConnectionName.remove(oldConnectionName);
            }

            list = sourceConfigsByConnectionName.get(connectionName);
            if (list == null) {
                list = new ArrayList<SourceConfig>();
                sourceConfigsByConnectionName.put(connectionName, list);
            }
            list.add(sourceConfig);
        }
    }

    public SourceConfig removeSourceConfig(String sourceName) {
        SourceConfig sourceConfig = sourceConfigs.remove(sourceName);

        String connectionName = sourceConfig.getConnectionName();
        Collection<SourceConfig> list = sourceConfigsByConnectionName.get(connectionName);
        if (list != null) {
            list.remove(sourceConfig);
            if (list.isEmpty()) sourceConfigsByConnectionName.remove(connectionName);
        }

        return sourceConfig;
    }

    public SourceConfig getSourceConfig(String name) {
        return sourceConfigs.get(name);
    }

    public Collection<SourceConfig> getSourceConfigsByConnectionName(String connectionName) {
        return sourceConfigsByConnectionName.get(connectionName);
    }

    public SourceConfig getSourceConfig(EntrySourceConfig entrySourceConfig) {
        return getSourceConfig(entrySourceConfig.getSourceName());
    }

    public Collection<SourceConfig> getSourceConfigs() {
        return sourceConfigs.values();
    }

    public Collection<EntryFieldConfig> getSearchableFields(EntrySourceConfig entrySourceConfig) {
        SourceConfig sourceConfig = getSourceConfig(entrySourceConfig.getSourceName());

        Collection<EntryFieldConfig> results = new ArrayList<EntryFieldConfig>();
        for (EntryFieldConfig entryFieldConfig : entrySourceConfig.getFieldConfigs()) {
            FieldConfig fieldConfig = sourceConfig.getFieldConfig(entryFieldConfig.getName());
            if (fieldConfig == null) continue;
            if (!fieldConfig.isSearchable()) continue;
            results.add(entryFieldConfig);
        }

        return results;
    }

    public Object clone() throws CloneNotSupportedException {
        SourceConfigManager sources = (SourceConfigManager)super.clone();

        sources.sourceConfigs = new LinkedHashMap<String,SourceConfig>();
        for (SourceConfig sourceConfig : sourceConfigs.values()) {
            sources.sourceConfigs.put(sourceConfig.getName(), (SourceConfig)sourceConfig.clone());
        }

        return sources;
    }
}
