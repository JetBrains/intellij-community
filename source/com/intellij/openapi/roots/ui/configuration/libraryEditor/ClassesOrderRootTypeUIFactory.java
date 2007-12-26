/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;

public class ClassesOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  public LibraryTableTreeContentElement createElement(final LibraryElement parentElement) {
    return new ClassesElement(parentElement);
  }
}