/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.plugins;

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
  private final TObjectIntHashMap myIdToNumberMap = new TObjectIntHashMap();
  private int myAvailableNumber = 0;

  public PluginDescriptorComparator(PluginDescriptor[] descriptors) throws Exception{
    final Map<String, PluginDescriptor> idToDescriptorMap = new HashMap<String, PluginDescriptor>();
    for (int idx = 0; idx < descriptors.length; idx++) {
      final PluginDescriptor descriptor = descriptors[idx];
      idToDescriptorMap.put(descriptor.getId(), descriptor);
    }
    final Stack<String> visited = new Stack<String>();
    for (int idx = 0; idx < descriptors.length && myIdToNumberMap.size() != descriptors.length; idx++) {
      assignNumbers(descriptors[idx].getId(), idToDescriptorMap, visited);
      visited.clear();
    }
  }

  private void assignNumbers(String id, Map<String, PluginDescriptor> idToDescriptorMap, Stack<String> visited) throws Exception {
    visited.push(id);
    try {
      final String[] parentIds = idToDescriptorMap.get(id).getDependentPluginIds();
      for (int idx = 0; idx < parentIds.length; idx++) {
        final String parentId = parentIds[idx];
        if (visited.contains(parentId)) {
          throw new Exception("Plugins should not have cyclic dependencies:\n" + id + "->" + parentId + "->...->" + id );
        }
      }
      for (int idx = 0; idx < parentIds.length; idx++) {
        assignNumbers(parentIds[idx], idToDescriptorMap, visited);
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
    return myIdToNumberMap.get(d1.getId()) - myIdToNumberMap.get(d2.getId());
  }
}
