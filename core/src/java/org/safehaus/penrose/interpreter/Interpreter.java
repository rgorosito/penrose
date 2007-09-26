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
package org.safehaus.penrose.interpreter;

import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.ldap.SourceValues;
import org.safehaus.penrose.ldap.RDN;
import org.safehaus.penrose.ldap.Attributes;
import org.safehaus.penrose.ldap.Attribute;
import org.safehaus.penrose.source.Field;
import org.safehaus.penrose.directory.AttributeMapping;
import org.safehaus.penrose.directory.FieldMapping;
import org.safehaus.penrose.directory.FieldRef;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author Endi S. Dewata
 */
public abstract class Interpreter {

    public Logger log = LoggerFactory.getLogger(getClass());

    protected Collection rows;
    protected ClassLoader classLoader;

    public void set(RDN rdn) throws Exception {
        if (rdn == null) return;
        
        for (String name : rdn.getNames()) {
            Object value = rdn.get(name);
            set(name, value);
        }
    }

    public void set(SourceValues av) throws Exception {
        for (String sourceName : av.getNames()) {
            Attributes attributes = av.get(sourceName);

            for (String fieldName : attributes.getNames()) {
                Attribute attribute = attributes.get(fieldName);

                if (attribute.getSize() == 1) {
                    set(sourceName+"."+fieldName, attribute.getValue());
                } else {
                    set(sourceName+"."+fieldName, attribute.getValues());
                }
            }
        }
    }

    public void set(Attributes attributes) throws Exception {
        for (String name : attributes.getNames()) {
            Collection list = attributes.getValues(name);

            Object value;
            if (list.size() == 1) {
                value = list.iterator().next();
            } else {
                value = list;
            }
            set(name, value);
        }
    }

    public abstract Collection parseVariables(String script) throws Exception;

    public abstract void set(String name, Object value) throws Exception;

    public abstract Object get(String name) throws Exception;

    public abstract Object eval(String script) throws Exception;

    public abstract void clear() throws Exception;

    public Object eval(AttributeMapping attributeMapping) throws Exception {
        try {
            Object constant = attributeMapping.getConstant();
            if (constant != null) {
                return constant;
            }

            String variable = attributeMapping.getVariable();
            if (variable != null) {
                return get(variable);

            }

            Expression expression = attributeMapping.getExpression();
            if (expression != null) {
                return eval(expression);

            }

            return null;

        } catch (Exception e) {
            log.error("Error evaluating attribute "+attributeMapping.getName()+": "+e.getMessage());
            throw e;
        }
    }

    public Object eval(Field field) throws Exception {
        try {
            if (field.getConstant() != null) {
                return field.getConstant();

            } else if (field.getVariable() != null) {
                String name = field.getVariable();
                Object value = get(name);
                if (value == null && name.startsWith("rdn.")) {
                    value = get(name.substring(4));
                }
                return value;

            } else if (field.getExpression() != null) {
                return eval(field.getExpression());

            } else {
                return null;
            }
        } catch (Exception e) {
            throw new Exception("Error evaluating field "+field.getName(), e);
        }
    }

    public Object eval(FieldRef fieldRef) throws Exception {
        try {
            if (fieldRef.getConstant() != null) {
                return fieldRef.getConstant();

            } else if (fieldRef.getVariable() != null) {
                String name = fieldRef.getVariable();
                Object value = get(name);
                if (value == null && name.startsWith("rdn.")) {
                    value = get(name.substring(4));
                }
                return value;

            } else if (fieldRef.getExpression() != null) {
                return eval(fieldRef.getExpression());

            } else {
                return null;
            }
        } catch (Exception e) {
            throw new Exception("Error evaluating field "+fieldRef.getName(), e);
        }
    }

    public Object eval(FieldMapping fieldMapping) throws Exception {
        try {
            if (fieldMapping.getConstant() != null) {
                return fieldMapping.getConstant();

            } else if (fieldMapping.getVariable() != null) {
                String name = fieldMapping.getVariable();
                Object value = get(name);
                if (value == null && name.startsWith("rdn.")) {
                    value = get(name.substring(4));
                }
                return value;

            } else if (fieldMapping.getExpression() != null) {
                return eval(fieldMapping.getExpression());

            } else {
                return null;
            }
        } catch (Exception e) {
            throw new Exception("Error evaluating field "+fieldMapping.getName(), e);
        }
    }

    public Object eval(Expression expression) throws Exception {

        String foreach = expression.getForeach();
        String var = expression.getVar();
        String script = expression.getScript();

        Object value = null;
        if (foreach == null) {
            //log.debug("Evaluating expression: "+expression);
            value = eval(script);

        } else {
            //log.debug("Foreach: "+foreach);

            Object v = get(foreach);
            //log.debug("Values: "+v);

            Collection<Object> newValues = new HashSet<Object>();

            if (v != null) {

                Collection<Object> values;
                if (v instanceof Collection) {
                    values = (Collection<Object>)v;
                } else {
                    values = new ArrayList<Object>();
                    values.add(v);
                }

                for (Object o : values) {
                    set(var, o);
                    value = eval(script);
                    if (value == null) continue;

                    //log.debug(" - "+value);
                    newValues.add(value);
                }
            }

            if (newValues.size() == 1) {
                value = newValues.iterator().next();

            } else if (newValues.size() > 1) {
                value = newValues;
            }
        }

        return value;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
