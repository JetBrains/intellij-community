package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  private PyImportedModule myImportedModule;

  public PyImportedModuleType(PyImportedModule importedModule) {
    myImportedModule = importedModule;
  }

  public PsiElement resolveMember(String name) {
    return myImportedModule.getElementNamed(name);
  }

  public Object[] getCompletionVariants(PyQualifiedExpression referenceExpression, ProcessingContext context) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    final List<PyImportElement> importTargets = ((PyFileImpl)myImportedModule.getContainingFile()).getImportTargets();
    final int imported = myImportedModule.getImportedPrefix().getComponentCount();
    for (PyImportElement importTarget : importTargets) {
      final PyQualifiedName qName = importTarget.getImportedQName();
      if (qName != null && qName.matchesPrefix(myImportedModule.getImportedPrefix())) {
        final List<String> components = qName.getComponents();
        if (components.size() > imported) {
          String module = components.get(imported);
          result.add(LookupElementBuilder.create(module));
        }
      }
    }
    return result.toArray(new Object[result.size()]);
  }

  public String getName() {
    return "PyImportedModuleType:" + myImportedModule.toString();
  }
}
