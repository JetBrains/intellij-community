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
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

import java.util.*;

public class TypeCookProcessor extends BaseRefactoringProcessor implements TypeCookDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.TypeCookProcessor");

  private PsiElement[] myElements;
  private Kitchen myKitchen;
  private TypeCookDialog myDialog;

  public TypeCookProcessor(Project project, PsiElement[] elements) {
    super(project);

    myElements = elements;
    myKitchen = new Kitchen(PsiManager.getInstance(project));
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new TypeCookViewDescriptor(myElements, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    HashSet<PsiElement> victims = myKitchen.getVictims(myElements, myDialog.getSettings());

    UsageInfo[] usages = new UsageInfo[victims.size()];
    int i = 0;

    for (Iterator<PsiElement> j = victims.iterator(); j.hasNext(); i++) {
      final PsiElement element = j.next();
      usages[i] = new UsageInfo(element){
        public String getTooltipText() {
          return myKitchen.getCookedType(element).getCanonicalText();  
        }
      };
    }

    return usages;
  }

  protected void refreshElements(PsiElement[] elements) {
    myElements = elements;
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    boolean toPreview = myDialog.isPreviewUsages();

    if (UsageViewUtil.hasReadOnlyUsages(usages)) {
      toPreview = true;
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
    }

    return toPreview;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    HashSet<PsiElement> victims = new HashSet<PsiElement>();

    for (int i = 0; i < usages.length; i++)
      victims.add(usages[i].getElement());

    myKitchen.perform(victims);

    UndoManager.getInstance(myProject).undoableActionPerformed(new DummyComplexUndoableAction()); // force confirmation dialog for undo
  }

  protected String getCommandName() {
    return "Generify";
  }

  public void run(TypeCookDialog dialog) {
    myDialog = dialog;

    final Runnable runnable = new Runnable() {
          public void run() {
            myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
          }
        };

    setPrepareSuccessfulSwingThreadCallback(runnable);
    run((Object) null);
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(Arrays.asList(myElements));
  }
}
