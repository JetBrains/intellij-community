// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.refactoring.typeCook.deductive.builder.Result;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TypeCookProcessor extends BaseRefactoringProcessor {
  private PsiElement[] myElements;
  private final Settings mySettings;
  private Result myResult;

  public TypeCookProcessor(Project project, @NotNull PsiElement @NotNull [] elements, Settings settings) {
    super(project);

    myElements = elements;
    mySettings = settings;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new TypeCookViewDescriptor(myElements);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final SystemBuilder systemBuilder = new SystemBuilder(myProject, mySettings);

    final ReductionSystem commonSystem = systemBuilder.build(myElements);
    myResult = new Result(commonSystem);

    final ReductionSystem[] systems = commonSystem.isolate();

    for (final ReductionSystem system : systems) {
      if (system != null) {
        final ResolverTree tree = new ResolverTree(system);

        tree.resolve();

        final Binding solution = tree.getBestSolution();

        if (solution != null) {
          myResult.incorporateSolution(solution);
        }
      }
    }

    final Set<PsiElement> changedItems = myResult.getCookedElements();
    final UsageInfo[] usages = new UsageInfo[changedItems.size()];

    int i = 0;
    for (final PsiElement element : changedItems) {
      if (!(element instanceof PsiTypeCastExpression)) {
        usages[i++] = new UsageInfo(element) {
          @Override
          public String getTooltipText() {
            return myResult.getCookedType(element).getCanonicalText();
          }
        };
      }
      else {
        usages[i++] = new UsageInfo(element);
      }
    }

    return usages;
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    myElements = elements;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    final Set<PsiElement> victims = new HashSet<>();

    for (UsageInfo usage : usages) {
      victims.add(usage.getElement());
    }

    myResult.apply (victims);

    WindowManager.getInstance().getStatusBar(myProject).setInfo(myResult.getReport());
  }

  @Override
  protected boolean isGlobalUndoAction() {
    return true;
  }

  @Override
  protected @NotNull String getCommandName() {
    return JavaRareRefactoringsBundle.message("type.cook.command");
  }

  public List<PsiElement> getElements() {
    return List.of(myElements);
  }
}
