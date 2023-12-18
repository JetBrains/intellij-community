// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.facet;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;


public class PythonSdkTableListener implements ProjectJdkTable.Listener {

  @Override
  public void jdkAdded(@NotNull final Sdk sdk) {
    if (sdk.getSdkType() instanceof PythonSdkType) {
      ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
        final ModifiableModelsProvider provider = ModifiableModelsProvider.getInstance();
        final LibraryTable.ModifiableModel libraryTableModifiableModel = provider.getLibraryTableModifiableModel();
        Library library = libraryTableModifiableModel.getLibraryByName(PythonFacetUtil.getFacetLibraryName(sdk.getName()));
        provider.disposeLibraryTableModifiableModel(libraryTableModifiableModel);
        if (library == null) {
          addLibrary(sdk);
        }
        else {
          // Library might already be created before listener invocation, e.g. by PythonFacetUtil.updateLibrary
          updateLibrary(sdk);
        }
      }));
    }
  }

  @Override
  public void jdkRemoved(@NotNull final Sdk sdk) {
    if (sdk.getSdkType() instanceof PythonSdkType) {
      removeLibrary(sdk);
    }
  }

  @Override
  public void jdkNameChanged(@NotNull final Sdk sdk, @NotNull final String previousName) {
    if (sdk.getSdkType() instanceof PythonSdkType) {
      renameLibrary(sdk, previousName);
    }
  }

  static Library addLibrary(Sdk sdk) {
    final LibraryTable.ModifiableModel libraryTableModel = ModifiableModelsProvider.getInstance().getLibraryTableModifiableModel();
    final Library library = libraryTableModel.createLibrary(PythonFacetUtil.getFacetLibraryName(sdk.getName()));
    final Library.ModifiableModel model = library.getModifiableModel();
    for (String url : sdk.getRootProvider().getUrls(OrderRootType.CLASSES)) {
      model.addRoot(url, OrderRootType.CLASSES);
      model.addRoot(url, OrderRootType.SOURCES);
    }
    model.commit();
    libraryTableModel.commit();
    return library;
  }

  static void updateLibrary(Sdk sdk) {
    final LibraryTable.ModifiableModel libraryTableModel = ModifiableModelsProvider.getInstance().getLibraryTableModifiableModel();
    final Library library = libraryTableModel.getLibraryByName(PythonFacetUtil.getFacetLibraryName(sdk.getName()));
    if (library == null) return;
    final Library.ModifiableModel model = library.getModifiableModel();
    for (String url : library.getRootProvider().getUrls(OrderRootType.CLASSES)) {
      model.removeRoot(url, OrderRootType.CLASSES);
    }
    for (String url : library.getRootProvider().getUrls(OrderRootType.SOURCES)) {
      model.removeRoot(url, OrderRootType.SOURCES);
    }
    for (String url : sdk.getRootProvider().getUrls(OrderRootType.CLASSES)) {
      model.addRoot(url, OrderRootType.CLASSES);
      model.addRoot(url, OrderRootType.SOURCES);
    }
    model.commit();
    libraryTableModel.commit();
  }

  private static void removeLibrary(final Sdk sdk) {
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      final LibraryTable.ModifiableModel libraryTableModel =
        ModifiableModelsProvider.getInstance().getLibraryTableModifiableModel();
      final Library library = libraryTableModel.getLibraryByName(PythonFacetUtil.getFacetLibraryName(sdk.getName()));
      if (library != null) {
        libraryTableModel.removeLibrary(library);
      }
      libraryTableModel.commit();
    }), ModalityState.nonModal());
  }

  private static void renameLibrary(final Sdk sdk, final String previousName) {
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      final LibraryTable.ModifiableModel libraryTableModel =
        ModifiableModelsProvider.getInstance().getLibraryTableModifiableModel();
      final Library library = libraryTableModel.getLibraryByName(PythonFacetUtil.getFacetLibraryName(previousName));
      if (library != null) {
        final Library.ModifiableModel model = library.getModifiableModel();
        model.setName(PythonFacetUtil.getFacetLibraryName(sdk.getName()));
        model.commit();
      }
      libraryTableModel.commit();
    }), ModalityState.nonModal());
  }
}
