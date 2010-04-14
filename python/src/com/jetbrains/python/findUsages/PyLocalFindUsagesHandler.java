package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyLocalFindUsagesHandler extends FindUsagesHandler {
  public PyLocalFindUsagesHandler(@NotNull PsiElement psiElement) {
    super(psiElement);
  }

  @Override
  public void processElementUsages(@NotNull final PsiElement element,
                                   @NotNull final Processor<UsageInfo> processor,
                                   @NotNull FindUsagesOptions options) {
    super.processElementUsages(element, processor, options);
    if (element instanceof PyTargetExpressionImpl) {
      final PsiElement redefinitionScope = ((PyTargetExpressionImpl)element).getRedefinitionScope();
      if (redefinitionScope != null) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            redefinitionScope.accept(new PyRecursiveElementVisitor() {
              private boolean found = false;

              @Override
              public void visitPyTargetExpression(PyTargetExpression node) {
                if (Comparing.equal(node.getName(), ((PyTargetExpression)element).getName()) && !found) {
                  found = true;
                  processor.process(new UsageInfo(node));
                }
              }
            });
          }
        });
      }
    }
  }
}
