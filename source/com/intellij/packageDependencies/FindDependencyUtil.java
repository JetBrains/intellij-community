package com.intellij.packageDependencies;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;

import java.util.*;

public class FindDependencyUtil {
  private FindDependencyUtil() {}

  public static UsageInfo[] findDependencies(final DependenciesBuilder builder, Set<PsiFile> searchIn, Set<PsiFile> searchFor) {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    int totalCount = searchIn.size();
    int count = 0;

    for (Iterator<PsiFile> inIterator = searchIn.iterator(); inIterator.hasNext();) {
      final PsiFile psiFile = inIterator.next();
      if (indicator != null) {
        if (indicator.isCanceled()) throw new ProcessCanceledException();
        indicator.setFraction(((double)++count)/totalCount);
        indicator.setText("Searching for usages: " + psiFile.getVirtualFile().getPresentableUrl());
      }

      final Set<PsiFile> deps = new HashSet<PsiFile>(builder.getDependencies().get(psiFile));
      deps.retainAll(searchFor);
      if (deps.isEmpty()) continue;

      builder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (deps.contains(dependencyFile)) {
            usages.add(new UsageInfo(place));
          }
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }
}