package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class TurnRefsToSuperProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor");

  private PsiClass mySuper;
  private final boolean myPreviewUsages;

  public TurnRefsToSuperProcessor(Project project,
                                  PsiClass aClass,
                                  PsiClass aSuper,
                                  boolean replaceInstanceOf,
                                  boolean previewUsages
                                  ) {
    super(project, replaceInstanceOf);
    myClass = aClass;
    mySuper = aSuper;
    myPreviewUsages = previewUsages;
  }

  protected String getCommandName() {
    return "Replacing usages of " + UsageViewUtil.getDescriptiveName(myClass) + " with " + UsageViewUtil.getDescriptiveName(mySuper);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new RefsToSuperViewDescriptor(this, myClass, mySuper, usages, refreshCommand);
  }

  void setClasses(final PsiClass aClass, final PsiClass aSuper) {
    myClass = aClass;
    mySuper = aSuper;
  }

  protected UsageInfo[] findUsages() {
    final PsiReference[] refs = mySearchHelper.findReferences(myClass, GlobalSearchScope.projectScope(myProject), false);

    final ArrayList<UsageInfo> result = detectTurnToSuperRefs(refs, new ArrayList<UsageInfo>());

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(final PsiElement[] elements) {
    final boolean condition = elements.length == 2 && elements[0] instanceof PsiClass && elements[1] instanceof PsiClass;
    LOG.assertTrue(condition);
    myClass = (PsiClass) elements[0];
    mySuper = (PsiClass) elements[1];
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    boolean toPreview = myPreviewUsages;
    return super.isPreviewUsages(usages) || toPreview;
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && usages[0].length == 0) {
      String message = "No usages of " + myClass.getQualifiedName() + "\n" +
              "can be replaced with usages of " + mySuper.getQualifiedName();
      Messages.showInfoMessage(myProject, message, TurnRefsToSuperHandler.REFACTORING_NAME);
      return false;
    }
    return true;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      final PsiClass aSuper = mySuper;
      processTurnToSuperRefs(usages, aSuper);

    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected boolean isInSuper(PsiElement member) {
    if (member instanceof PsiField) {
      final PsiClass containingClass = ((PsiField) member).getContainingClass();
      final PsiManager manager = member.getManager();
      if (manager.areElementsEquivalent(containingClass, manager.getElementFactory().getArrayClass())) {
        return true;
      }
      return manager.areElementsEquivalent(containingClass, mySuper)
              || mySuper.isInheritor(containingClass, true);
    } else if (member instanceof PsiMethod) {
      if (member.getParent().equals(mySuper)) return true;
      PsiMethod methodInSuper2 = mySuper.findMethodBySignature((PsiMethod) member, true);
      if (methodInSuper2 != null) {
        return true;
      }
      return false;
    } else {
      return false; //?
    }
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