package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;

import javax.swing.*;
import java.io.File;
import java.awt.*;

class LibraryElementDescriptor extends NodeDescriptor<LibraryElement> {
  private final LibraryElement myElement;
  private final LibraryTableEditor myParentEditor;

  public LibraryElementDescriptor(NodeDescriptor parentDescriptor, LibraryElement element, LibraryTableEditor parentEditor) {
    super(null, parentDescriptor);
    myElement = element;
    myParentEditor = parentEditor;
  }

  public boolean update() {
    final Library library = myElement.getLibrary();
    final String name;
    final Icon icon;
    if (myElement.isAnonymous()) {
      final VirtualFile[] files = myParentEditor.getLibraryEditor(library).getFiles(OrderRootType.CLASSES);
      if (files.length > 0) {
        name = files[0].getPresentableUrl();
        icon = LibraryTableEditor.getIconForUrl(files[0].getUrl(), true);
      }
      else {
        final String[] urls = myParentEditor.getLibraryEditor(library).getUrls(OrderRootType.CLASSES);
        if (urls.length > 0) {
          final String url = urls[0];
          name = LibraryTableEditor.getPresentablePath(url).replace('/', File.separatorChar);
          icon = LibraryTableEditor.getIconForUrl(url, false);
        }
        else {
          name = "<empty library>"; // the library is anonymous, library.getName() == null
          icon = Icons.LIBRARY_ICON;
        }
      }
    }
    else {
      name = myParentEditor.getLibraryEditor(library).getName();
      icon = Icons.LIBRARY_ICON;
    }
    myColor = myElement.hasInvalidPaths()? Color.RED : null;
    final boolean changed = !name.equals(myName) || !icon.equals(myClosedIcon);
    if (changed) {
      myName = name;
      myClosedIcon = myOpenIcon = icon;
    }
    return changed;
  }

  public LibraryElement getElement() {
    return myElement;
  }
}

