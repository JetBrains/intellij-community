/**
 * @author cdr
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class OrderEntryUtil {
  private OrderEntryUtil() {
  }

  @Nullable
  public static LibraryOrderEntry findLibraryOrderEntry(@NotNull ModuleRootModel model, @Nullable Library library) {
    return findLibraryOrderEntry(model, library, false, null);
  }

  @Nullable
  public static LibraryOrderEntry findLibraryOrderEntry(@NotNull ModuleRootModel model, @Nullable Library library,
                                                        boolean searchInDependencies, final ModulesProvider modulesProvider) {
    if (library == null) return null;
    for (OrderEntry orderEntry : getOrderEntries(model, searchInDependencies, modulesProvider)) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }

    return null;
  }

  private static Collection<? extends OrderEntry> getOrderEntries(final ModuleRootModel model, final boolean includeDependent, @Nullable ModulesProvider modulesProvider) {
    if (includeDependent) {
      return modulesProvider != null ? getDependentOrderEntries(model, modulesProvider) : getDependentOrderEntries(model);
    }
    else {
      return Arrays.asList(model.getOrderEntries());
    }
  }

  @Nullable
  public static ModuleOrderEntry findModuleOrderEntry(@NotNull ModuleRootModel model, @Nullable Module module) {
    return findModuleOrderEntry(model, module, false, null);
  }

  @Nullable
  public static ModuleOrderEntry findModuleOrderEntry(@NotNull ModuleRootModel model, @Nullable Module module, boolean searchInDependencies,
                                                      final ModulesProvider modulesProvider) {
    if (module == null) return null;

    for (OrderEntry orderEntry : getOrderEntries(model, searchInDependencies, modulesProvider)) {
      if (orderEntry instanceof ModuleOrderEntry && module.equals(((ModuleOrderEntry)orderEntry).getModule())) {
        return (ModuleOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Nullable
  public static JdkOrderEntry findJdkOrderEntry(@NotNull ModuleRootModel model, @Nullable Sdk sdk) {
    if (sdk == null) return null;

    for (OrderEntry orderEntry : getOrderEntries(model, false, null)) {
      if (orderEntry instanceof JdkOrderEntry && sdk.equals(((JdkOrderEntry)orderEntry).getJdk())) {
        return (JdkOrderEntry)orderEntry;
      }
    }
    return null;
  }


  public static Collection<OrderEntry> getDependentOrderEntries(@NotNull ModuleRootModel rootModel) {
    return getDependentOrderEntries(rootModel, new DefaultModulesProvider(rootModel.getModule().getProject()));
  }

  public static Collection<OrderEntry> getDependentOrderEntries(ModuleRootModel rootModel, ModulesProvider modulesProvider) {
    HashSet<Module> processedModules = new HashSet<Module>();
    processedModules.add(rootModel.getModule());
    return getDependentOrderEntries(rootModel,processedModules, modulesProvider);
  }

  private static Collection<OrderEntry> getDependentOrderEntries(ModuleRootModel rootModel, Set<Module> processedModules,
                                                                 ModulesProvider modulesProvider) {
    return rootModel.processOrder(new CollectDependentOrderEntries(processedModules, modulesProvider), new LinkedHashSet<OrderEntry>());
  }

  private static class CollectDependentOrderEntries extends RootPolicy<Set<OrderEntry>> {
    private final Set<Module> myProcessedModules;
    private ModulesProvider myModulesProvider;

    public CollectDependentOrderEntries(Set<Module> processedModules, ModulesProvider modulesProvider) {
      myProcessedModules = processedModules;
      myModulesProvider = modulesProvider;
    }

    public Set<OrderEntry> visitOrderEntry(OrderEntry orderEntry, Set<OrderEntry> orderEntries) {
      orderEntries.add(orderEntry);
      return orderEntries;
    }

    public Set<OrderEntry> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Set<OrderEntry> orderEntries) {
      final Module module = moduleOrderEntry.getModule();
      if (module != null && myProcessedModules.add(module)) {
        orderEntries.add(moduleOrderEntry);
        final ModuleRootModel modifiableModel = myModulesProvider.getRootModel(module);
        final Collection<OrderEntry> dependentOrderEntries = getDependentOrderEntries(modifiableModel,myProcessedModules, myModulesProvider);
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

    for (OrderRootType type : OrderRootType.getAllTypes()) {
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
      for(OrderRootType orderRootType: OrderRootType.getAllTypes()) {
        VirtualFile[] files = library.getFiles(orderRootType);
        for (VirtualFile jarFile : files) {
          libraryModel.addRoot(jarFile, orderRootType);
        }
      }
      libraryModel.commit();
    }
    else {
      rootModel.addLibraryEntry(library);
    }
    rootModel.commit();
  }
}