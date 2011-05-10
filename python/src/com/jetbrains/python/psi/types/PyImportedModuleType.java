package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  private PyImportedModule myImportedModule;

  public PyImportedModuleType(PyImportedModule importedModule) {
    myImportedModule = importedModule;
  }

  @NotNull
  public List<? extends PsiElement> resolveMember(String name,
                                                  PyExpression location,
                                                  AccessDirection direction,
                                                  PyResolveContext resolveContext) {
    final PsiElement element = myImportedModule.getElementNamed(name);
    if (element != null) {
      return new SmartList<PsiElement>(element);
    }
    else {
      return Collections.emptyList();
    }
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
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
    return "imported module " + myImportedModule.getImportedPrefix().toString();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;  // no module can be imported from builtins
  }
}
