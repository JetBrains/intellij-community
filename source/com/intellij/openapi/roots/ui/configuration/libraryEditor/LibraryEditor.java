package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;

public class LibraryEditor {
  private final Library myLibrary;
  private String myLibraryName = null;
  private Library.ModifiableModel myModel = null;

  public LibraryEditor(Library library) {
    myLibrary = library;
  }

  public String getName() {
    if (myLibraryName != null) {
      return myLibraryName;
    }
    return myLibrary.getName();
  }

  public String[] getUrls(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getUrls(rootType);
    }
    return myLibrary.getUrls(rootType);
  }

  public VirtualFile[] getFiles(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getFiles(rootType);
    }
    return myLibrary.getFiles(rootType);
  }

  public void setName(String name) {
    myLibraryName = name;
    getModel().setName(name);
  }

  public void addRoot(String url, OrderRootType rootType) {
    getModel().addRoot(url, rootType);
  }

  public void addRoot(VirtualFile file, OrderRootType rootType) {
    getModel().addRoot(file, rootType);
  }

  public void removeRoot(String url, OrderRootType rootType) {
    while (getModel().removeRoot(url, rootType)) ;
  }

  public void commit() {
    if (myModel != null) {
      myModel.commit();
      myModel = null;
      myLibraryName = null;
    }
  }

  private Library.ModifiableModel getModel() {
    if (myModel == null) {
      myModel = myLibrary.getModifiableModel();
    }
    return myModel;
  }

  public boolean hasChanges() {
    if (myModel != null) {
      return myModel.isChanged();
    }
    return false;
  }
}
