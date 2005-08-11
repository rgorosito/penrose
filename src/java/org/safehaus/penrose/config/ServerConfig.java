/**
 * Copyright (c) 1998-2005, Verge Lab., LLC.
 * All rights reserved.
 */
package org.safehaus.penrose.config;

import java.io.Serializable;
import java.util.*;

import org.apache.log4j.Logger;
import org.safehaus.penrose.module.ModuleConfig;
import org.safehaus.penrose.module.ModuleMapping;
import org.safehaus.penrose.module.GenericModuleMapping;
import org.safehaus.penrose.Penrose;
import org.safehaus.penrose.graph.Graph;
import org.safehaus.penrose.cache.CacheConfig;
import org.safehaus.penrose.engine.EngineConfig;
import org.safehaus.penrose.interpreter.InterpreterConfig;
import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.connection.*;


/**
 * @author Endi S. Dewata
 */
public class ServerConfig implements Serializable {

    public Logger log = Logger.getLogger(Penrose.CONFIG_LOGGER);

    private Collection schemaFiles = new ArrayList();

    private String rootDn;
    private String rootPassword;
	
    private Map interpreterConfigs = new LinkedHashMap();
    private Map cacheConfigs = new LinkedHashMap();
    private Map engineConfigs = new LinkedHashMap();
    private Map adapterConfigs = new LinkedHashMap();

    public ServerConfig() {
    }

    public void addAdapterConfig(AdapterConfig adapter) {
        adapterConfigs.put(adapter.getAdapterName(), adapter);
    }

	/**
	 * @return Returns the rootDn.
	 */
	public String getRootDn() {
		return rootDn;
	}
	/**
	 * @param rootDn
	 *            The rootDn to set.
	 */
	public void setRootDn(String rootDn) {
		this.rootDn = rootDn;
	}
	/**
	 * @return Returns the rootPassword.
	 */
	public String getRootPassword() {
		return rootPassword;
	}
	/**
	 * @param rootPassword
	 *            The rootPassword to set.
	 */
	public void setRootPassword(String rootPassword) {
		this.rootPassword = rootPassword;
	}

	public String toString() {

		String nl = System.getProperty("line.separator");
		StringBuffer sb = new StringBuffer();
		
        sb.append(nl);
        sb.append(nl);

        sb.append("CACHE:");
        sb.append(nl);
        sb.append(nl);

        for (Iterator i = cacheConfigs.keySet().iterator(); i.hasNext();) {
            String cacheName = (String) i.next();
            CacheConfig sourceCache = (CacheConfig) cacheConfigs.get(cacheName);
            sb.append(cacheName + " (" + sourceCache.getCacheClass() + ")" + nl);
            sb.append("Parameters:" + nl);
            for (Iterator j = sourceCache.getParameterNames().iterator(); j.hasNext();) {
                String name = (String) j.next();
                String value = sourceCache.getParameter(name);
                sb.append("- " + name + ": " + value + nl);
            }
            sb.append(nl);
        }

		return sb.toString();
	}

    public Collection getAdapterConfigs() {
        return adapterConfigs.values();
    }

    public AdapterConfig getAdapterConfig(String name) {
        return (AdapterConfig)adapterConfigs.get(name);
    }

    public Collection getSchemaFiles() {
        return schemaFiles;
    }

    public void setSchemaFiles(List schemaFiles) {
        this.schemaFiles = schemaFiles;
    }
    
    public void addCacheConfig(CacheConfig cacheConfig) {
    	cacheConfigs.put(cacheConfig.getCacheName(), cacheConfig);
    }

    public CacheConfig getCacheConfig() {
        return (CacheConfig)cacheConfigs.get("DEFAULT");
    }

    public CacheConfig getCacheConfig(String name) {
        return (CacheConfig)cacheConfigs.get(name);
    }

    public Collection getCacheConfigs() {
    	return cacheConfigs.values();
    }

    public Collection getEngineConfigs() {
        return engineConfigs.values();
    }

    public void addEngineConfig(EngineConfig engineConfig) {
        engineConfigs.put(engineConfig.getEngineName(), engineConfig);
    }

    public void addInterpreterConfig(InterpreterConfig interpreterConfig) {
        interpreterConfigs.put(interpreterConfig.getInterpreterName(), interpreterConfig);
    }

    public Collection getInterpreterConfigs() {
        return interpreterConfigs.values();
    }

    public InterpreterConfig getInterpreterConfig(String name) {
        return (InterpreterConfig)interpreterConfigs.get(name);
    }

    public void setAdapterConfigs(Map adapterConfigs) {
        this.adapterConfigs = adapterConfigs;
    }

    public void setCacheConfigs(Map cacheConfigs) {
        this.cacheConfigs = cacheConfigs;
    }

    public void setInterpreterConfigs(Map interpreterConfigs) {
        this.interpreterConfigs = interpreterConfigs;
    }
}
