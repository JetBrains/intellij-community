package com.jetbrains.python.psi.impl;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyQualifiedNameFactory {
  @Nullable
  public static QualifiedName fromReferenceChain(List<PyExpression> components) {
    List<String> componentNames = new ArrayList<String>(components.size());
    for (PyExpression component : components) {
      final String refName = (component instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)component).getReferencedName() : null;
      if (refName == null) {
        return null;
      }
      componentNames.add(refName);
    }
    return QualifiedName.fromComponents(componentNames);
  }

  @Nullable
  public static QualifiedName fromExpression(PyExpression expr) {
    return expr instanceof PyReferenceExpression ? ((PyReferenceExpression) expr).asQualifiedName() : null;
  }
}
