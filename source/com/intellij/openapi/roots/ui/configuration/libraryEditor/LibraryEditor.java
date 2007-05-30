package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
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

  public void addJarDirectory(String url, boolean recursive) {
    getModel().addJarDirectory(url, recursive);
  }

  public void addJarDirectory(VirtualFile file, boolean recursive) {
    getModel().addJarDirectory(file, recursive);
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

  public Library.ModifiableModel getModel() {
    if (myModel == null) {
      myModel = myLibrary.getModifiableModel();
    }
    return myModel;
  }

  public boolean hasChanges() {
    return myModel != null && myModel.isChanged();
  }
  
  public boolean isJarDirectory(String url) {
    if (myModel != null) {
      return myModel.isJarDirectory(url);
    }
    return myLibrary.isJarDirectory(url); 
  }
  
  public boolean allPathsValid(OrderRootType orderRootType) {
    if (myModel != null) {
      return ((LibraryEx.ModifiableModelEx)myModel).allPathsValid(orderRootType);
    }
    return ((LibraryEx)myLibrary).allPathsValid(orderRootType); 
  }

  public boolean isValid(final String url, final OrderRootType orderRootType) {
    if (myModel != null) {
      return myModel.isValid(url, orderRootType);
    }
    return myLibrary.isValid(url, orderRootType); 
  }
}
