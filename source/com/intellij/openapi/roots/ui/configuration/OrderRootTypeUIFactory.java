/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryElement;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableTreeContentElement;
import com.intellij.openapi.util.KeyedExtensionFactory;

public interface OrderRootTypeUIFactory {
  KeyedExtensionFactory<OrderRootTypeUIFactory, OrderRootType> FACTORY = new KeyedExtensionFactory<OrderRootTypeUIFactory, OrderRootType>(OrderRootTypeUIFactory.class, "com.intellij.OrderRootTypeUI") {
    public String getKey(final OrderRootType key) {
      return key.name();
    }
  };

  LibraryTableTreeContentElement createElement(final LibraryElement parentElement);
  PathEditor createPathEditor();

  class MyPathsEditor extends PathEditor {
    private boolean myShowUrl;
    private OrderRootType myOrderRootType;
    private FileChooserDescriptor myDescriptor;
    private String myDisplayName;

    public MyPathsEditor(final String displayName, final OrderRootType orderRootType, final FileChooserDescriptor descriptor, final boolean showUrl) {
      myShowUrl = showUrl;
      myOrderRootType = orderRootType;
      myDescriptor = descriptor;
      myDisplayName = displayName;
    }

    protected boolean isShowUrlButton() {
      return myShowUrl;
    }

    protected OrderRootType getRootType() {
      return myOrderRootType;
    }

    protected FileChooserDescriptor createFileChooserDescriptor() {
      return myDescriptor;
    }

    public String getDisplayName() {
      return myDisplayName;
    }
  }
}