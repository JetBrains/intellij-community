package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.NameDefiner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ResolveProcessor implements PyAsScopeProcessor {
  private final String myName;
  private PsiElement myResult = null;
  private final List<NameDefiner> myDefiners;

  public ResolveProcessor(final String name) {
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
          myResult = element;
          return false;
        }
        else if (ResolveImportUtil.INIT_PY.equals(file.getName())) {
          VirtualFile dir = file.getParent();
          if ((dir != null) && myName.equals(dir.getName())) {
            myResult = element;
            return false;
          }
        }
      }
    }
    else if (element instanceof PsiNamedElement) {
      if (myName.equals(((PsiNamedElement)element).getName())) {
        myResult = element;
        return false;
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (referencedName != null && referencedName.equals(myName)) {
        myResult = element;
        return false;
      }
    }
    else if (element instanceof NameDefiner) {
      final NameDefiner definer = (NameDefiner)element;
      PsiElement by_name = definer.getElementNamed(myName);
      if (by_name != null) {
        myResult = by_name;
        if (!PsiTreeUtil.isAncestor(element, by_name, true)) { // non-trivial definer
          addNameDefiner(definer);
        }
        return false;
      }
    }

    return true;
  }

  public boolean execute(final PsiElement element, final String asName) {
    if (asName.equals(myName)) {
      myResult = element;
      return false;
    }
    return true;
  }

  @Nullable
  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }


}
