/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */

public class LibrariesModifiableModel implements LibraryTable.ModifiableModel {
  private Map<Library, LibraryEditor> myLibrary2EditorMap = new HashMap<Library, LibraryEditor>();
  private Set<Library> myRemovedLibraries = new HashSet<Library>();

  private LibraryTable.ModifiableModel myLibrariesModifiableModel;

  public LibrariesModifiableModel(final LibraryTable table) {
    myLibrariesModifiableModel = table.getModifiableModel();
  }

  public Library createLibrary(String name) {
    final Library library = myLibrariesModifiableModel.createLibrary(name);
    createLibraryEditor(library);
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
    LibraryEditor libraryEditor = myLibrary2EditorMap.get(library);
    if (libraryEditor == null){
      libraryEditor = createLibraryEditor(library);
    }
    return libraryEditor;
  }

  private LibraryEditor createLibraryEditor(final Library library) {
    final LibraryEditor libraryEditor = new LibraryEditor(library);
    myLibrary2EditorMap.put(library, libraryEditor);
    myLibrary2EditorMap.put((Library)libraryEditor.getModel(), libraryEditor);
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

}
