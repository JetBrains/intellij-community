package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Recursive visitor with multimap, to be used for {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager#getDependencies(com.jetbrains.python.psi.PyElement)}
 */
class PyRecursiveElementVisitorWithResult extends PyRecursiveElementVisitor {
  @NotNull
  protected final MultiMap<PyClass, PyElement> myResult;

  PyRecursiveElementVisitorWithResult() {
    myResult = new MultiMap<PyClass, PyElement>();
  }
}
