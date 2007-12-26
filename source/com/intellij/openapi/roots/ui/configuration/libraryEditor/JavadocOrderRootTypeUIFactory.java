/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;

public class JavadocOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  public LibraryTableTreeContentElement createElement(final LibraryElement parentElement) {
    return new JavadocElement(parentElement);
  }

  public PathEditor createPathEditor() {
    return new MyPathsEditor(ProjectBundle.message("sdk.configure.javadoc.tab"), JavadocOrderRootType.INSTANCE, new FileChooserDescriptor(false, true, true, false, true, true), true);
  }
}