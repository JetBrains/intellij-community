package com.intellij.refactoring.typeCook;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DummyComplexUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.typeCook.deductive.builder.Result;
import com.intellij.refactoring.typeCook.deductive.builder.System;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.NotNull;

public class TypeCookProcessor extends BaseRefactoringProcessor {
  private PsiElement[] myElements;
  private final Settings mySettings;
  private Result myResult;

  public TypeCookProcessor(Project project, PsiElement[] elements, Settings settings) {
    super(project);

    myElements = elements;
    mySettings = settings;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new TypeCookViewDescriptor(myElements, usages, refreshCommand);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final SystemBuilder systemBuilder = new SystemBuilder(myProject, mySettings);

    final System commonSystem = systemBuilder.build(myElements);
    myResult = new Result(commonSystem);

    final System[] systems = commonSystem.isolate();

    for (final System system : systems) {
      if (system != null) {
        final ResolverTree tree = new ResolverTree(system);

        tree.resolve();

        final Binding solution = tree.getBestSolution();

        if (solution != null) {
          myResult.incorporateSolution(solution);
        }
      }
    }

    final HashSet<PsiElement> cookedItems = myResult.getCookedElements();
    final UsageInfo[] usages = new UsageInfo[cookedItems.size()];

    int i = 0;
    for (final PsiElement element : cookedItems) {
      usages[i++] = new UsageInfo(element) {
        public String getTooltipText() {
          return myResult.getCookedType(element).getCanonicalText();
        }
      };
    }

    return usages;
  }

  protected void refreshElements(PsiElement[] elements) {
    myElements = elements;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    final HashSet<PsiElement> victims = new HashSet<PsiElement>();

    for (UsageInfo usage : usages) {
      victims.add(usage.getElement());
    }

    myResult.apply (victims);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isOpen()) {
          WindowManager.getInstance().getStatusBar(myProject).setInfo(myResult.getReport());
        }
      }
    });

    UndoManager.getInstance(myProject).undoableActionPerformed(new DummyComplexUndoableAction()); // force confirmation dialog for undo
  }

  protected String getCommandName() {
    return RefactoringBundle.message("type.cook.command");
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(Arrays.asList(myElements));
  }
}
