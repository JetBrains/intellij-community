package com.jetbrains.python.codeInsight;

import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.findUsages.PyPsiFindUsagesHandlerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyPsiIndexUtil {
  public static @NotNull List<UsageInfo> findUsages(@NotNull PsiNamedElement element, boolean forHighlightUsages) {
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

  public static boolean isNotUnderSourceRoot(final @NotNull Project project, final @Nullable PsiFile psiFile) {
    if (psiFile == null) {
      return true;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (fileIndex.isExcluded(virtualFile) || (fileIndex.isInLibraryClasses(virtualFile) && !fileIndex.isInContent(virtualFile))) {
        return true;
      }
    }
    return false;
  }
}
