package org.safehaus.penrose.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.safehaus.penrose.adapter.AdapterConfig;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;

/**
 * @author Endi Sukma Dewata
 */
public class ConnectionConfigs implements Cloneable {

    public Logger log = LoggerFactory.getLogger(getClass());
    public boolean debug = log.isDebugEnabled();

    private Map<String,AdapterConfig> adapterConfigs = new LinkedHashMap<String,AdapterConfig>();
    private Map<String,ConnectionConfig> connectionConfigs = new LinkedHashMap<String,ConnectionConfig>();

    public void addAdapterConfig(AdapterConfig adapterConfig) {
        String name = adapterConfig.getName();
        if (debug) log.debug("Adding adapter "+name+".");
        adapterConfigs.put(name, adapterConfig);
    }

    public AdapterConfig getAdapterConfig(String name) {
        return adapterConfigs.get(name);
    }

    public Collection<AdapterConfig> getAdapterConfigs() {
        return adapterConfigs.values();
    }

    public void addConnectionConfig(ConnectionConfig connectionConfig) {
        String name = connectionConfig.getName();
        if (debug) log.debug("Adding connection "+name+".");
        connectionConfigs.put(name, connectionConfig);
    }

    public ConnectionConfig getConnectionConfig(String name) {
        return connectionConfigs.get(name);
    }

    public Collection<ConnectionConfig> getConnectionConfigs() {
        return connectionConfigs.values();
    }

    public void modifyConnectionConfig(String name, ConnectionConfig newConnectionConfig) {
        ConnectionConfig connectionConfig = connectionConfigs.get(name);
        connectionConfig.copy(newConnectionConfig);
    }

    public ConnectionConfig removeConnectionConfig(String connectionName) {
        return connectionConfigs.remove(connectionName);
    }

    public void renameConnectionConfig(ConnectionConfig connectionConfig, String newName) {
        if (connectionConfig == null) return;
        if (connectionConfig.getName().equals(newName)) return;

        connectionConfigs.remove(connectionConfig.getName());
        connectionConfigs.put(newName, connectionConfig);
    }

    public Object clone() throws CloneNotSupportedException {
        ConnectionConfigs connections = (ConnectionConfigs)super.clone();

        connections.adapterConfigs = new LinkedHashMap<String,AdapterConfig>();
        for (AdapterConfig adapterConfig : adapterConfigs.values()) {
            connections.adapterConfigs.put(adapterConfig.getName(), (AdapterConfig)adapterConfig.clone());
        }

        connections.connectionConfigs = new LinkedHashMap<String,ConnectionConfig>();
        for (ConnectionConfig connectionConfig : connectionConfigs.values()) {
            connections.connectionConfigs.put(connectionConfig.getName(), (ConnectionConfig)connectionConfig.clone());
        }

        return connections;
    }
}