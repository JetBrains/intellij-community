package com.jetbrains.python.refactoring.classes;

import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * @author Dennis.Ushakov
 */
public class PyDependentMembersCollector extends DependentMembersCollectorBase<PyElement,PyClass> {
  public PyDependentMembersCollector(PyClass clazz, PyClass superClass) {
    super(clazz, superClass);
  }

  @Override
  public void collect(PyElement member) {
    final PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        final Callable markedFunction = node.resolveCalleeFunction(PyResolveContext.noImplicits());
        final PyFunction function = markedFunction != null ? markedFunction.asMethod() : null;
        if (!existsInSuperClass(function)) {
          myCollection.add(function);
        }
      }
    };
    member.accept(visitor);
  }

  private boolean existsInSuperClass(PyFunction classMember) {
    if (getSuperClass() == null) return false;
    final String name = classMember != null ? classMember.getName() : null;
    if (name == null) return false;
    final PyFunction methodBySignature = (getSuperClass()).findMethodByName(name, true);
    return methodBySignature != null;
  }
}
