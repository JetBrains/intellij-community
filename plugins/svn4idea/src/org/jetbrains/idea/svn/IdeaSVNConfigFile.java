/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.config.DefaultProxyGroup;
import org.jetbrains.idea.svn.config.ProxyGroup;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IdeaSVNConfigFile {

  public final static String SERVERS_FILE_NAME = "servers";
  public final static String CONFIG_FILE_NAME = "config";

  private final Map<String, String> myPatternsMap;
  private final long myLatestUpdate;
  private final File myFile;
  private Map myDefaultProperties;

  private final SVNConfigFile mySVNConfigFile;

  private static final String GROUPS_GROUP_NAME = "groups";

  public IdeaSVNConfigFile(final File file) {
    mySVNConfigFile = new SVNConfigFile(file);
    myFile = file;
    myLatestUpdate = -1;
    myPatternsMap = new HashMap<>();
  }

  @NotNull
  public static String getNewGroupName(@NotNull String host, @NotNull IdeaSVNConfigFile configFile) {
    String groupName = host;
    final Map<String, ProxyGroup> groups = configFile.getAllGroups();
    while (StringUtil.isEmptyOrSpaces(groupName) || groups.containsKey(groupName)) {
      groupName += "1";
    }
    return groupName;
  }

  public void updateGroups() {
    if (myLatestUpdate != myFile.lastModified()) {
      myPatternsMap.clear();
      myPatternsMap.putAll(mySVNConfigFile.getProperties(GROUPS_GROUP_NAME));

      myDefaultProperties = mySVNConfigFile.getProperties(DefaultProxyGroup.DEFAULT_GROUP_NAME);
    }
  }

  public Map<String, ProxyGroup> getAllGroups() {
    final Map<String, ProxyGroup> result = new HashMap<>(myPatternsMap.size());
    for (Map.Entry<String, String> entry : myPatternsMap.entrySet()) {
      final String groupName = entry.getKey();
      result.put(groupName, new ProxyGroup(groupName, entry.getValue(), mySVNConfigFile.getProperties(groupName)));
    }
    return result;
  }

  public DefaultProxyGroup getDefaultGroup() {
    return new DefaultProxyGroup(myDefaultProperties);
  }

  public void setValue(final String groupName, final String propertyName, final String value) {
    mySVNConfigFile.setPropertyValue(groupName, propertyName, value, true);
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
    mySVNConfigFile.deleteGroup(name, false);
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
    if (!isDefault) {
      mySVNConfigFile.setPropertyValue(GROUPS_GROUP_NAME, name, patterns, false);
    }
    final Map<String, String> deletedPrepared = new HashMap<>(delete.size());
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
