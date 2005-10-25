/**
 * @author cdr
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class OrderEntryUtil {
  public static Collection<OrderEntry> getDependentOrderEntries(ModuleRootModel modifiableModel) {
    HashSet<Module> processedModules = new HashSet<Module>();
    processedModules.add(modifiableModel.getModule());
    return getDependentOrderEntries(modifiableModel,processedModules);
  }
  private static Collection<OrderEntry> getDependentOrderEntries(ModuleRootModel modifiableModel, Set<Module> processedModules) {
    final Set<OrderEntry> orderEntries = modifiableModel.processOrder(new CollectDependentOrderEntries(processedModules), new HashSet<OrderEntry>());
    return orderEntries;
  }

  private static class CollectDependentOrderEntries extends RootPolicy<Set<OrderEntry>> {
    private final Set<Module> myProcessedModules;

    public CollectDependentOrderEntries(Set<Module> processedModules) {
      myProcessedModules = processedModules;
    }

    public Set<OrderEntry> visitOrderEntry(OrderEntry orderEntry, Set<OrderEntry> orderEntries) {
      orderEntries.add(orderEntry);
      return orderEntries;
    }

    public Set<OrderEntry> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Set<OrderEntry> orderEntries) {
      final Module module = moduleOrderEntry.getModule();
      if (module != null && myProcessedModules.add(module)) {
        orderEntries.add(moduleOrderEntry);
        final ModuleRootModel modifiableModel = ModuleRootManager.getInstance(module);
        final Collection<OrderEntry> dependentOrderEntries = getDependentOrderEntries(modifiableModel,myProcessedModules);
        orderEntries.addAll(dependentOrderEntries);
      }
      return orderEntries;
    }
  }

  public static boolean equals(OrderEntry orderEntry1, OrderEntry orderEntry2) {
    if (orderEntry1 instanceof JdkOrderEntry && orderEntry2 instanceof JdkOrderEntry) {
      final JdkOrderEntry jdkOrderEntry1 = (JdkOrderEntry)orderEntry1;
      final JdkOrderEntry jdkOrderEntry2 = (JdkOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getJdk(), jdkOrderEntry2.getJdk()) && Comparing.strEqual(jdkOrderEntry1.getJdkName(), jdkOrderEntry2.getJdkName());
    }
    if (orderEntry1 instanceof LibraryOrderEntry && orderEntry2 instanceof LibraryOrderEntry) {
      final LibraryOrderEntry jdkOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      final LibraryOrderEntry jdkOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getLibrary(), jdkOrderEntry2.getLibrary());
    }
    if (orderEntry1 instanceof ModuleSourceOrderEntry && orderEntry2 instanceof ModuleSourceOrderEntry) {
      final ModuleSourceOrderEntry jdkOrderEntry1 = (ModuleSourceOrderEntry)orderEntry1;
      final ModuleSourceOrderEntry jdkOrderEntry2 = (ModuleSourceOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getOwnerModule(), jdkOrderEntry2.getOwnerModule());
    }
    if (orderEntry1 instanceof ModuleOrderEntry && orderEntry2 instanceof ModuleOrderEntry) {
      final ModuleOrderEntry jdkOrderEntry1 = (ModuleOrderEntry)orderEntry1;
      final ModuleOrderEntry jdkOrderEntry2 = (ModuleOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getModule(), jdkOrderEntry2.getModule());
    }
    return false;
  }
}