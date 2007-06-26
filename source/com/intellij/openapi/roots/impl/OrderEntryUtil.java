/**
 * @author cdr
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class OrderEntryUtil {
  public static Collection<OrderEntry> getDependentOrderEntries(ModuleRootModel rootModel) {
    HashSet<Module> processedModules = new HashSet<Module>();
    processedModules.add(rootModel.getModule());
    return getDependentOrderEntries(rootModel,processedModules);
  }
  private static Collection<OrderEntry> getDependentOrderEntries(ModuleRootModel rootModel, Set<Module> processedModules) {
    return rootModel.processOrder(new CollectDependentOrderEntries(processedModules), new HashSet<OrderEntry>());
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

  public static boolean equals(Library library1, Library library2) {
    if (library1 == library2) return true;
    if (library1 == null || library2 == null) return false;

    final LibraryTable table = library1.getTable();
    if (table != null) {
      if (library2.getTable() != table) return false;
      final String name = library1.getName();
      return name != null && name.equals(library2.getName());
    }

    if (library2.getTable() != null) return false;

    for (OrderRootType type : OrderRootType.ALL_TYPES) {
      if (!Comparing.equal(library1.getUrls(type), library2.getUrls(type))) {
        return false;
      }
    }
    return true;
  }

  public static void addLibraryToRoots(final LibraryOrderEntry libraryOrderEntry, final Module module) {
    Library library = libraryOrderEntry.getLibrary();
    if (library == null) return;
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = manager.getModifiableModel();

    if (libraryOrderEntry.isModuleLevel()) {
      final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
      final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
      VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      for (VirtualFile jarFile : files) {
        libraryModel.addRoot(jarFile, OrderRootType.CLASSES);
      }
      final VirtualFile[] sources = library.getFiles(OrderRootType.SOURCES);
      for (VirtualFile source : sources) {
        libraryModel.addRoot(source, OrderRootType.SOURCES);
      }
      final VirtualFile[] javadocs = library.getFiles(OrderRootType.JAVADOC);
      for (VirtualFile javadoc : javadocs) {
        libraryModel.addRoot(javadoc, OrderRootType.JAVADOC);
      }
      final VirtualFile[] annotations = library.getFiles(OrderRootType.ANNOTATIONS);
      for (VirtualFile annotation : annotations) {
        libraryModel.addRoot(annotation, OrderRootType.ANNOTATIONS);
      }
      libraryModel.commit();
    }
    else {
      rootModel.addLibraryEntry(library);
    }
    rootModel.commit();
  }
}