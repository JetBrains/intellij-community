package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ResolveProcessor implements PyAsScopeProcessor {
  @NotNull private final String myName;
  private PsiElement myResult = null;
  private final List<NameDefiner> myDefiners;

  public ResolveProcessor(@NotNull final String name) {
    myName = name;
    myDefiners = new ArrayList<NameDefiner>(2); // 1 is typical, 2 is sometimes, more is rare.
  }

  public PsiElement getResult() {
    return myResult;
  }

  /**
   * Adds a NameDefiner point which is a secondary resolution target. E.g. import statement for imported name.
   *
   * @param definer
   */
  protected void addNameDefiner(NameDefiner definer) {
    myDefiners.add(definer);
  }

  public List<NameDefiner> getDefiners() {
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
          return setResult(element);
        }
        else if (PyNames.INIT_DOT_PY.equals(file.getName())) {
          VirtualFile dir = file.getParent();
          if ((dir != null) && myName.equals(dir.getName())) {
            return setResult(element);
          }
        }
      }
    }
    else if (element instanceof PsiNamedElement) {
      if (myName.equals(((PsiNamedElement)element).getName())) {
        return setResult(element);
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (referencedName != null && referencedName.equals(myName)) {
        return setResult(element);
      }
    }
    else if (element instanceof NameDefiner) {
      final NameDefiner definer = (NameDefiner)element;
      PsiElement by_name = definer.getElementNamed(myName);
      if (by_name != null) {
        setResult(by_name);
        if (!PsiTreeUtil.isAncestor(element, by_name, true)) {
          addNameDefiner(definer);
        }
        // we can have same module imported directly and as part of chain (import os; import os.path)
        // direct imports always take precedence over imported modules
        if (!(myResult instanceof PyImportedModule)) {
          return false;
        }
      }
    }

    return true;
  }

  public boolean execute(final PsiElement element, final String asName) {
    if (asName.equals(myName)) {
      return setResult(element);
    }
    return true;
  }

  @Nullable
  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  private boolean setResult(PsiElement result) {
    if (myResult == null || getScope(myResult) == getScope(result)) {
      myResult = result;
    }
    return false;
  }

  private static PsiElement getScope(PsiElement result) {
    return PsiTreeUtil.getParentOfType(result, PyFunction.class, PyClass.class, PyFile.class); 
  }
}
