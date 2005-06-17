/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;

import java.util.Comparator;
import java.util.Map;
import java.util.Stack;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 3, 2004
 */
public class PluginDescriptorComparator implements Comparator<PluginDescriptor>{
  private final TObjectIntHashMap<PluginId> myIdToNumberMap = new TObjectIntHashMap<PluginId>();
  private int myAvailableNumber = 0;

  public PluginDescriptorComparator(PluginDescriptor[] descriptors) throws Exception{
    final Map<PluginId, PluginDescriptor> idToDescriptorMap = new HashMap<PluginId, PluginDescriptor>();
    for (final PluginDescriptor descriptor : descriptors) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }
    
    final Stack<PluginId> visited = new Stack<PluginId>();
    for (int idx = 0; idx < descriptors.length && myIdToNumberMap.size() != descriptors.length; idx++) {
      assignNumbers(descriptors[idx].getPluginId(), idToDescriptorMap, visited);
      visited.clear();
    }
  }

  private void assignNumbers(PluginId id, Map<PluginId,PluginDescriptor> idToDescriptorMap, Stack<PluginId> visited) throws Exception {
    visited.push(id);
    try {
      final PluginId[] parentIds = idToDescriptorMap.get(id).getDependentPluginIds();
      for (final PluginId parentId : parentIds) {
        if (visited.contains(parentId)) {
          throw new Exception("Plugins should not have cyclic dependencies:\n" + id + "->" + parentId + "->...->" + id);
        }
      }
      for (PluginId parentId1 : parentIds) {
        assignNumbers(parentId1, idToDescriptorMap, visited);
      }
      if (!myIdToNumberMap.contains(id)) {
        myIdToNumberMap.put(id, myAvailableNumber++);
      }
    }
    finally {
      visited.pop();
    }
  }

  public int compare(PluginDescriptor d1, PluginDescriptor d2) {
    return myIdToNumberMap.get(d1.getPluginId()) - myIdToNumberMap.get(d2.getPluginId());
  }
}
