package org.safehaus.penrose.jdbc.source;

import org.safehaus.penrose.source.*;
import org.safehaus.penrose.ldap.*;
import org.safehaus.penrose.session.Session;
import org.safehaus.penrose.directory.SourceRef;
import org.safehaus.penrose.directory.FieldRef;
import org.safehaus.penrose.util.TextUtil;
import org.safehaus.penrose.jdbc.*;
import org.safehaus.penrose.jdbc.connection.*;
import org.safehaus.penrose.interpreter.Interpreter;
import org.safehaus.penrose.filter.SimpleFilter;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.filter.FilterTool;

import java.util.Collection;
import java.sql.ResultSet;

/**
 * @author Endi S. Dewata
 */
public class JDBCSource extends Source {

    public final static String BASE_DN      = "baseDn";
    public final static String CATALOG      = "catalog";
    public final static String SCHEMA       = "schema";
    public final static String TABLE        = "table";
    public final static String FILTER       = "filter";

    public final static String SIZE_LIMIT   = "sizeLimit";
    public final static String CREATE       = "create";

    public final static String AUTHENTICATION          = "authentication";
    public final static String AUTHENTICATION_DEFAULT  = "default";
    public final static String AUTHENTICATION_FULL     = "full";
    public final static String AUTHENTICATION_DISABLED = "disabled";

    JDBCConnection connection;

    String sourceBaseDn;

    public JDBCSource() {
    }

    public void init() throws Exception {
        connection = (JDBCConnection)getConnection();

        sourceBaseDn = getParameter(BASE_DN);

        boolean create = Boolean.parseBoolean(getParameter(CREATE));
        if (create) {
            try {
                create();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Add
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void add(
            final Session session,
            final AddRequest request,
            final AddResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Add "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.getClient(session);

        try {
            InsertStatement statement = new InsertStatement();
            statement.setSourceName(getName());

            RDN rdn = request.getDn().getRdn();

            if (rdn != null) {
                for (String name : rdn.getNames()) {

                    Object value = rdn.get(name);

                    Field field = getField(name);
                    if (field == null) throw new Exception("Unknown field: " + name);

                    statement.addAssignment(new Assignment(field.getOriginalName(), value));
                }
            }

            Attributes attributes = request.getAttributes();

            for (String name : attributes.getNames()) {
                if (rdn != null && rdn.contains(name)) continue;

                Object value = attributes.getValue(name); // get first value

                Field field = getField(name);
                if (field == null) throw new Exception("Unknown field: " + name);

                statement.addAssignment(new Assignment(field.getOriginalName(), value));
            }

            JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
            statementBuilder.setQuote(client.getQuote());

            String sql = statementBuilder.generate(statement);
            Collection<Object> parameters = statementBuilder.getParameters();

            client.executeUpdate(sql, parameters);

            log.debug("Add operation completed.");

        } finally {
            connection.closeClient(session);
        }
    }

    public void add(
            Session session,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            AddRequest request,
            AddResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Add "+ sourceRefs, 80));
            log.debug(TextUtil.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        Interpreter interpreter = getPartition().newInterpreter();

        AddRequestBuilder builder = new AddRequestBuilder(
                sourceRefs,
                sourceValues,
                interpreter,
                request,
                response
        );

        Collection<Statement> statements = builder.generate();

        JDBCClient client = connection.getClient(session);

        try {
            for (Statement statement : statements) {

                JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
                statementBuilder.setQuote(client.getQuote());

                String sql = statementBuilder.generate(statement);
                Collection<Object> parameters = statementBuilder.getParameters();

                client.executeUpdate(sql, parameters);
            }

        } finally {
            connection.closeClient(session);
        }

        log.debug("Add operation completed.");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Compare
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void compare(
            final Session session,
            final CompareRequest request,
            final CompareResponse response
    ) throws Exception {

        SearchRequest newRequest = new SearchRequest();
        newRequest.setDn(request.getDn());
        newRequest.setScope(SearchRequest.SCOPE_BASE);

        SimpleFilter filter = new SimpleFilter(request.getAttributeName(), "=", request.getAttributeValue());
        newRequest.setFilter(filter);

        SearchResponse newResponse = new SearchResponse();

        search(session, newRequest, newResponse);

        boolean result = newResponse.hasNext();

        if (debug) log.debug("Compare operation completed ["+result+"].");
        response.setReturnCode(result ? LDAP.COMPARE_TRUE : LDAP.COMPARE_FALSE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Delete
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void delete(
            final Session session,
            final DeleteRequest request,
            final DeleteResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Delete "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.getClient(session);

        try {
            DeleteStatement statement = new DeleteStatement();

            statement.setSourceName(getName());

            Filter filter = null;

            RDN rdn = request.getDn().getRdn();
            if (rdn != null) {
                for (String name : rdn.getNames()) {
                    Object value = rdn.get(name);

                    SimpleFilter sf = new SimpleFilter(name, "=", value);
                    filter = FilterTool.appendAndFilter(filter, sf);
                }
            }

            statement.setFilter(filter);

            JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
            statementBuilder.setQuote(client.getQuote());

            String sql = statementBuilder.generate(statement);
            Collection<Object> parameters = statementBuilder.getParameters();

            client.executeUpdate(sql, parameters);

            log.debug("Delete operation completed.");

        } finally {
            connection.closeClient(session);
        }
    }

    public void delete(
            Session session,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            DeleteRequest request,
            DeleteResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Delete "+ sourceRefs, 80));
            log.debug(TextUtil.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        Interpreter interpreter = getPartition().newInterpreter();

        DeleteRequestBuilder builder = new DeleteRequestBuilder(
                sourceRefs,
                sourceValues,
                interpreter,
                request,
                response
        );

        Collection<Statement> statements = builder.generate();

        JDBCClient client = connection.getClient(session);

        try {
            for (Statement statement : statements) {

                JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
                statementBuilder.setQuote(client.getQuote());

                String sql = statementBuilder.generate(statement);
                Collection<Object> parameters = statementBuilder.getParameters();

                client.executeUpdate(sql, parameters);
            }

        } finally {
            connection.closeClient(session);
        }

        log.debug("Delete operation completed.");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Modify
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void modify(
            final Session session,
            final ModifyRequest request,
            final ModifyResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Modify "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.getClient(session);

        try {
            UpdateStatement statement = new UpdateStatement();

            statement.setSourceName(getName());

            RDN rdn = request.getDn().getRdn();

            Collection<Modification> modifications = request.getModifications();
            for (Modification modification : modifications) {

                int type = modification.getType();
                Attribute attribute = modification.getAttribute();
                String name = attribute.getName();

                Field field = getField(name);
                if (field == null) continue;

                switch (type) {
                    case Modification.ADD:
                    case Modification.REPLACE:
                        Object value = rdn.get(name);
                        if (value == null) value = attribute.getValue();
                        statement.addAssignment(new Assignment(field.getOriginalName(), value));
                        break;

                    case Modification.DELETE:
                        statement.addAssignment(new Assignment(field.getOriginalName(), null));
                        break;
                }
            }

            Filter filter = null;
            for (String name : rdn.getNames()) {
                Object value = rdn.get(name);

                SimpleFilter sf = new SimpleFilter(name, "=", value);
                filter = FilterTool.appendAndFilter(filter, sf);
            }

            statement.setFilter(filter);

            JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
            statementBuilder.setQuote(client.getQuote());

            String sql = statementBuilder.generate(statement);
            Collection<Object> parameters = statementBuilder.getParameters();

            client.executeUpdate(sql, parameters);

            log.debug("Modify operation completed.");

        } finally {
            connection.closeClient(session);
        }
    }

    public void modify(
            Session session,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            ModifyRequest request,
            ModifyResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Modify "+ sourceRefs, 80));
            log.debug(TextUtil.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        Interpreter interpreter = getPartition().newInterpreter();

        ModifyRequestBuilder builder = new ModifyRequestBuilder(
                sourceRefs,
                sourceValues,
                interpreter,
                request,
                response
        );

        Collection<Statement> statements = builder.generate();

        JDBCClient client = connection.getClient(session);

        try {
            for (Statement statement : statements) {

                JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
                statementBuilder.setQuote(client.getQuote());

                String sql = statementBuilder.generate(statement);
                Collection<Object> parameters = statementBuilder.getParameters();

                client.executeUpdate(sql, parameters);
            }

        } finally {
            connection.closeClient(session);
        }

        log.debug("Modify operation completed.");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ModRdn
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void modrdn(
            final Session session,
            final ModRdnRequest request,
            final ModRdnResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("ModRdn "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.getClient(session);

        try {
            UpdateStatement statement = new UpdateStatement();

            statement.setSourceName(getName());

            RDN newRdn = request.getNewRdn();
            for (String name : newRdn.getNames()) {
                Object value = newRdn.get(name);

                Field field = getField(name);
                if (field == null) continue;

                statement.addAssignment(new Assignment(field.getOriginalName(), value));
            }

            RDN rdn = request.getDn().getRdn();
            Filter filter = null;
            for (String name : rdn.getNames()) {
                Object value = rdn.get(name);

                SimpleFilter sf = new SimpleFilter(name, "=", value);
                filter = FilterTool.appendAndFilter(filter, sf);
            }

            statement.setFilter(filter);

            JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
            statementBuilder.setQuote(client.getQuote());

            String sql = statementBuilder.generate(statement);
            Collection<Object> parameters = statementBuilder.getParameters();

            client.executeUpdate(sql, parameters);

            log.debug("ModRdn operation completed.");

        } finally {
            connection.closeClient(session);
        }
    }

    public void modrdn(
            Session session,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            ModRdnRequest request,
            ModRdnResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("ModRdn "+ sourceRefs, 80));
            log.debug(TextUtil.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        Interpreter interpreter = getPartition().newInterpreter();

        ModRdnRequestBuilder builder = new ModRdnRequestBuilder(
                sourceRefs,
                sourceValues,
                interpreter,
                request,
                response
        );

        Collection<Statement> statements = builder.generate();

        JDBCClient client = connection.getClient(session);

        try {
            for (Statement statement : statements) {

                JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
                statementBuilder.setQuote(client.getQuote());

                String sql = statementBuilder.generate(statement);
                Collection<Object> parameters = statementBuilder.getParameters();

                client.executeUpdate(sql, parameters);
            }

        } finally {
            connection.closeClient(session);
        }

        log.debug("ModRdn operation completed.");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Search
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void search(
            final Session session,
            final SearchRequest request,
            final SearchResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Search "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        response.setSizeLimit(request.getSizeLimit());

        SelectStatement statement = new SelectStatement();

        SourceRef sourceRef = new SourceRef(this);

        Filter filter = null;

        DN dn = request.getDn();
        if (dn != null) {
            RDN rdn = dn.getRdn();
            for (String name : rdn.getNames()) {
                Object value = rdn.get(name);

                SimpleFilter sf = new SimpleFilter(name, "=", value);
                filter = FilterTool.appendAndFilter(filter, sf);
            }
        }

        filter = FilterTool.appendAndFilter(filter, request.getFilter());

        for (FieldRef fieldRef : sourceRef.getFieldRefs()) {
            statement.addColumn(fieldRef.getSourceName()+"."+fieldRef.getOriginalName());
        }
        statement.addSourceName(sourceRef.getAlias(), sourceRef.getSource().getName());
        statement.setFilter(filter);

        String where = getParameter(FILTER);
        if (where != null) {
            statement.setWhere(where);
        }

        for (FieldRef fieldRef : sourceRef.getPrimaryKeyFieldRefs()) {
            statement.addOrder(fieldRef.getSourceName()+"."+fieldRef.getOriginalName());
        }

        QueryResponse queryResponse = new QueryResponse() {
            public void add(Object object) throws Exception {
                ResultSet rs = (ResultSet)object;

                if (sizeLimit > 0 && totalCount >= sizeLimit) {
                    throw LDAP.createException(LDAP.SIZE_LIMIT_EXCEEDED);
                }

                SearchResult searchResult = createSearchResult(rs);
                response.add(searchResult);

                totalCount++;
            }
            public void close() throws Exception {
                response.close();
                super.close();
            }
        };

        String sizeLimit = getParameter(SIZE_LIMIT);
        if (sizeLimit != null) {
            queryResponse.setSizeLimit(Long.parseLong(sizeLimit));
        }

        JDBCClient client = connection.getClient(session);

        try {
            JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
            statementBuilder.setQuote(client.getQuote());

            String sql = statementBuilder.generate(statement);
            Collection<Object> parameters = statementBuilder.getParameters();

            client.executeQuery(sql, parameters, queryResponse);

        } finally {
            connection.closeClient(session);
        }

        log.debug("Search operation completed.");
    }

    public SearchResult createSearchResult(
            ResultSet rs
    ) throws Exception {

        Attributes attributes = new Attributes();
        RDNBuilder rb = new RDNBuilder();

        int column = 1;

        for (Field field : getFields()) {

            Object value = rs.getObject(column++);
            if (value == null) continue;

            String fieldName = field.getName();
            attributes.addValue(fieldName, value);

            if (field.isPrimaryKey()) rb.set(fieldName, value);
        }

        DNBuilder db = new DNBuilder();
        db.append(rb.toRdn());
        db.append(sourceBaseDn);
        DN dn = db.toDn();

        return new SearchResult(dn, attributes);
    }

    public void search(
            final Session session,
            //final Collection<SourceRef> primarySourceRefs,
            final Collection<SourceRef> localSourceRefs,
            final Collection<SourceRef> sourceRefs,
            final SourceValues sourceValues,
            final SearchRequest request,
            final SearchResponse response
    ) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Search "+ sourceRefs, 80));
            log.debug(TextUtil.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        //Interpreter interpreter = partition.newInterpreter();

        response.setSizeLimit(request.getSizeLimit());

        SearchRequestBuilder builder = new SearchRequestBuilder(
                //interpreter,
                getPartition(),
                //primarySourceRefs,
                localSourceRefs,
                sourceRefs,
                sourceValues,
                request,
                response
        );

        SelectStatement statement = builder.generate();

        QueryResponse queryResponse = new QueryResponse() {

            SearchResult lastResult;

            public void add(Object object) throws Exception {
                ResultSet rs = (ResultSet)object;

                if (sizeLimit > 0 && totalCount >= sizeLimit) {
                    throw LDAP.createException(LDAP.SIZE_LIMIT_EXCEEDED);
                }

                //SearchResult searchResult = createSearchResult(primarySourceRefs, sourceRefs, rs);
                SearchResult searchResult = createSearchResult(sourceRefs, rs);
                if (searchResult == null) return;

                if (lastResult == null) {
                    lastResult = searchResult;

                } else if (searchResult.getDn().equals(lastResult.getDn())) {
                    mergeSearchResult(searchResult, lastResult);

                } else {
                    response.add(lastResult);
                    lastResult = searchResult;
                }

                totalCount++;

                if (debug) {
                    searchResult.print();
                }
            }

            public void close() throws Exception {
                if (lastResult != null) {
                    response.add(lastResult);
                }
                response.close();
                super.close();
            }
        };

        String sizeLimit = getParameter(SIZE_LIMIT);

        if (sizeLimit != null) {
            queryResponse.setSizeLimit(Long.parseLong(sizeLimit));
        }

        JDBCClient client = connection.getClient(session);

        try {
            JDBCStatementBuilder statementBuilder = new JDBCStatementBuilder(sourceContext.getPartition());
            statementBuilder.setQuote(client.getQuote());

            String sql = statementBuilder.generate(statement);
            Collection<Object> parameters = statementBuilder.getParameters();

            client.executeQuery(sql, parameters, queryResponse);

        } finally {
            connection.closeClient(session);
        }

        log.debug("Search operation completed.");
    }

    public SearchResult createSearchResult(
            //Collection<SourceRef> primarySourceRefs,
            Collection<SourceRef> sourceRefs,
            ResultSet rs
    ) throws Exception {

        SearchResult searchResult = new SearchResult();

        SourceValues sourceValues = new SourceValues();
        RDNBuilder rb = new RDNBuilder();

        int column = 1;

        for (SourceRef sourceRef : sourceRefs) {
            String alias = sourceRef.getAlias();
            //boolean primarySource = primarySourceRefs.contains(sourceRef);

            Attributes fields = new Attributes();

            for (FieldRef fieldRef : sourceRef.getFieldRefs()) {

                Object value = rs.getObject(column++);

                String fieldName = fieldRef.getName();
                String name = alias + "." + fieldName;

                //if (primarySource && fieldRef.isPrimaryKey()) {
                if (sourceRef.isPrimarySourceRef() && fieldRef.isPrimaryKey()) {
                    if (value == null) return null;
                    rb.set(name, value);
                }

                if (value == null) continue;
                fields.addValue(fieldName, value);
            }

            sourceValues.set(alias, fields);
        }

        searchResult.setSourceValues(sourceValues);
        searchResult.setDn(new DN(rb.toRdn()));

        return searchResult;
    }

    public void mergeSearchResult(SearchResult source, SearchResult destination) {
        SourceValues sourceValues = source.getSourceValues();
        SourceValues destinationValues = destination.getSourceValues();

        destinationValues.add(sourceValues);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Storage
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void create() throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Create "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.createClient();

        StringBuilder sb = new StringBuilder();

        sb.append("create table ");
        sb.append(connection.getTableName(sourceConfig));
        sb.append(" (");

        boolean first = true;
        for (FieldConfig fieldConfig : sourceConfig.getFieldConfigs()) {

            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }

            sb.append(fieldConfig.getName());
            sb.append(" ");

            if (fieldConfig.getOriginalType() == null) {

                sb.append(fieldConfig.getType());

                if (fieldConfig.getLength() > 0) {
                    sb.append("(");
                    sb.append(fieldConfig.getLength());
                    sb.append(")");
                }

                if (fieldConfig.isCaseSensitive()) {
                    sb.append(" binary");
                }

                if (fieldConfig.isAutoIncrement()) {
                    sb.append(" auto_increment");
                }

            } else {
                sb.append(fieldConfig.getOriginalType());
            }
        }
/*
        Collection<String> indexFieldNames = sourceConfig.getIndexFieldNames();
        for (String fieldName : indexFieldNames) {
            sb.append(", index (");
            sb.append(fieldName);
            sb.append(")");
        }
*/
        Collection<String> primaryKeyNames = sourceConfig.getPrimaryKeyNames();
        if (!primaryKeyNames.isEmpty()) {
            sb.append(", primary key (");

            first = true;
            for (String fieldName : primaryKeyNames) {

                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }

                sb.append(fieldName);
            }

            sb.append(")");
        }

        sb.append(")");

        String sql = sb.toString();

        client.executeUpdate(sql);

        client.close();
    }

    public void rename(Source newSource) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Rename "+getName()+" to "+newSource.getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        SourceConfig newSourceConfig = newSource.getSourceConfig();

        JDBCClient client = connection.createClient();

        StringBuilder sb = new StringBuilder();

        sb.append("rename table ");
        sb.append(connection.getTableName(sourceConfig));
        sb.append(" to ");
        sb.append(connection.getTableName(newSourceConfig));

        String sql = sb.toString();

        client.executeUpdate(sql);

        client.close();
    }

    public void clear(Session session) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Clear "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.getClient(session);

        try {
            StringBuilder sb = new StringBuilder();

            sb.append("delete from ");
            sb.append(connection.getTableName(sourceConfig));

            String sql = sb.toString();

            client.executeUpdate(sql);

        } finally {
            connection.closeClient(session);
        }
    }

    public void drop() throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Drop "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.createClient();

        StringBuilder sb = new StringBuilder();

        sb.append("drop table ");
        sb.append(connection.getTableName(sourceConfig));

        String sql = sb.toString();

        client.executeUpdate(sql);

        client.close();
    }

    public void status() throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Status "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        JDBCClient client = connection.createClient();

        final String tableName = connection.getTableName(sourceConfig);

        StringBuilder sb = new StringBuilder();

        sb.append("select count(*) from ");
        sb.append(tableName);

        String sql = sb.toString();

        QueryResponse response = new QueryResponse() {
            public void add(Object object) throws Exception {
                ResultSet rs = (ResultSet)object;
                log.error("Table "+tableName+": "+rs.getObject(1));
            }
        };

        client.executeQuery(sql, response);

        sb = new StringBuilder();

        sb.append("select ");

        boolean first = true;
        for (FieldConfig fieldConfig : sourceConfig.getFieldConfigs()) {

            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }

            sb.append("max(length(");
            sb.append(fieldConfig.getOriginalName());
            sb.append("))");
        }

        sb.append(" from ");
        sb.append(tableName);

        sql = sb.toString();

        response = new QueryResponse() {
            public void add(Object object) throws Exception {
                ResultSet rs = (ResultSet)object;

                int index = 1;
                for (FieldConfig fieldConfig : sourceConfig.getFieldConfigs()) {
                    Object length = rs.getObject(index++);
                    int maxLength = fieldConfig.getLength();
                    log.error(" - Field " + fieldConfig.getName() + ": " + length + (maxLength > 0 ? "/" + maxLength : ""));
                }
            }
        };

        client.executeQuery(sql, response);

        client.close();
    }

    public long getCount(Session session) throws Exception {

        if (debug) {
            log.debug(TextUtil.displaySeparator(80));
            log.debug(TextUtil.displayLine("Count "+getName(), 80));
            log.debug(TextUtil.displaySeparator(80));
        }

        final String tableName = connection.getTableName(sourceConfig);

        StringBuilder sb = new StringBuilder();

        sb.append("select count(*) from ");
        sb.append(tableName);

        String sql = sb.toString();

        QueryResponse response = new QueryResponse() {
            public void add(Object object) throws Exception {
                ResultSet rs = (ResultSet)object;
                Long count = rs.getLong(1);
                super.add(count);
            }
        };

        executeQuery(session, sql, response);

        if (!response.hasNext()) {
            throw LDAP.createException(LDAP.OPERATIONS_ERROR);
        }

        Long count = (Long)response.next();
        log.error("Table "+tableName+": "+count);

        return count;
/*
        JDBCClient client = connection.getClient(session);

        try {
            client.executeQuery(sql, response);

            if (!response.hasNext()) {
                throw LDAP.createException(LDAP.OPERATIONS_ERROR);
            }

            Long count = (Long)response.next();
            log.error("Table "+tableName+": "+count);

            return count;

        } finally {
            connection.closeClient(session);
        }
*/
    }

    public String getTableName() throws Exception {
        return connection.getTableName(sourceConfig);
    }

    public void executeQuery(Session session, String sql, QueryResponse response) throws Exception {

        JDBCClient client = connection.getClient(session);

        try {
            client.executeQuery(sql, response);

        } finally {
            connection.closeClient(session);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Clone
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Object clone() throws CloneNotSupportedException {

        JDBCSource source = (JDBCSource)super.clone();

        source.connection       = connection;

        return source;
    }
}