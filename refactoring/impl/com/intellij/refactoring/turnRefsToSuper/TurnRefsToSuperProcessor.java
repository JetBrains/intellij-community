package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.pom.java.LanguageLevel;

import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;

public class TurnRefsToSuperProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor");

  private PsiClass mySuper;
  public TurnRefsToSuperProcessor(Project project,
                                  PsiClass aClass,
                                  PsiClass aSuper,
                                  boolean replaceInstanceOf) {
    super(project, replaceInstanceOf, aSuper.getName());
    myClass = aClass;
    mySuper = aSuper;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("turn.refs.to.super.command",
                                     UsageViewUtil.getDescriptiveName(myClass), UsageViewUtil.getDescriptiveName(mySuper));
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new RefsToSuperViewDescriptor(myClass, mySuper);
  }

  void setClasses(final PsiClass aClass, final PsiClass aSuper) {
    myClass = aClass;
    mySuper = aSuper;
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiReference[] refs = mySearchHelper.findReferences(myClass, GlobalSearchScope.projectScope(myProject), false);

    final ArrayList<UsageInfo> result = detectTurnToSuperRefs(refs, new ArrayList<UsageInfo>());

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(final PsiElement[] elements) {
    LOG.assertTrue(elements.length == 2 && elements[0] instanceof PsiClass && elements[1] instanceof PsiClass);
    setClasses ((PsiClass) elements[0], (PsiClass) elements[1]);
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && refUsages.get().length == 0) {
      String message = RefactoringBundle.message("no.usages.can.be.replaced", myClass.getQualifiedName(), mySuper.getQualifiedName());
      Messages.showInfoMessage(myProject, message, TurnRefsToSuperHandler.REFACTORING_NAME);
      return false;
    }

    return super.preprocessUsages(refUsages);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      final PsiClass aSuper = mySuper;
      processTurnToSuperRefs(usages, aSuper);

    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    performVariablesRenaming();
  }

  protected boolean isInSuper(PsiElement member) {
    if (!(member instanceof PsiMember)) return false;
    final PsiManager manager = member.getManager();
    if (InheritanceUtil.isInheritorOrSelf(mySuper, ((PsiMember)member).getContainingClass(), true)) return true;

    if (member instanceof PsiField) {
      final PsiClass containingClass = ((PsiField) member).getContainingClass();
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(member);
      if (manager.areElementsEquivalent(containingClass, manager.getElementFactory().getArrayClass(languageLevel))) {
        return true;
      }
    } else if (member instanceof PsiMethod) {
      return mySuper.findMethodBySignature((PsiMethod) member, true) != null;
    }

    return false;
  }

  protected boolean isSuperInheritor(PsiClass aClass) {
    return InheritanceUtil.isInheritorOrSelf(mySuper, aClass, true);
  }

  public PsiClass getSuper() {
    return mySuper;
  }

  public PsiClass getTarget() {
    return myClass;
  }

  public boolean isReplaceInstanceOf() {
    return myReplaceInstanceOf;
  }
}