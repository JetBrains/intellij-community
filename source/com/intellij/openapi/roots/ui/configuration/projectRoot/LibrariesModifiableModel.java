/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */

public class LibrariesModifiableModel implements LibraryTable.ModifiableModel {
  private final Map<Library, LibraryEditor> myLibrary2EditorMap = new HashMap<Library, LibraryEditor>();
  private final Set<Library> myRemovedLibraries = new HashSet<Library>();

  private final LibraryTable.ModifiableModel myLibrariesModifiableModel;
  private final Project myProject;

  public LibrariesModifiableModel(final LibraryTable table, final Project project) {
    myProject = project;
    myLibrariesModifiableModel = table.getModifiableModel();
  }

  public Library createLibrary(String name) {
    final Library library = myLibrariesModifiableModel.createLibrary(name);
    //createLibraryEditor(library);
    final BaseLibrariesConfigurable configurable = ProjectStructureConfigurable.getInstance(myProject).getConfigurableFor(library);
    configurable.createLibraryNode(library);
    return library;
  }

  public void removeLibrary(@NotNull Library library) {
    if (myLibrariesModifiableModel.getLibraryByName(library.getName()) == null) return;

    myRemovedLibraries.add(library);
    removeLibraryEditor(library);
    myLibrariesModifiableModel.removeLibrary(library);
  }

  public void commit() {
    //do nothing  - do deffered commit
  }

  @NotNull
  public Iterator<Library> getLibraryIterator() {
    return myLibrariesModifiableModel.getLibraryIterator();
  }

  public Library getLibraryByName(@NotNull String name) {
    return myLibrariesModifiableModel.getLibraryByName(name);
  }

  @NotNull
  public Library[] getLibraries() {
    return myLibrariesModifiableModel.getLibraries();
  }

  public boolean isChanged() {
    for (LibraryEditor libraryEditor : myLibrary2EditorMap.values()) {
      if (libraryEditor.hasChanges()) return true;
    }
    return myLibrariesModifiableModel.isChanged();
  }

  public void deferredCommit(){
    for (LibraryEditor libraryEditor : new ArrayList<LibraryEditor>(myLibrary2EditorMap.values())) {
      libraryEditor.commit();
    }
    if (!(myLibrary2EditorMap.isEmpty() && myRemovedLibraries.isEmpty())) {
      myLibrariesModifiableModel.commit();
    }
    myLibrary2EditorMap.clear();
    myRemovedLibraries.clear();
  }

  public boolean wasLibraryRemoved(Library library){
    return myRemovedLibraries.contains(library);
  }

  public boolean hasLibraryEditor(Library library){
    return myLibrary2EditorMap.containsKey(library);
  }

  public LibraryEditor getLibraryEditor(Library library){
    final Library source = ((LibraryImpl)library).getSource();
    if (source != null) {
      return getLibraryEditor(source);
    }
    LibraryEditor libraryEditor = myLibrary2EditorMap.get(library);
    if (libraryEditor == null){
      libraryEditor = createLibraryEditor(library);
    }
    return libraryEditor;
  }

  private LibraryEditor createLibraryEditor(final Library library) {
    final LibraryEditor libraryEditor = new LibraryEditor(library);
    myLibrary2EditorMap.put(library, libraryEditor);
    return libraryEditor;
  }

  private void removeLibraryEditor(final Library library) {
    final LibraryEditor libraryEditor = myLibrary2EditorMap.remove(library);
    if (libraryEditor != null) {
      for (Iterator it = myLibrary2EditorMap.keySet().iterator(); it.hasNext();) {
        final Library lib = (Library)it.next();
        if (libraryEditor == myLibrary2EditorMap.get(lib)) {
          it.remove();
        }
      }
    }
  }

  public Library.ModifiableModel getLibraryModifiableModel(final Library library) {
    return getLibraryEditor(library).getModel();
  }
}
