package com.jetbrains.python.codeInsight;

import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.findUsages.PyPsiFindUsagesHandlerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyPsiIndexUtil {
  @NotNull
  public static List<UsageInfo> findUsages(@NotNull PsiNamedElement element, boolean forHighlightUsages) {
    final List<UsageInfo> usages = new ArrayList<>();
    final FindUsagesHandlerBase handler = new PyPsiFindUsagesHandlerFactory() {
    }.createFindUsagesHandler(element, forHighlightUsages);
    assert handler != null;
    final List<PsiElement> elementsToProcess = new ArrayList<>();
    Collections.addAll(elementsToProcess, handler.getPrimaryElements());
    Collections.addAll(elementsToProcess, handler.getSecondaryElements());
    for (PsiElement e : elementsToProcess) {
      handler.processElementUsages(e, usageInfo -> {
        if (!usageInfo.isNonCodeUsage) {
          usages.add(usageInfo);
        }
        return true;
      }, FindUsagesHandlerBase.createFindUsagesOptions(element.getProject(), null));
    }
    return usages;
  }
}
