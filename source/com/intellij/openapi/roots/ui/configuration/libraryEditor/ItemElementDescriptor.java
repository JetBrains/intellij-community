package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;

import java.awt.*;
import java.io.File;

class ItemElementDescriptor extends NodeDescriptor<ItemElement> {
    private final ItemElement myElement;

    public ItemElementDescriptor(NodeDescriptor parentDescriptor, ItemElement element) {
      super(null, parentDescriptor);
      myElement = element;
      final String url = element.getUrl();
      myName = LibraryTableEditor.getPresentablePath(url).replace('/', File.separatorChar);
      myOpenIcon = myClosedIcon = LibraryTableEditor.getIconForUrl(url, element.isValid(), element.isJarDirectory());
    }

    public boolean update() {
      Color color = myElement.isValid()? Color.BLACK : Color.RED;
      final boolean changes = !color.equals(myColor);
      myColor = color;
      return changes;
    }

    public ItemElement getElement() {
      return myElement;
    }
  }
