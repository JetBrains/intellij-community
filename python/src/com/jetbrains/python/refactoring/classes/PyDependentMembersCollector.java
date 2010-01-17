package com.jetbrains.python.refactoring.classes;

import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;

/**
 * @author Dennis.Ushakov
 */
public class PyDependentMembersCollector extends DependentMembersCollectorBase<PyElement,PyClass> {
  public PyDependentMembersCollector(PyClass clazz, PyClass superClass) {
    super(clazz, superClass);
  }

  @Override
  public void collect(PyElement member) {

  }
}
