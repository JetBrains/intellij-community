package com.intellij.refactoring.typeCook;

import com.intellij.openapi.command.undo.DummyComplexUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.typeCook.deductive.builder.*;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.builder.System;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;

import java.util.*;

public class TypeCookProcessor extends BaseRefactoringProcessor implements TypeCookDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.TypeCookProcessor");

  private PsiElement[] myElements;
  private SystemBuilder mySystemBuilder;
  private TypeCookDialog myDialog;
  private Result myResult;

  public TypeCookProcessor(Project project, PsiElement[] elements) {
    super(project);

    myElements = elements;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new TypeCookViewDescriptor(myElements, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    mySystemBuilder = new SystemBuilder(myProject, myDialog.getSettings());

    final System commonSystem = mySystemBuilder.build(myElements);
    myResult = new Result(commonSystem);

    final System[] systems = commonSystem.isolate();

    for (int i = 0; i < systems.length; i++) {
      final System system = systems[i];

      if (system != null) {
        final ResolverTree tree = new ResolverTree(system);

        tree.resolve();

        final Binding[] solutions = tree.getSolutions();

        if (solutions.length > 0) {
          myResult.incorporateSolution(solutions[0]);
        }
      }
    }

    final HashSet<PsiElement> cookedItems = myResult.getCookedElements();
    final UsageInfo[] usages = new UsageInfo[cookedItems.size()];

    int i = 0;
    for (final Iterator<PsiElement> e=cookedItems.iterator(); e.hasNext();) {
      final PsiElement element = e.next();

      usages[i++] = new UsageInfo(element){
        public String getTooltipText(){
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

    for (int i = 0; i < usages.length; i++) {
      victims.add(usages[i].getElement());
    }

    myResult.apply (victims);

    UndoManager.getInstance(myProject).undoableActionPerformed(new DummyComplexUndoableAction()); // force confirmation dialog for undo
  }

  protected String getCommandName() {
    return "Generify";
  }

  public void run(TypeCookDialog dialog) {
    myDialog = dialog;
    setPreviewUsages(dialog.isPreviewUsages());

    final Runnable runnable = new Runnable() {
      public void run() {
        myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    };

    setPrepareSuccessfulSwingThreadCallback(runnable);
    run((Object)null);
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(Arrays.asList(myElements));
  }
}
