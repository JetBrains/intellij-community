package com.intellij.refactoring.base;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class RPPBaseRefactoringProcessor extends BaseRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.RPPBaseRefactoringProcessor");
  private final boolean previewUsages;

  protected RPPBaseRefactoringProcessor(Project project, boolean previewUsages) {
    super(project);
    this.previewUsages = previewUsages;
  }

  protected boolean isPreviewUsages(UsageInfo[] usageInfos) {
    return previewUsages || super.isPreviewUsages(usageInfos);
  }

  protected void refreshElements(PsiElement[] psiElements) {
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    for (UsageInfo usageInfo : usageInfos) {
      try {
        ((RefactorJUsageInfo)usageInfo).fixUsage();
      }
      catch (IncorrectOperationException e) {
        logger.error(e);
      }
    }
  }


  @NotNull
  protected UsageInfo[] findUsages() {
    final List<RefactorJUsageInfo> usages = new ArrayList<RefactorJUsageInfo>();
    findUsages(usages);
    final int numUsages = usages.size();
    final RefactorJUsageInfo[] usageArray = usages.toArray(new RefactorJUsageInfo[numUsages]);
    RefactoringUtil.sortDepthFirstRightLeftOrder(usageArray);
    return usageArray;
  }

  protected abstract void findUsages(@NotNull List<RefactorJUsageInfo> usages);

}
