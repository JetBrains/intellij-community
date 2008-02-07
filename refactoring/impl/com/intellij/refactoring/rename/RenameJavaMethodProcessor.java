package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RenameJavaMethodProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaMethodProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiMethod;
  }

  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiMethod method = (PsiMethod) psiElement;
    // do actual rename of overriding/implementing methods and of references to all them
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      if (!(element instanceof PsiMethod)) {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        } else {
          ref = element.getReference();
        }
        if (ref != null) {
          ref.handleElementRename(newName);
        }
      }
    }

    // do actual rename of method
    method.setName(newName);
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiMethod) {
        ((PsiMethod)element).setName(newName);
      }
    }
    listener.elementRenamed(method);
  }

  @NotNull
  public Collection<PsiReference> findReferences(final PsiElement element) {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
    return MethodReferencesSearch.search((PsiMethod)element, projectScope, true).findAll();
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final Collection<String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    PsiMethod refactoredMethod = (PsiMethod)element;
    if (newName.equals(refactoredMethod.getName())) return;
    final PsiMethod prototype = (PsiMethod)refactoredMethod.copy();
    try {
      prototype.setName(newName);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    ConflictsUtil.checkMethodConflicts(
      refactoredMethod.getContainingClass(),
      refactoredMethod,
      prototype,
      conflicts);
  }
}
