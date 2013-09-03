package com.jetbrains.pyqt;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.*;

/**
 * User : ktisha
 */
public class PyQtTypeProvider extends PyTypeProviderBase {
  private static final String ourQtBoundSignal = "QtCore.pyqtBoundSignal";
  private static final String ourQt4Signal = "pyqtSignal";

  @Override
  public PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
    final PsiPolyVariantReference reference = referenceExpression.getReference();
    final PsiElement element = reference.resolve();
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);
      if (type instanceof PyClassType) {
        final String classQName = ((PyClassType)type).getClassQName();
        if (classQName != null && classQName.startsWith("PyQt")) {
          final PyQualifiedName name = PyQualifiedName.fromDottedString(classQName);
          final String qtVersion = name.getComponents().get(0);
          if (ourQt4Signal.equals(name.getLastComponent())) {
            final PyClass aClass = PyClassNameIndex.findClass(qtVersion + "." + ourQtBoundSignal, referenceExpression.getProject());
            if (aClass != null)
              return new PyClassTypeImpl(aClass, false);
          }
        }
      }
      else if (type instanceof PyFunctionType) {
        final Callable callable = ((PyFunctionType)type).getCallable();
        if (callable instanceof PyFunction) {
          final String qualifiedName = callable.getQualifiedName();
          if (qualifiedName != null && qualifiedName.startsWith("PyQt")){
            final PyQualifiedName name = PyQualifiedName.fromDottedString(qualifiedName);
            final String qtVersion = name.getComponents().get(0);
            final String docstring = ((PyFunction)callable).getDocStringValue();
            if (docstring != null && docstring.contains("[signal]")) {
              final PyClass aClass = PyClassNameIndex.findClass(qtVersion + "." + ourQtBoundSignal, referenceExpression.getProject());
              if (aClass != null)
                  return new PyClassTypeImpl(aClass, false);
            }
          }
        }
      }
    }
    return null;
  }
}
