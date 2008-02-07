package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.ide.util.SuperMethodWarningUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.List;

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

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
    findSubmemberHidesMemberCollisions((PsiMethod) element, newName, result);
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

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    final PsiMethod method = (PsiMethod) element;
    OverridingMethodsSearch.search(method, true).forEach(new Processor<PsiMethod>() {
      public boolean process(PsiMethod overrider) {
        final String overriderName = overrider.getName();
        final String baseName = method.getName();
        final String newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName);
        if (newOverriderName != null) {
          allRenames.put(overrider, newOverriderName);
        }
        return true;
      }
    });
  }

  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_METHOD;
  }

  public boolean isToSearchInComments(final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
  }

  @Nullable
  public PsiElement substituteElementToRename(PsiElement element) {
    PsiMethod psiMethod = (PsiMethod)element;
    if (psiMethod.isConstructor()) {
      PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return null;
      element = containingClass;
      if (!PsiElementRenameHandler.canRename(element, element.getProject())) {
        return null;
      }
      return element;
    }
    return SuperMethodWarningUtil.checkSuperMethod(psiMethod, RefactoringBundle.message("to.rename"));
  }

  private static void findSubmemberHidesMemberCollisions(final PsiMethod method, final String newName, final List<UsageInfo> result) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return;
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass, containingClass.getUseScope(), true).findAll();

    MethodSignature oldSignature = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignature newSignature = MethodSignatureUtil.createMethodSignature(newName, oldSignature.getParameterTypes(),
                                                                             oldSignature.getTypeParameters(),
                                                                             oldSignature.getSubstitutor());
    for (PsiClass inheritor : inheritors) {
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, inheritor, PsiSubstitutor.EMPTY);
      final PsiMethod[] methodsByName = inheritor.findMethodsByName(newName, false);
      for (PsiMethod conflictingMethod : methodsByName) {
        if (newSignature.equals(conflictingMethod.getSignature(superSubstitutor))) {
          result.add(new SubmemberHidesMemberUsageInfo(conflictingMethod, method));
          break;
        }
      }
    }
  }
}
