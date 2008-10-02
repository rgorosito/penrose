package org.safehaus.penrose.management.directory;

import org.safehaus.penrose.directory.DirectoryConfig;
import org.safehaus.penrose.directory.EntryConfig;

import java.util.Collection;

/**
 * @author Endi Sukma Dewata
 */
public interface DirectoryServiceMBean {

    public DirectoryConfig getDirectoryConfig() throws Exception;

    public Collection<String> getEntryIds() throws Exception;
    public String createEntry(EntryConfig entryConfig) throws Exception;
    public void updateEntry(String id, EntryConfig entryConfig) throws Exception;
    public void removeEntry(String id) throws Exception;
}