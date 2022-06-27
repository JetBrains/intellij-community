// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.facet;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.library.PythonLibraryType;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public final class FacetLibraryConfigurator {
  private FacetLibraryConfigurator() {
  }

  public static void attachPythonLibrary(final Module module,
                                         @Nullable final ModifiableRootModel existingModel,
                                         final String libraryName,
                                         final List<String> paths) {
    final ModifiableModelsProvider modelsProvider = ModifiableModelsProvider.getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        // add all paths to library
        final ModifiableRootModel model = existingModel != null ? existingModel : modelsProvider.getModuleModifiableModel(module);
        final LibraryOrderEntry orderEntry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
        if (orderEntry != null) {
          // update existing
          Library lib = orderEntry.getLibrary();
          if (lib != null) {
            if (lib instanceof LibraryEx && ((LibraryEx)lib).getKind() == null) {   // replace old python libraries with python specific ones
              model.removeOrderEntry(orderEntry);
              LibraryTablesRegistrar.getInstance().getLibraryTable(model.getProject()).removeLibrary(lib);
              createNewLibrary(model);
              return;
            }
            fillLibrary(lib, paths);
            if (existingModel == null) {
              modelsProvider.commitModuleModifiableModel(model);
            }
            return;
          }
        }
        // create new
        createNewLibrary(model);
      }

      private void createNewLibrary(ModifiableRootModel model) {
        final LibraryTable.ModifiableModel projectLibrariesModel = modelsProvider.getLibraryTableModifiableModel(model.getProject());
        Library lib = projectLibrariesModel.createLibrary(libraryName, PythonLibraryType.getInstance().getKind());
        fillLibrary(lib, paths);
        projectLibrariesModel.commit();
        model.addLibraryEntry(lib);
        if (existingModel == null) {
          modelsProvider.commitModuleModifiableModel(model);
        }
      }
    });
  }

  private static void fillLibrary(Library lib, List<String> paths) {
    Library.ModifiableModel modifiableModel = lib.getModifiableModel();
    for (String root : lib.getUrls(OrderRootType.CLASSES)) {
      modifiableModel.removeRoot(root, OrderRootType.CLASSES);
    }
    if (paths != null) {
      for (String dir : paths) {
        VirtualFile pathEntry = LocalFileSystem.getInstance().findFileByPath(dir);
        if (pathEntry != null && !pathEntry.isDirectory() && pathEntry.getFileType() instanceof ArchiveFileType) {
          pathEntry = JarFileSystem.getInstance().getJarRootForLocalFile(pathEntry);
        }
        if (pathEntry != null) {
          modifiableModel.addRoot(pathEntry, OrderRootType.CLASSES);
        }
        else {
          modifiableModel.addRoot("file://"+dir, OrderRootType.CLASSES);
        }
      }
    }
    modifiableModel.commit();
  }

  public static void detachPythonLibrary(final Module module, final String libraryName) {
    final ModifiableModelsProvider modelsProvider = ModifiableModelsProvider.getInstance();
    ApplicationManager.getApplication().runWriteAction(() -> {
      // remove the library
      final ModifiableRootModel model = modelsProvider.getModuleModifiableModel(module);
      OrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
      if (entry == null) {
        modelsProvider.disposeModuleModifiableModel(model);
      }
      else {
        model.removeOrderEntry(entry);
        modelsProvider.commitModuleModifiableModel(model);
      }
    });
  }
}
