/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration;

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
}