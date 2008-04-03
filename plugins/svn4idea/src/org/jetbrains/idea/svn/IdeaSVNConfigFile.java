package org.jetbrains.idea.svn;

import org.jetbrains.idea.svn.config.DefaultProxyGroup;
import org.jetbrains.idea.svn.config.ProxyGroup;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IdeaSVNConfigFile {
  private Map<String, String> myPatternsMap;
  private long myLatestUpdate;
  private File myFile;
  private Map myDefaultProperties;

  private final SVNConfigFile mySVNConfigFile;

  private static final String GROUPS_GROUP_NAME = "groups";

  public IdeaSVNConfigFile(final File file) {
    mySVNConfigFile = new SVNConfigFile(file);
    myFile = file;
    myLatestUpdate = -1;
    myPatternsMap = new HashMap<String, String>();
  }

  public void updateGroups() {
    if (myLatestUpdate != myFile.lastModified()) {
      myPatternsMap.clear();
      myPatternsMap.putAll(mySVNConfigFile.getProperties(GROUPS_GROUP_NAME));

      myDefaultProperties = mySVNConfigFile.getProperties(DefaultProxyGroup.DEFAULT_GROUP_NAME);
    }
  }

  public Map<String, ProxyGroup> getAllGroups() {
    final Map<String, ProxyGroup> result = new HashMap<String, ProxyGroup>(myPatternsMap.size());
    for (Map.Entry<String, String> entry : myPatternsMap.entrySet()) {
      final String groupName = entry.getKey();
      result.put(groupName, new ProxyGroup(groupName, entry.getValue(), mySVNConfigFile.getProperties(groupName)));
    }
    return result;
  }

  public DefaultProxyGroup getDefaultGroup() {
    return new DefaultProxyGroup(myDefaultProperties);
  }

  public void deleteGroup(final String name) {
    // remove all properties
    final Map<String, String> properties = mySVNConfigFile.getProperties(name);
    for (String propertyName : properties.keySet()) {
      mySVNConfigFile.setPropertyValue(name, propertyName, null, false);
    }
    if (DefaultProxyGroup.DEFAULT_GROUP_NAME.equals(name)) {
      myDefaultProperties.clear();
    }
    // remove group from groups
    mySVNConfigFile.setPropertyValue(GROUPS_GROUP_NAME, name, null, false);
  }

  public void addGroup(final String name, final String patterns, final Map<String, String> properties) {
    mySVNConfigFile.setPropertyValue(GROUPS_GROUP_NAME, name, patterns, false);
    addProperties(name, properties);
  }

  private void addProperties(final String groupName, final Map<String, String> properties) {
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      mySVNConfigFile.setPropertyValue(groupName, entry.getKey(), entry.getValue(), false);
    }
  }

  public void modifyGroup(final String name, final String patterns, final Collection<String> delete, final Map<String, String> addOrModify,
                          final boolean isDefault) {
    if (! isDefault) {
      mySVNConfigFile.setPropertyValue(GROUPS_GROUP_NAME, name, patterns, false);
    }
    final Map<String,String> deletedPrepared = new HashMap<String, String>(delete.size());
    for (String property : delete) {
      deletedPrepared.put(property, null);
    }
    addProperties(name, deletedPrepared);
    addProperties(name, addOrModify);
  }

  public void save() {
    mySVNConfigFile.save();
  }
}
