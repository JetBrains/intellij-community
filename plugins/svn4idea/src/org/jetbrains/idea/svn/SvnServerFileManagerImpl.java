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

import org.jetbrains.idea.svn.config.DefaultProxyGroup;
import org.jetbrains.idea.svn.config.ProxyGroup;

import java.util.*;

public class SvnServerFileManagerImpl implements SvnServerFileManager {
  private final DefaultProxyGroup myDefaultGroup;
  private final Map<String, ProxyGroup> myGroups;
  private final IdeaSVNConfigFile myFile;

  public SvnServerFileManagerImpl(final IdeaSVNConfigFile file) {
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

  @Override
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
