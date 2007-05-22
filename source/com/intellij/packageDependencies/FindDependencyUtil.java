package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FindDependencyUtil {
  private FindDependencyUtil() {}

  public static UsageInfo[] findDependencies(@Nullable final DependenciesBuilder builder, Set<PsiFile> searchIn, Set<PsiFile> searchFor) {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    int totalCount = searchIn.size();
    int count = 0;

    nextFile: for (final PsiFile psiFile : searchIn) {
      count = updateIndicator(indicator, totalCount, count, psiFile);

      if (!psiFile.isValid()) continue;

      final Set<PsiFile> precomputedDeps;
      if (builder != null) {
        final Set<PsiFile> depsByFile = builder.getDependencies().get(psiFile);
        precomputedDeps = depsByFile != null ? new HashSet<PsiFile>(depsByFile) : new HashSet<PsiFile>();
        precomputedDeps.retainAll(searchFor);
        if (precomputedDeps.isEmpty()) continue nextFile;
      }
      else {
        precomputedDeps = Collections.unmodifiableSet(searchFor);
      }

      DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (precomputedDeps.contains(dependencyFile)) {
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
    for (PsiFile psiFile : searchFor) {
      deps.addAll(builder.getDependencies().get(psiFile));
    }
    deps.retainAll(searchIn);
    if (deps.isEmpty()) return new UsageInfo[0];

    int totalCount = deps.size();
    int count = 0;
    for (final PsiFile psiFile : deps) {
      count = updateIndicator(indicator, totalCount, count, psiFile);

      DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
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

  private static int updateIndicator(final ProgressIndicator indicator, final int totalCount, int count, final PsiFile psiFile) {
    if (indicator != null) {
      if (indicator.isCanceled()) throw new ProcessCanceledException();
      indicator.setFraction(((double)++count) / totalCount);
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        indicator.setText(AnalysisScopeBundle.message("find.dependencies.progress.text", virtualFile.getPresentableUrl()));
      }
    }
    return count;
  }
}