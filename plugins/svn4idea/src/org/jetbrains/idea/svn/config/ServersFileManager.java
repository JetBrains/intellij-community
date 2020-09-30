// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import java.util.*;

public class ServersFileManager {
  private final DefaultProxyGroup myDefaultGroup;
  private final Map<String, ProxyGroup> myGroups;
  private final SvnIniFile myFile;

  public ServersFileManager(final SvnIniFile file) {
    myFile = file;
    myFile.updateGroups();

    myGroups = new HashMap<>();
    myGroups.putAll(file.getAllGroups());
    myDefaultGroup = file.getDefaultGroup();
  }

  public DefaultProxyGroup getDefaultGroup() {
    return (DefaultProxyGroup) myDefaultGroup.copy();
  }

  public Map<String, ProxyGroup> getGroups() {
    // return deep copy
    final Map<String, ProxyGroup> result = new HashMap<>(myGroups);
    for (Map.Entry<String, ProxyGroup> entry : myGroups.entrySet()) {
      result.put(entry.getKey(), entry.getValue().copy());
    }
    return result;
  }

  public void updateUserServerFile(final Collection<ProxyGroup> newUserGroups) {
    final Map<String, ProxyGroup> oldGroups = getGroups();

    for (ProxyGroup proxyGroup : newUserGroups) {
      if (proxyGroup.isDefault()) {
        processGroup(proxyGroup, getDefaultGroup(), false);
      } else {
        findAndProcessGroup(proxyGroup, oldGroups);
      }
    }

    for (String groupName : oldGroups.keySet()) {
      myFile.deleteGroup(groupName);
    }

    myFile.save();
  }

  public void updateFromFile() {
    myFile.updateGroups();
  }

  private void processGroup(final ProxyGroup newGroup, final ProxyGroup oldGroup, final boolean groupWasAdded) {
    final String newGroupName = newGroup.getName();
    if (groupWasAdded) {
      myFile.addGroup(newGroupName, newGroup.getPatterns(), newGroup.getProperties());
    } else {
      final Map<String, String> oldProperties = oldGroup.getProperties();
      final Map<String, String> newProperties = newGroup.getProperties();

      final Set<String> deletedProperties = new HashSet<>();
      for (String oldKey : oldProperties.keySet()) {
        if (! newProperties.containsKey(oldKey)) {
          deletedProperties.add(oldKey);
        }
      }

      final Map<String, String> newOrModifiedProperties = new HashMap<>();
      for (Map.Entry<String, String> entry : newProperties.entrySet()) {
        final String oldValue = oldProperties.get(entry.getKey());
        if ((oldValue == null) || (! oldValue.equals(entry.getValue()))) {
          newOrModifiedProperties.put(entry.getKey(), entry.getValue());
        }
      }

      myFile.modifyGroup(newGroupName, newGroup.getPatterns(), deletedProperties, newOrModifiedProperties, newGroup.isDefault());
    }
  }

  private void findAndProcessGroup(final ProxyGroup newGroup, final Map<String, ProxyGroup> oldGroups) {
    final String newGroupName = newGroup.getName();
    final ProxyGroup oldGroup = oldGroups.get(newGroupName);
    final boolean groupWasAdded = (oldGroup == null) && (! newGroup.isDefault());
    if (! groupWasAdded) {
      // to track deleted
      oldGroups.remove(newGroupName);
    }
    processGroup(newGroup, oldGroup, groupWasAdded);
  }
}
