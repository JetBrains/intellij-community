// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.facet;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.jetbrains.python.facet.PythonFacetSettings;

import static com.jetbrains.python.facet.LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX;

public final class PythonFacetUtil {
  public static String getFacetLibraryName(final String sdkName) {
    return sdkName + PYTHON_FACET_LIBRARY_NAME_SUFFIX;
  }

  public static void updateLibrary(Module module, PythonFacetSettings facetSettings) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final ModifiableRootModel model = rootManager.getModifiableModel();
      boolean modelChanged = false;
      try {
        // Just remove all old facet libraries except one, that is necessary
        final Sdk sdk = facetSettings.getSdk();
        final String name = (sdk != null) ? getFacetLibraryName(sdk.getName()) : null;
        boolean librarySeen = false;
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            final String libraryName = ((LibraryOrderEntry)entry).getLibraryName();
            if (name != null && name.equals(libraryName)) {
              librarySeen = true;
              continue;
            }
            if (libraryName != null && libraryName.endsWith(PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
              model.removeOrderEntry(entry);
              modelChanged = true;
            }
          }
        }
        if (name != null) {
          final ModifiableModelsProvider provider = ModifiableModelsProvider.getInstance();
          final LibraryTable.ModifiableModel libraryTableModifiableModel = provider.getLibraryTableModifiableModel();
          Library library = libraryTableModifiableModel.getLibraryByName(name);
          provider.disposeLibraryTableModifiableModel(libraryTableModifiableModel);
          if (library == null) {
            // we just create new project library
            library = PythonSdkTableListener.addLibrary(sdk);
          }
          else {
            PythonSdkTableListener.updateLibrary(sdk);
          }
          if (!librarySeen) {
            model.addLibraryEntry(library);
            modelChanged = true;
          }
        }
      }
      finally {
        if (modelChanged) {
          model.commit();
        }
        else {
          model.dispose();
        }
      }
    });
  }

  public static void removeLibrary(Module module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final ModifiableRootModel model = rootManager.getModifiableModel();
      // Just remove all old facet libraries
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            final String libraryName = library.getName();
            if (libraryName!=null && libraryName.endsWith(PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
              model.removeOrderEntry(entry);
              //PyBuiltinCache.clearInstanceCache();
            }
          }
        }
      }
      model.commit();
    });
  }
}
