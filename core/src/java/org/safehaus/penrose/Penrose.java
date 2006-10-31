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
package org.safehaus.penrose;

import java.util.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.safehaus.penrose.config.*;
import org.safehaus.penrose.schema.*;
import org.safehaus.penrose.engine.EngineConfig;
import org.safehaus.penrose.engine.Engine;
import org.safehaus.penrose.engine.EngineManager;
import org.safehaus.penrose.handler.Handler;
import org.safehaus.penrose.handler.HandlerManager;
import org.safehaus.penrose.handler.HandlerConfig;
import org.safehaus.penrose.interpreter.InterpreterConfig;
import org.safehaus.penrose.interpreter.InterpreterManager;
import org.safehaus.penrose.partition.*;
import org.safehaus.penrose.session.PenroseSession;
import org.safehaus.penrose.session.SessionManager;
import org.safehaus.penrose.module.ModuleManager;
import org.safehaus.penrose.thread.ThreadManager;
import org.safehaus.penrose.event.EventManager;
import org.safehaus.penrose.log4j.Log4jConfigReader;
import org.safehaus.penrose.log4j.Log4jConfig;
import org.safehaus.penrose.log4j.LoggerConfig;
import org.safehaus.penrose.log4j.AppenderConfig;
import org.safehaus.penrose.connection.ConnectionManager;
import org.safehaus.penrose.source.SourceManager;
import org.safehaus.penrose.cache.EntryCacheManager;
import org.safehaus.penrose.cache.SourceCacheManager;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Endi S. Dewata
 */
public class Penrose {

    Logger log = LoggerFactory.getLogger(getClass());

    public static String PRODUCT_NAME;
    public static String PRODUCT_VERSION;
    public static String VENDOR_NAME;
    public static String PRODUCT_COPYRIGHT = "Copyright (c) 2000-2006, Identyx Corporation.";

    public final static DateFormat DATE_FORMAT   = new SimpleDateFormat("MM/dd/yyyy");
    public final static String RELEASE_DATE      = "09/01/2006";

    public final static String STOPPED  = "STOPPED";
    public final static String STARTING = "STARTING";
    public final static String STARTED  = "STARTED";
    public final static String STOPPING = "STOPPING";

    private PenroseConfig      penroseConfig;

    private ThreadManager      threadManager;
    private SchemaManager      schemaManager;

    private PartitionManager   partitionManager;
    private PartitionValidator partitionValidator;

    private ConnectionManager  connectionManager;
    private SourceCacheManager sourceCacheManager;
    private EntryCacheManager  entryCacheManager;
    private SourceManager      sourceManager;
    private ModuleManager      moduleManager;

    private SessionManager     sessionManager;

    private EngineManager      engineManager;
    private EventManager       eventManager;
    private HandlerManager     handlerManager;

    private InterpreterManager interpreterManager;

    private String status = STOPPED;

    static {
        try {
            Package pkg = Penrose.class.getPackage();

            PRODUCT_NAME    = pkg.getImplementationTitle();
            PRODUCT_VERSION = pkg.getImplementationVersion();
            VENDOR_NAME     = pkg.getImplementationVendor();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected Penrose(PenroseConfig penroseConfig) throws Exception {
        this.penroseConfig = penroseConfig;
        init();
        load();
    }

    protected Penrose(String home) throws Exception {

        penroseConfig = new PenroseConfig();
        penroseConfig.setHome(home);
        loadConfig();

        init();
        load();
    }

    protected Penrose() throws Exception {
        penroseConfig = new PenroseConfig();
        loadConfig();

        init();
        load();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Initialize Penrose components
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    void init() throws Exception {
        initLoggers();

        initThreadManager();
        initInterpreterManager();
        initSchemaManager();
        initSessionManager();

        initConnectionManager();
        initSourceCacheManager();
        initSourceManager();
        initEntryCacheManager();
        initModuleManager();
        initPartitionManager();

        initEngineManager();
        initEventManager();
        initHandlerManager();
    }

    public void initLoggers() throws Exception {

        String home = penroseConfig.getHome();

        File log4jXml = new File((home == null ? "" : home+File.separator)+"conf"+File.separator+"log4j.xml");
        if (!log4jXml.exists()) return;

        Log4jConfigReader configReader = new Log4jConfigReader(log4jXml);
        Log4jConfig config = configReader.read();

        log.debug("Appenders:");
        for (Iterator i=config.getAppenderConfigs().iterator(); i.hasNext(); ) {
            AppenderConfig appenderConfig = (AppenderConfig)i.next();
            log.debug(" - "+appenderConfig.getName());
        }

        log.debug("Loggers:");
        for (Iterator i=config.getLoggerConfigs().iterator(); i.hasNext(); ) {
            LoggerConfig loggerConfig = (LoggerConfig)i.next();
            log.debug(" - "+loggerConfig.getName()+": "+loggerConfig.getLevel()+" "+loggerConfig.getAppenders());
        }
    }

    public void initThreadManager() throws Exception {
        //String s = engineConfig.getParameter(EngineConfig.THREAD_POOL_SIZE);
        //int threadPoolSize = s == null ? EngineConfig.DEFAULT_THREAD_POOL_SIZE : Integer.parseInt(s);

        threadManager = new ThreadManager(20);
    }

    public void initSchemaManager() throws Exception {
        schemaManager = new SchemaManager();
    }

    public void initSessionManager() throws Exception {
        sessionManager = new SessionManager();
        sessionManager.setPenroseConfig(penroseConfig);
    }

    public void initConnectionManager() throws Exception {
        connectionManager = new ConnectionManager();
    }

    public void initSourceCacheManager() throws Exception {
        sourceCacheManager = new SourceCacheManager();
        sourceCacheManager.setConnectionManager(connectionManager);
    }

    public void initEntryCacheManager() throws Exception {
        entryCacheManager = new EntryCacheManager();
        entryCacheManager.setConnectionManager(connectionManager);
        entryCacheManager.init();
    }

    public void initSourceManager() throws Exception {
        sourceManager = new SourceManager();
        sourceManager.setPenroseConfig(penroseConfig);
        sourceManager.setConnectionManager(connectionManager);
        sourceManager.init();
    }

    public void initModuleManager() throws Exception {
        moduleManager = new ModuleManager();
        moduleManager.setPenrose(this);
    }

    public void initPartitionManager() throws Exception {
        partitionManager = new PartitionManager();
        partitionManager.setPenroseConfig(penroseConfig);
        partitionManager.setSchemaManager(schemaManager);
        partitionManager.setInterpreterManager(interpreterManager);
        partitionManager.setConnectionManager(connectionManager);
        partitionManager.setSourceCacheManager(sourceCacheManager);
        partitionManager.setSourceManager(sourceManager);
        partitionManager.setEntryCacheManager(entryCacheManager);
        partitionManager.setModuleManager(moduleManager);
        partitionManager.init();

        partitionValidator = new PartitionValidator();
        partitionValidator.setPenroseConfig(penroseConfig);
        partitionValidator.setSchemaManager(schemaManager);
    }

    public void initInterpreterManager() throws Exception {
        interpreterManager = new InterpreterManager();
    }

    public void initEngineManager() throws Exception {
        engineManager = new EngineManager();
        engineManager.setPenrose(this);
        engineManager.setPenroseConfig(penroseConfig);
        engineManager.setSchemaManager(schemaManager);
        engineManager.setInterpreterFactory(interpreterManager);
        engineManager.setConnectorManager(sourceManager);
        engineManager.setConnectionManager(connectionManager);
        engineManager.setPartitionManager(partitionManager);
    }

    public void initEventManager() throws Exception {
        eventManager = new EventManager();
        eventManager.setModuleManager(moduleManager);
    }

    public void initHandlerManager() throws Exception {
        handlerManager = new HandlerManager();
        handlerManager.setPenroseConfig(penroseConfig);
        handlerManager.setSessionManager(sessionManager);
        handlerManager.setSchemaManager(schemaManager);
        handlerManager.setInterpreterFactory(interpreterManager);
        handlerManager.setPartitionManager(partitionManager);
        handlerManager.setEntryCacheManager(entryCacheManager);
        handlerManager.setModuleManager(moduleManager);
        handlerManager.setThreadManager(threadManager);
        handlerManager.setPenrose(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Load Penrose Configurations
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void loadConfig() throws Exception {

        String home = penroseConfig.getHome();

        penroseConfig.clear();

        PenroseConfigReader reader = new PenroseConfigReader((home == null ? "" : home+File.separator)+"conf"+File.separator+"server.xml");
        reader.read(penroseConfig);
        penroseConfig.setHome(home);
    }

    public void load() throws Exception {

        loadSystemProperties();

        loadInterpreter();
        loadSchemas();

        loadEngine();
        loadHandler();
    }

    public void loadSystemProperties() throws Exception {
        for (Iterator i=penroseConfig.getSystemPropertyNames().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            String value = penroseConfig.getSystemProperty(name);

            System.setProperty(name, value);
        }
    }

    public void loadInterpreter() throws Exception {
        InterpreterConfig interpreterConfig = penroseConfig.getInterpreterConfig();
        interpreterManager.init(interpreterConfig);
    }

    public void loadSchemas() throws Exception {
        schemaManager.load(penroseConfig.getHome(), penroseConfig.getSchemaConfigs());
    }

    public void loadPartitions() throws Exception {

        PartitionConfig partitionConfig = new PartitionConfig();
        partitionConfig.setName("DEFAULT");
        partitionConfig.setPath("conf");
        partitionManager.load(penroseConfig.getHome(), partitionConfig);
        
        partitionManager.load("partitions");

        for (Iterator i=partitionManager.getPartitions().iterator(); i.hasNext(); ) {
            Partition partition = (Partition)i.next();

            Collection results = partitionValidator.validate(partition);

            for (Iterator j=results.iterator(); j.hasNext(); ) {
                PartitionValidationResult resultPartition = (PartitionValidationResult)j.next();

                if (resultPartition.getType().equals(PartitionValidationResult.ERROR)) {
                    log.error("ERROR: "+resultPartition.getMessage()+" ["+resultPartition.getSource()+"]");
                } else {
                    log.warn("WARNING: "+resultPartition.getMessage()+" ["+resultPartition.getSource()+"]");
                }
            }
        }
    }

    public void loadEngine() throws Exception {

        Collection engineConfigs = penroseConfig.getEngineConfigs();

        for (Iterator i=engineConfigs.iterator(); i.hasNext(); ) {
            EngineConfig engineConfig = (EngineConfig)i.next();
            if (engineManager.getEngine(engineConfig.getName()) != null) continue;

            engineManager.init(engineConfig);
        }
    }

    public void loadHandler() throws Exception {

        HandlerConfig handlerConfig = penroseConfig.getHandlerConfig();
        if (handlerManager.getHandler(handlerConfig.getName()) != null) return;

        handlerManager.init(handlerConfig, engineManager);
    }


    public void clear() throws Exception {
        handlerManager.clear();
        engineManager.clear();
        sourceManager.clear();
        interpreterManager.clear();
        connectionManager.clear();
        partitionManager.clear();
        schemaManager.clear();
    }

    public void reload() throws Exception {
        clear();
        loadConfig();
        init();
        load();
    }

    public void store() throws Exception {

        String home = penroseConfig.getHome();
        String filename = (home == null ? "" : home+File.separator)+"conf"+File.separator+"server.xml";
        log.debug("Storing Penrose configuration into "+filename);

        PenroseConfigWriter serverConfigWriter = new PenroseConfigWriter(filename);
        serverConfigWriter.write(penroseConfig);

        partitionManager.store(home);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Start Penrose
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void start() throws Exception {

        if (status != STOPPED) return;

        try {
            status = STARTING;

            loadPartitions();

            partitionManager.start();
            engineManager.start();
            sessionManager.start();
            handlerManager.start();

            status = STARTED;

        } catch (Exception e) {
            status = STOPPED;
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Stop Penrose
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void stop() {

        if (status != STARTED) return;

        try {
            status = STOPPING;

            threadManager.stopRequestAllWorkers();

            handlerManager.stop();
            sessionManager.stop();
            engineManager.stop();
            partitionManager.stop();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        status = STOPPED;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Penrose Sessions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public PenroseSession newSession() throws Exception {

        PenroseSession session = sessionManager.newSession();
        if (session == null) return null;

        HandlerConfig handlerConfig = penroseConfig.getHandlerConfig();
        Handler handler = handlerManager.getHandler(handlerConfig.getName());
        session.setHandler(handler);

        session.setEventManager(eventManager);

        return session;
    }

    public PenroseSession createSession(String sessionId) throws Exception {

        PenroseSession session = sessionManager.createSession(sessionId);
        if (session == null) return null;

        HandlerConfig handlerConfig = penroseConfig.getHandlerConfig();
        Handler handler = handlerManager.getHandler(handlerConfig.getName());
        session.setHandler(handler);

        session.setEventManager(eventManager);

        return session;
    }

    public PenroseSession getSession(String sessionId) throws Exception {
        return sessionManager.getSession(sessionId);
    }

    public PenroseSession removeSession(String sessionId) throws Exception {
        return sessionManager.removeSession(sessionId);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Setters & Getters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public PenroseConfig getPenroseConfig() {
        return penroseConfig;
    }

    public InterpreterManager getInterpreterFactory() {
        return interpreterManager;
    }

    public void setInterpreterFactory(InterpreterManager interpreterManager) {
        this.interpreterManager = interpreterManager;
    }

    public PartitionManager getPartitionManager() {
        return partitionManager;
    }

    public void setPartitionManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public Engine getEngine() {
        return engineManager.getEngine("DEFAULT");
    }

    public Handler getHandler() {
        HandlerConfig handlerConfig = penroseConfig.getHandlerConfig();
        return handlerManager.getHandler(handlerConfig.getName());
    }

    public SchemaManager getSchemaManager() {
        return schemaManager;
    }

    public void setSchemaManager(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    public String getStatus() {
        return status;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public void setModuleManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public ThreadManager getThreadManager() {
        return threadManager;
    }

    public void setThreadManager(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
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
}
