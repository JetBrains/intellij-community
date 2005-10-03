package com.intellij.packageDependencies;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.analysis.AnalysisScopeBundle;

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
        indicator.setText(AnalysisScopeBundle.message("find.dependencies.progress.text", psiFile.getVirtualFile().getPresentableUrl()));
      }

      final Set<PsiFile> depsByFile = builder.getDependencies().get(psiFile);
      final Set<PsiFile> deps = depsByFile != null ? new HashSet<PsiFile>(depsByFile) : new HashSet<PsiFile>();
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

  public static UsageInfo[] findBackwardDependencies(final DependenciesBuilder builder, final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();


    final Set<PsiFile> deps = new HashSet<PsiFile>();
    for (Iterator<PsiFile> iterator = searchFor.iterator(); iterator.hasNext();) {
      PsiFile psiFile = iterator.next();
      deps.addAll(builder.getDependencies().get(psiFile));
    }
    deps.retainAll(searchIn);
    if (deps.isEmpty()) return new UsageInfo[0];

    int totalCount = deps.size();
    int count = 0;
    for (Iterator<PsiFile> inIterator = deps.iterator(); inIterator.hasNext();) {
      final PsiFile psiFile = inIterator.next();
      if (indicator != null) {
        if (indicator.isCanceled()) throw new ProcessCanceledException();
        indicator.setFraction(((double)++count)/totalCount);
        indicator.setText(AnalysisScopeBundle.message("find.dependencies.progress.text", psiFile.getVirtualFile().getPresentableUrl()));
      }

      builder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (searchFor.contains(dependencyFile)) {
            usages.add(new UsageInfo(place));
          }
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }
}