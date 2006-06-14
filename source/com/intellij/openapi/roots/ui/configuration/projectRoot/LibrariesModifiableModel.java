/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;

import java.util.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */

public class LibrariesModifiableModel implements LibraryTable.ModifiableModel {
  private Map<Library, LibraryEditor> myLibrary2EditorMap = new HashMap<Library, LibraryEditor>();
  private Set<Library> myRemovedLibraries = new HashSet<Library>();

  private LibraryTable.ModifiableModel myLibrariesModifiableModel;

  public LibrariesModifiableModel(final LibraryTable.ModifiableModel librariesModifiableModel) {
    myLibrariesModifiableModel = librariesModifiableModel;
  }

  public Library createLibrary(String name) {
    final Library library = myLibrariesModifiableModel.createLibrary(name);
    myLibrary2EditorMap.put(library, new LibraryEditor(library));
    return library;
  }

  public void removeLibrary(Library library) {
    myRemovedLibraries.add(library);
    myLibrary2EditorMap.remove(library);
    myLibrariesModifiableModel.removeLibrary(library);
  }

  public void commit() {
    //do nothing  - do deffered commit
  }

  public Iterator getLibraryIterator() {
    return myLibrariesModifiableModel.getLibraryIterator();
  }

  public Library getLibraryByName(String name) {
    return myLibrariesModifiableModel.getLibraryByName(name);
  }

  public Library[] getLibraries() {
    return myLibrariesModifiableModel.getLibraries();
  }

  public boolean isChanged() {
    for (LibraryEditor libraryEditor : myLibrary2EditorMap.values()) {
      if (libraryEditor.hasChanges()) return true;
    }
    final boolean changed = myLibrariesModifiableModel.isChanged();
    if (changed) return true;
    return false;
  }

  public void deferredCommit(){
    for (LibraryEditor libraryEditor : myLibrary2EditorMap.values()) {
      libraryEditor.commit();
    }
    myLibrariesModifiableModel.commit();
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
      libraryEditor = new LibraryEditor(library);
      myLibrary2EditorMap.put(library, libraryEditor);
    }
    return libraryEditor;
  }

}
