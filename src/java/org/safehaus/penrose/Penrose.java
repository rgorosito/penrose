/**
 * Copyright (c) 1998-2005, Verge Lab., LLC.
 * All rights reserved.
 */
package org.safehaus.penrose;

import java.util.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.net.ServerSocket;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import mx4j.log.Log4JLogger;
import mx4j.tools.config.ConfigurationLoader;

import org.ietf.ldap.*;
import org.safehaus.penrose.config.*;
import org.safehaus.penrose.event.*;
import org.safehaus.penrose.module.ModuleContext;
import org.safehaus.penrose.module.Module;
import org.safehaus.penrose.schema.Schema;
import org.safehaus.penrose.schema.AttributeType;
import org.safehaus.penrose.schema.ObjectClass;
import org.safehaus.penrose.schema.SchemaParser;
import org.safehaus.penrose.engine.Engine;
import org.safehaus.penrose.engine.TransformEngine;
import org.safehaus.penrose.engine.EngineConfig;
import org.safehaus.penrose.engine.EngineContext;
import org.safehaus.penrose.interpreter.Interpreter;
import org.safehaus.penrose.interpreter.InterpreterConfig;
import org.safehaus.penrose.cache.*;
import org.safehaus.penrose.connection.*;
import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.filter.FilterTool;
import org.safehaus.penrose.acl.AclTool;
import org.safehaus.penrose.management.PenroseClient;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import sun.misc.SignalHandler;
import sun.misc.Signal;

/**
 * @author Endi S. Dewata
 */
public class Penrose implements
        AdapterContext,
        CacheContext,
        EngineContext,
        ModuleContext,
        PenroseMBean,
        SignalHandler {
	
	// ------------------------------------------------
	// Constants
	// ------------------------------------------------
    public final static String ENGINE_LOGGER     = "org.safehaus.penrose.engine";
    public final static String CONFIG_LOGGER     = "org.safehaus.penrose.config";
    public final static String SCHEMA_LOGGER     = "org.safehaus.penrose.schema";
    public final static String FILTER_LOGGER     = "org.safehaus.penrose.filter";
    public final static String ADAPTER_LOGGER    = "org.safehaus.penrose.adapter";
    public final static String PASSWORD_LOGGER   = "org.safehaus.penrose.password";
    public final static String CACHE_LOGGER      = "org.safehaus.penrose.cache";
    public final static String CONNECTION_LOGGER = "org.safehaus.penrose.connection";
    public final static String TRANSFORM_LOGGER  = "org.safehaus.penrose.transform";
    public final static String MODULE_LOGGER     = "org.safehaus.penrose.module";
    public final static String SECURITY_LOGGER   = "org.safehaus.penrose.security";

    public final static String SEARCH_LOGGER     = "org.safehaus.penrose.search";
    public final static String BIND_LOGGER       = "org.safehaus.penrose.bind";
    public final static String ADD_LOGGER        = "org.safehaus.penrose.add";
    public final static String MODIFY_LOGGER     = "org.safehaus.penrose.modify";
    public final static String DELETE_LOGGER     = "org.safehaus.penrose.delete";
    public final static String COMPARE_LOGGER    = "org.safehaus.penrose.compare";
    public final static String MODRDN_LOGGER     = "org.safehaus.penrose.modrdn";

    public final static String PENROSE_HOME      = "org.safehaus.penrose.home";

	public final static boolean SEARCH_IN_BACKGROUND = true;
	public final static int WAIT_TIMEOUT = 10000; // wait timeout is 10 seconds

	private List suffixes = new ArrayList();
	private List normalizedSuffixes = new ArrayList();

	private String trustedKeyStore;

	private String rootDn;
	private String rootPassword;

	private AclTool aclTool;
	private FilterTool filterTool;


	private TransformEngine transformEngine;

    private Map caches = new LinkedHashMap();
	private Map engines = new LinkedHashMap();
    private Map connections = new LinkedHashMap();

    private ServerConfig serverConfig;
	private Config config;
    private Schema schema;

	private Logger log = Logger.getLogger(ENGINE_LOGGER);

	private boolean stopRequested = false;

	private PenroseConnectionPool connectionPool = new PenroseConnectionPool(this);

	public Penrose() {
	}

	public Config getConfig() {
		return config;
	}

	/**
	 * Initialize server with a set of suffixes.
	 * 
	 * @param suffixes
	 */
	public void setSuffix(String suffixes[]) {
		log.debug("-------------------------------------------------------------------------------");
		log.debug("Penrose.setSuffix(suffixArray);");

		for (int i = 0; i < suffixes.length; i++) {
			log.debug(" suffix            : " + suffixes[i]);
			this.suffixes.add(suffixes[i]);
		}

		for (int i = 0; i < suffixes.length; i++) {
			this.normalizedSuffixes.add(LDAPDN.normalize(suffixes[i]));
		}

	}

	public int init() throws Exception {

        PropertyConfigurator.configure("conf/log4j.properties");

        initServer();
        loadConfig("conf");

        log.warn("Penrose is ready.");

		return LDAPException.SUCCESS;
	}

    public void initCache() throws Exception {
        for (Iterator i=serverConfig.getCacheConfigs().iterator(); i.hasNext(); ) {
            CacheConfig cacheConfig = (CacheConfig)i.next();

            Class clazz = Class.forName(cacheConfig.getCacheClass());
            Cache cache = (Cache)clazz.newInstance();
            cache.init(cacheConfig, this);

            caches.put(cacheConfig.getCacheName(), cache);
        }
    }

    public void initEngine() throws Exception {

        for (Iterator i=serverConfig.getEngineConfigs().iterator(); i.hasNext(); ) {
            EngineConfig engineConfig = (EngineConfig)i.next();

            Class clazz = Class.forName(engineConfig.getEngineClass());
            Engine engine = (Engine)clazz.newInstance();
            engine.init(engineConfig, this);

            engines.put(engineConfig.getEngineName(), engine);
        }
    }

    public Connection getConnection(String name) {
        return (Connection)connections.get(name);
    }

    public void initJmx() {
        // Register JMX
        MBeanServer server = null;
        try {
            ArrayList servers = MBeanServerFactory.findMBeanServer(null);
            server = (MBeanServer) servers.get(0);

        } catch (Exception ex) {
            log.debug("Default MBeanServer has not been created yet.");
        }

        if (server == null) {
            try {
                log.debug("Creating MBeanServer...");

                // MX4J's logging redirection to Apache's Commons Logging
                mx4j.log.Log.redirectTo(new Log4JLogger());

                // Create the MBeanServer
                server = MBeanServerFactory.createMBeanServer();

                // Create the ConfigurationLoader
                ConfigurationLoader loader = new ConfigurationLoader();

                // Register the configuration loader into the MBeanServer
                ObjectName name = ObjectName.getInstance(":service=configuration");
                server.registerMBean(loader, name);

                // Tell the configuration loader the XML configuration file
                Reader reader = new BufferedReader(new FileReader("conf/mx4j.xml"));
                loader.startup(reader);
                reader.close();

                log.debug("Done creating MBeanServer.");

            } catch (Exception ex) {
                log.error(ex.toString(), ex);
            }
        }

        if (server != null) {
            try {
                server.registerMBean(this, ObjectName.getInstance(PenroseClient.MBEAN_NAME));
                //server.registerMBean(engine, ObjectName.getInstance("Penrose:type=Engine"));
                //server.registerMBean(connectionPool, ObjectName.getInstance("Penrose:type=PenroseConnectionPool"));
                //server.registerMBean(threadPool, ObjectName.getInstance("Penrose:type=ThreadPool"));
            } catch (Exception ex) {
                log.error(ex.toString(), ex);
            }
        }

    }

    public void initServer() throws Exception {

        log.debug("-------------------------------------------------------------------------------");
        log.debug("Penrose.initServer()");

        ServerConfigReader reader = new ServerConfigReader();
        reader.read("conf/server.xml");

        serverConfig = reader.getServerConfig();
        log.debug(serverConfig.toString());

        aclTool = new AclTool(this);
        filterTool = new FilterTool(this);
        transformEngine = new TransformEngine(this);

        loadSchema();
        initCache();
        initEngine();

        if (trustedKeyStore != null) System.setProperty("javax.net.ssl.trustStore", trustedKeyStore);

        initJmx();
    }

	public void loadConfig(String directory) throws Exception {

        log.debug("-------------------------------------------------------------------------------");
        log.debug("Penrose.loadConfig(\""+directory+"\")");

        ConfigReader reader = new ConfigReader();
        reader.loadSourcesConfig(directory+"/sources.xml");
        reader.loadMappingConfig(directory+"/mapping.xml");
        reader.loadModulesConfig(directory+"/modules.xml");

        config = reader.getConfig();
        log.debug(config.toString());

        config.init();

        Engine engine = getEngine();
        engine.setConfig(config);

        initConnections(config, serverConfig);
	}

    public void initConnections(Config config, ServerConfig serverConfig) throws Exception {
        for (Iterator i = config.getConnectionConfigs().iterator(); i.hasNext();) {
            ConnectionConfig connectionConfig = (ConnectionConfig) i.next();

            String adapterName = connectionConfig.getAdapterName();
            if (adapterName == null) throw new Exception("Missing adapter name");

            AdapterConfig adapterConfig = serverConfig.getAdapterConfig(adapterName);
            if (adapterConfig == null) throw new Exception("Undefined adapter "+adapterName);

            String adapterClass = adapterConfig.getAdapterClass();
            Class clazz = Class.forName(adapterClass);
            Adapter adapter = (Adapter)clazz.newInstance();

            Connection connection = new Connection();
            connection.init(connectionConfig, adapter);

            adapter.init(adapterConfig, this, connection);

            connections.put(connectionConfig.getConnectionName(), connection);
        }

    }

    public void loadSchema() throws Exception {

        schema = new Schema();

        File schemaDir = new File("schema");

        File schemaFiles[] = schemaDir.listFiles();
        for (int i=0; i<schemaFiles.length; i++) {
            if (schemaFiles[i].isDirectory()) continue;
            loadSchema(schemaFiles[i].getAbsolutePath());
        }
    }

	public void loadSchema(String filename) throws Exception {

        log.debug("Penrose.loadSchema(\""+filename+"\")");

		FileReader in = new FileReader(filename);
		SchemaParser parser = new SchemaParser(in);
		Collection c = parser.parse();

		Map attributeTypes = schema.getAttributeTypes();
		Map objectClasses = schema.getObjectClasses();

		for (Iterator i = c.iterator(); i.hasNext();) {
			Object o = i.next();
			if (o instanceof AttributeType) {
				AttributeType at = (AttributeType) o;
				attributeTypes.put(at.getName(), at);

			} else if (o instanceof ObjectClass) {
				ObjectClass oc = (ObjectClass) o;
				//log.debug("Adding object class "+oc.getName());
				objectClasses.put(oc.getName(), oc);

			} else {
				//log.debug(" - ERROR");
			}
		}
	}

    public Engine getEngine() {
        return getEngine("DEFAULT");
    }

    public Engine getEngine(String name) {
        return (Engine)engines.get(name);
    }

    public Collection getModules(String dn) throws Exception {
        return config.getModules(dn);
    }

    public int bind(PenroseConnection connection, String dn, String password) throws Exception {

        log.info("-------------------------------------------------");
        log.info("BIND:");
        log.info(" - dn      : "+dn);

        BindEvent beforeBindEvent = new BindEvent(this, BindEvent.BEFORE_BIND, connection, dn, password);
        postEvent(dn, beforeBindEvent);

        int rc = getEngine().bind(connection, dn, password);

        BindEvent afterBindEvent = new BindEvent(this, BindEvent.AFTER_BIND, connection, dn, password);
        afterBindEvent.setReturnCode(rc);
        postEvent(dn, afterBindEvent);

        return rc;
    }

    public int unbind(PenroseConnection connection) throws Exception {
        return getEngine().unbind(connection);
    }

    public SearchResults search(PenroseConnection connection, String base, int scope,
            int deref, String filter, Collection attributeNames)
            throws Exception {

        String s = null;
        switch (scope) {
        case LDAPConnection.SCOPE_BASE:
            s = "base";
            break;
        case LDAPConnection.SCOPE_ONE:
            s = "one level";
            break;
        case LDAPConnection.SCOPE_SUB:
            s = "subtree";
            break;
        }

        String d = null;
        switch (deref) {
        case LDAPSearchConstraints.DEREF_NEVER:
            d = "never";
            break;
        case LDAPSearchConstraints.DEREF_SEARCHING:
            d = "searching";
            break;
        case LDAPSearchConstraints.DEREF_FINDING:
            d = "finding";
            break;
        case LDAPSearchConstraints.DEREF_ALWAYS:
            d = "always";
            break;
        }

        log.info("-------------------------------------------------");
        log.info("SEARCH:");
        if (connection.getBindDn() != null) log.info(" - bindDn: " + connection.getBindDn());
        log.info(" - base: " + base);
        log.info(" - scope: " + s);
        log.debug(" - deref: " + d);
        log.info(" - filter: " + filter);
        log.debug(" - attr: " + attributeNames);
        log.info("");

        SearchEvent beforeSearchEvent = new SearchEvent(this, SearchEvent.BEFORE_SEARCH, connection, base);
        postEvent(base, beforeSearchEvent);

        SearchResults results = getEngine().search(connection, base, scope, deref, filter, attributeNames);

        SearchEvent afterSearchEvent = new SearchEvent(this, SearchEvent.AFTER_SEARCH, connection, base);
        afterSearchEvent.setReturnCode(results.getReturnCode());
        postEvent(base, afterSearchEvent);

        return results;
    }

    public int add(PenroseConnection connection, LDAPEntry entry) throws Exception {

        log.info("-------------------------------------------------");
        log.info("ADD:");
        if (connection.getBindDn() != null) log.info(" - bindDn: "+connection.getBindDn());
        log.info(Entry.toString(entry));
        log.info("");

        AddEvent beforeModifyEvent = new AddEvent(this, AddEvent.BEFORE_ADD, connection, entry);
        postEvent(entry.getDN(), beforeModifyEvent);

        int rc = getEngine().add(connection, entry);

        AddEvent afterModifyEvent = new AddEvent(this, AddEvent.AFTER_ADD, connection, entry);
        afterModifyEvent.setReturnCode(rc);
        postEvent(entry.getDN(), afterModifyEvent);

        return rc;
    }

    public int delete(PenroseConnection connection, String dn) throws Exception {

        log.info("-------------------------------------------------");
        log.info("DELETE:");
        if (connection.getBindDn() != null) log.info(" - bindDn: "+connection.getBindDn());
        log.info(" - dn: "+dn);
        log.info("");

        DeleteEvent beforeDeleteEvent = new DeleteEvent(this, DeleteEvent.BEFORE_DELETE, connection, dn);
        postEvent(dn, beforeDeleteEvent);

        int rc = getEngine().delete(connection, dn);

        DeleteEvent afterDeleteEvent = new DeleteEvent(this, DeleteEvent.AFTER_DELETE, connection, dn);
        afterDeleteEvent.setReturnCode(rc);
        postEvent(dn, afterDeleteEvent);

        return rc;
    }

    public int modify(PenroseConnection connection, String dn, List modifications) throws Exception {

        log.info("-------------------------------------------------");
		log.info("MODIFY:");
		if (connection.getBindDn() != null) log.info(" - bindDn: " + connection.getBindDn());
        log.info(" - dn: " + dn);
        log.debug("-------------------------------------------------");
		log.debug("changetype: modify");

		for (Iterator i = modifications.iterator(); i.hasNext();) {
			LDAPModification modification = (LDAPModification) i.next();

			LDAPAttribute attribute = modification.getAttribute();
			String attributeName = attribute.getName();
			String values[] = attribute.getStringValueArray();

			switch (modification.getOp()) {
			case LDAPModification.ADD:
				log.debug("add: " + attributeName);
				for (int j = 0; j < values.length; j++)
					log.debug(attributeName + ": " + values[j]);
				break;
			case LDAPModification.DELETE:
				log.debug("delete: " + attributeName);
				for (int j = 0; j < values.length; j++)
					log.debug(attributeName + ": " + values[j]);
				break;
			case LDAPModification.REPLACE:
				log.debug("replace: " + attributeName);
				for (int j = 0; j < values.length; j++)
					log.debug(attributeName + ": " + values[j]);
				break;
			}
			log.debug("-");
		}

        log.info("");

        ModifyEvent beforeModifyEvent = new ModifyEvent(this, ModifyEvent.BEFORE_MODIFY, connection, dn, modifications);
        postEvent(dn, beforeModifyEvent);

        int rc = getEngine().modify(connection, dn, modifications);

        ModifyEvent afterModifyEvent = new ModifyEvent(this, ModifyEvent.AFTER_MODIFY, connection, dn, modifications);
        afterModifyEvent.setReturnCode(rc);
        postEvent(dn, afterModifyEvent);

        return rc;
    }

    public int modrdn(PenroseConnection connection, String dn, String newRdn) throws Exception {

        log.debug("-------------------------------------------------------------------------------");
        log.debug("COMPARE:");
        if (connection.getBindDn() != null) log.info(" - bindDn: " + connection.getBindDn());
        log.debug("  dn: " + dn);
        log.debug("  new rdn: " + newRdn);

        return getEngine().modrdn(connection, dn, newRdn);
    }

	public int compare(PenroseConnection connection, String dn, String attributeName,
			String attributeValue) throws Exception {

        log.debug("-------------------------------------------------------------------------------");
        log.debug("COMPARE:");
        if (connection.getBindDn() != null) log.info(" - bindDn: " + connection.getBindDn());
        log.debug("  dn: " + dn);
        log.debug("  attributeName: " + attributeName);
        log.debug("  attributeValue: " + attributeValue);
        log.debug("-------------------------------------------------------------------------------");

        return getEngine().compare(connection, dn, attributeName, attributeValue);
	}

    public void postEvent(String dn, Event event) throws Exception {
        Collection c = getModules(dn);

        for (Iterator i=c.iterator(); i.hasNext(); ) {
            Module module = (Module)i.next();

            if (event instanceof AddEvent) {
                switch (event.getType()) {
                    case AddEvent.BEFORE_ADD:
                        module.beforeAdd((AddEvent)event);
                        break;

                    case AddEvent.AFTER_ADD:
                        module.afterAdd((AddEvent)event);
                        break;
                }

            } else if (event instanceof BindEvent) {

                switch (event.getType()) {
                    case BindEvent.BEFORE_BIND:
                        module.beforeBind((BindEvent)event);
                        break;

                    case BindEvent.AFTER_BIND:
                        module.afterBind((BindEvent)event);
                        break;
                }

            } else if (event instanceof DeleteEvent) {

                switch (event.getType()) {
                    case DeleteEvent.BEFORE_DELETE:
                        module.beforeDelete((DeleteEvent)event);
                        break;

                    case DeleteEvent.AFTER_DELETE:
                        module.afterDelete((DeleteEvent)event);
                        break;
                }

            } else if (event instanceof ModifyEvent) {

                switch (event.getType()) {
                case ModifyEvent.BEFORE_MODIFY:
                    module.beforeModify((ModifyEvent)event);
                    break;

                case ModifyEvent.AFTER_MODIFY:
                    module.afterModify((ModifyEvent)event);
                    break;
                }

            } else if (event instanceof SearchEvent) {

                switch (event.getType()) {
                    case SearchEvent.BEFORE_SEARCH:
                        module.beforeSearch((SearchEvent)event);
                        break;

                    case SearchEvent.AFTER_SEARCH:
                        module.afterSearch((SearchEvent)event);
                        break;
                }

            }
        }
    }

	/**
	 * Convert entry to string.
	 * 
	 * @param entry Entry.
	 * @return LDAP entry in LDIF format
	 * @throws Exception
	 */
	public String toString(LDAPEntry entry) throws Exception {
        return Entry.toString(entry);
	}

	/**
	 * Shutdown gracefully
	 */
	public void stop() {
        
        if (stopRequested) return;

        try {
            log.debug("Stop requested...");
            stopRequested = true;

            Engine engine = getEngine();
            if (engine != null) engine.stop();

            // close all the pools, including all the connections
            //connectionPool.closeAll();

            log.warn("Penrose has been shutdown.");

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            //System.exit(0);
        }
	}

    public PenroseConnection openConnection() throws Exception {
        return connectionPool.createConnection();
    }

    public void removeConnection(PenroseConnection connection) {
        connectionPool.removeConnection(connection);
    }

	// ------------------------------------------------
	// Listeners
	// ------------------------------------------------

	public void addConnectionListener(ConnectionListener l) {
	}

	public void removeConnectionListener(ConnectionListener l) {
	}

	public void addBindListener(BindListener l) {
	}

	public void removeBindListener(BindListener l) {
	}

	public void addSearchListener(SearchListener l) {
	}

	public void removeSearchListener(SearchListener l) {
	}

	public void addCompareListener(CompareListener l) {
	}

	public void removeCompareListener(CompareListener l) {
	}

	public void addAddListener(AddListener l) {
	}

	public void removeAddListener(AddListener l) {
	}

	public void addDeleteListener(DeleteListener l) {
	}

	public void removeDeleteListener(DeleteListener l) {
	}

	public void addModifyListener(ModifyListener l) {
	}

	public void removeModifyListener(ModifyListener l) {
	}

	// ------------------------------------------------
	// Getters and Setters
	// ------------------------------------------------
	
	public AclTool getAclTool() {
		return aclTool;
	}

    public void setAclTool(AclTool aclTool) {
		this.aclTool = aclTool;
	}

	public Collection getEngines() {
		return engines.values();
	}
	public PenroseConnectionPool getConnectionPool() {
		return connectionPool;
	}
	public void setConnectionPool(PenroseConnectionPool connectionPool) {
		this.connectionPool = connectionPool;
	}

	public FilterTool getFilterTool() {
		return filterTool;
	}
	public void setFilterTool(FilterTool filterTool) {
		this.filterTool = filterTool;
	}
	public Logger getLog() {
		return log;
	}
	public void setLog(Logger log) {
		this.log = log;
	}
	public List getNormalizedSuffixes() {
		return normalizedSuffixes;
	}
	public void setNormalizedSuffixes(List normalizedSuffixes) {
		this.normalizedSuffixes = normalizedSuffixes;
	}
	public String getRootDn() {
		return rootDn;
	}
	public void setRootDn(String rootDn) {
		this.rootDn = rootDn;
	}
	public String getRootPassword() {
		return rootPassword;
	}
	public void setRootPassword(String rootPassword) {
		this.rootPassword = rootPassword;
	}
	public boolean isStopRequested() {
		return stopRequested;
	}
	public void setStopRequested(boolean stopRequested) {
		this.stopRequested = stopRequested;
	}
	public List getSuffixes() {
		return suffixes;
	}
	public void setSuffixes(List suffixes) {
		this.suffixes = suffixes;
	}
	public TransformEngine getTransformEngine() {
		return transformEngine;
	}
	public void setTransformEngine(TransformEngine transformEngine) {
		this.transformEngine = transformEngine;
	}
	public String getTrustedKeyStore() {
		return trustedKeyStore;
	}
	public void setTrustedKeyStore(String trustedKeyStore) {
		this.trustedKeyStore = trustedKeyStore;
	}
	public void setConfig(Config config) {
		this.config = config;
	}

	public String readConfigFile(String filename) throws IOException {
		File file = new File(filename);
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		StringBuffer sb = new StringBuffer();
		String line = "";
		while (line != null) {
			line = br.readLine();
			if (line == null) break;
			sb.append(line);
			sb.append("\n");
		}
		fr.close();
		return sb.toString();
	}
	
	public void writeConfigFile(String filename, String content) throws IOException {
		File file = new File(filename);
		FileWriter fw = new FileWriter(file);
		fw.write(content);
		fw.close();
	}

    public Interpreter newInterpreter() throws Exception {
        InterpreterConfig interpreterConfig = serverConfig.getInterpreterConfig("DEFAULT");
        Class clazz = Class.forName(interpreterConfig.getInterpreterClass());
        return (Interpreter)clazz.newInstance();
    }

    public Cache getCache() {
        return getCache("DEFAULT");
    }

    public Cache getCache(String name) {
        return (Cache)caches.get(name);
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    /**
     * Ctrl-C (Interrupt Signal) handler
     *
     * The obvious drawback with this is that it relies on undocumented classes from
     * Sun. There are other solutions, including one given at
     * http://www.naturalbridge.com/useful/index.html and another at
     * http://interstice.com/~kevinh/projects/javasignals/ but both of these use
     * native code.
     */
    public void initSignalHandler() {
        Signal.handle(new Signal("INT"), this);
    }

    public void handle(Signal sig) {
        //code to be executed goes here
        log.info("Interrupt Signal (Ctrl-C) caught! Initiating shutdown...");
        listAllThreads();
        stop();
    }

    public void listAllThreads() {
        // Find the root thread group
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        log.debug(".. ThreadGroup: "+root.getName());

        while (root.getParent() != null) {
            root = root.getParent();
            log.debug(".. ThreadGroup: "+root.getName());
        }

        // Visit each thread group
        visit(root, 0);
    }

    /**
     * This method recursively visits all thread groups under `group'.
     */
    private void visit(ThreadGroup group, int level) {
        //logger.debug(group.toString());

        // Get threads in 'group'
        int numThreads = group.activeCount();
        Thread[] threads = new Thread[numThreads * 2];
        numThreads = group.enumerate(threads, false);

        // Enumerate each thread in 'group'
        for (int i = 0; i < numThreads; i++) {
            // Get thread
            Thread thread = threads[i];
            StringBuffer sb = new StringBuffer();
            for (int j=0; j<level; j++) sb.append("  ");
            sb.append(thread.toString());
            log.debug(sb.toString());
        }

        // Get thread subgroups of 'group'
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups * 2];
        numGroups = group.enumerate(groups, false);

        // Recursively visit each subgroup
        for (int i = 0; i < numGroups; i++) {
            visit(groups[i], level + 1);
        }
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }
}