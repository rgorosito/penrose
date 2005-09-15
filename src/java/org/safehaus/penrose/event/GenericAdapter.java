/**
 * Copyright (c) 2000-2005, Identyx Corporation.
 * All rights reserved.
 */
package org.safehaus.penrose.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.safehaus.penrose.Penrose;

/**
 * @author Endi S. Dewata
 */
public class GenericAdapter implements BindListener, AddListener, ModifyListener {

    Logger log = LoggerFactory.getLogger(getClass());

    public GenericAdapter() {
    	log.debug("GenericAdapter.<init>()");
    }

    public void beforeBind(BindEvent e) throws Exception {
    	log.debug("GenericAdapter.beforeBind()");
    }

    public void afterBind(BindEvent e) throws Exception {
    	log.debug("GenericAdapter.afterBind()");
    }

    public void beforeUnbind(BindEvent e) throws Exception {
    	log.debug("GenericAdapter.beforeUnbind()");
    }

    public void afterUnbind(BindEvent e) throws Exception {
    	log.debug("GenericAdapter.afterUnbind()");
    }

    public void beforeAdd(AddEvent event) throws Exception {
    	log.debug("GenericAdapter.beforeAdd()");
    }

    public void afterAdd(AddEvent event) throws Exception {
    	log.debug("GenericAdapter.afterAdd()");
    }

    public void beforeModify(ModifyEvent event) throws Exception {
    	log.debug("GenericAdapter.beforeModify()");
    }

    public void afterModify(ModifyEvent event) throws Exception {
    	log.debug("GenericAdapter.afterModify()");
    }
}
