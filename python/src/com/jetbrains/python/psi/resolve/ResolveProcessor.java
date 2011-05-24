package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ResolveProcessor implements PsiScopeProcessor {
  @NotNull private final String myName;
  private PsiElement myResult = null;
  private final List<PsiElement> myDefiners;

  public ResolveProcessor(@NotNull final String name) {
    myName = name;
    myDefiners = new ArrayList<PsiElement>(2); // 1 is typical, 2 is sometimes, more is rare.
  }

  public PsiElement getResult() {
    return myResult;
  }

  /**
   * Adds a NameDefiner point which is a secondary resolution target. E.g. import statement for imported name.
   *
   * @param definer
   */
  protected void addNameDefiner(PsiElement definer) {
    myDefiners.add(definer);
  }

  public List<PsiElement> getDefiners() {
    return myDefiners;
  }

  public String toString() {
    return PyUtil.nvl(myName) + ", " + PyUtil.nvl(myResult);
  }

  public boolean execute(PsiElement element, ResolveState substitutor) {
    if (element instanceof PyFile) {
      final VirtualFile file = ((PyFile)element).getVirtualFile();
      if (file != null) {
        if (myName.equals(file.getNameWithoutExtension())) {
          return setResult(element, null);
        }
        else if (PyNames.INIT_DOT_PY.equals(file.getName())) {
          VirtualFile dir = file.getParent();
          if ((dir != null) && myName.equals(dir.getName())) {
            return setResult(element, null);
          }
        }
      }
    }
    else if (element instanceof PsiNamedElement) {
      if (myName.equals(((PsiNamedElement)element).getName())) {
        return setResult(element, null);
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (referencedName != null && referencedName.equals(myName)) {
        return setResult(element, null);
      }
    }
    else if (element instanceof NameDefiner) {
      final NameDefiner definer = (NameDefiner)element;
      PsiElement by_name = definer.getElementNamed(myName);
      if (by_name != null) {
        // prefer more specific imported modules to less specific ones
        if (by_name instanceof PyImportedModule && myResult instanceof PyImportedModule &&
            ((PyImportedModule)by_name).isAncestorOf((PyImportedModule)myResult)) {
          return false;
        }

        setResult(by_name, definer);
        if (!PsiTreeUtil.isAncestor(element, by_name, true)) {
          addNameDefiner(definer);
        }
        // we can have same module imported directly and as part of chain (import os; import os.path)
        // direct imports always take precedence over imported modules
        // also, if some name is defined both in 'try' and 'except' parts of the same try/except statement,
        // we prefer the declaration in the 'try' part
        if (!(myResult instanceof PyImportedModule) && PsiTreeUtil.getParentOfType(element, PyExceptPart.class) == null) {
          return false;
        }
      }
      else if (element instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement) element;
        final PyQualifiedName qName = importElement.getImportedQName();
        // http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
        if (qName != null && qName.getComponentCount() > 1 && myName.equals(qName.getLastComponent()) &&
            PyNames.INIT_DOT_PY.equals(importElement.getContainingFile().getName())) {
          final PsiElement packageElement = ResolveImportUtil.resolveImportElement(importElement, qName.removeLastComponent());
          if (PyUtil.turnDirIntoInit(packageElement) == importElement.getContainingFile()) {
            myResult = PyUtil.turnDirIntoInit(ResolveImportUtil.resolveImportElement(importElement));
            addNameDefiner(importElement);
          }
        }

        // name is resolved to unresolved import (PY-956)
        String definedName = importElement.getAsName();
        if (definedName == null) {
          if (qName != null && qName.getComponentCount() == 1) {
            definedName = qName.getComponents().get(0);
          }
        }
        if (myName.equals(definedName)) {
          addNameDefiner(importElement);
        }
      }
    }
    else if (element instanceof PyFromImportStatement && PyNames.INIT_DOT_PY.equals(element.getContainingFile().getName())) {
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)element;
      final PyQualifiedName qName = fromImportStatement.getImportSourceQName();
      if (qName != null && qName.endsWith(myName)) {
        final PsiElement source = PyUtil.turnInitIntoDir(ResolveImportUtil.resolveFromImportStatementSource(fromImportStatement));
        if (source != null && source.getParent() == element.getContainingFile().getContainingDirectory()) {
          myResult = source;
          addNameDefiner(fromImportStatement);
        }
      }
    }

    return true;
  }

  @Nullable
  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  private boolean setResult(PsiElement result, @Nullable PsiElement definer) {
    if (myResult == null || getScope(myResult) == getScope(result) || (definer != null && getScope(myResult) == getScope(definer))) {
      myResult = result;
    }
    return false;
  }

  private static PsiElement getScope(PsiElement result) {
    return PsiTreeUtil.getParentOfType(result, PyFunction.class, PyClass.class, PyFile.class); 
  }
}
