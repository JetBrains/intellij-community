package com.jetbrains.performancePlugin.lang.inspections;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.commands.StartProfileCommand;
import com.jetbrains.performancePlugin.commands.StopProfileCommand;
import com.jetbrains.performancePlugin.lang.IJPerfLanguage;
import com.jetbrains.performancePlugin.lang.psi.IJPerfFile;
import org.jetbrains.annotations.NotNull;

public class IJPerfStartStopProfileInspection implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {

    if (!(element instanceof IJPerfFile)) return;

    PsiElement startCommand = null;
    PsiElement @NotNull [] children = element.getChildren();
    PsiElement currentPsiElement;
    for (int i = 0; i <= children.length; i++) {

      //Check the last element
      if (i == children.length) {
        checkAndRegisterUnclosedProblem(startCommand, holder);
        break;
      }

      currentPsiElement = children[i];
      if (currentPsiElement.getText().startsWith(StartProfileCommand.PREFIX)) {
        checkAndRegisterUnclosedProblem(startCommand, holder);
        checkAndRegisterRepeatProblem(startCommand, currentPsiElement, holder);
        startCommand = currentPsiElement;
      }
      else if (currentPsiElement.getText().startsWith(StopProfileCommand.PREFIX)) {
        startCommand = null;
      }
    }
  }

  private static void checkAndRegisterUnclosedProblem(PsiElement startCommand, AnnotationHolder holder) {
    if (startCommand != null) {
      holder
        .newAnnotation(HighlightSeverity.WARNING,
                       PerformanceTestingBundle.message("inspection.message.activity.started.but.never.closed.with.stopprofile",
                                                        startCommand.getText()))
        .range(startCommand)
        .withFix(new AddStopCommandFix())
        .create();
    }
  }

  private static void checkAndRegisterRepeatProblem(PsiElement prevStartCommand, PsiElement curStartCommand, AnnotationHolder holder) {
    if (prevStartCommand != null && curStartCommand != null) {
      holder
        .newAnnotation(HighlightSeverity.ERROR,
                       PerformanceTestingBundle.message("inspection.message.two.startprofile.commands.cant.follow.each.other",
                                                        curStartCommand.getText()))
        .range(curStartCommand)
        .withFix(new AddStopCommandFix())
        .create();
    }
  }

  private static class AddStopCommandFix extends BaseIntentionAction {

    @Override
    public @NotNull String getFamilyName() {
      return PerformanceTestingBundle.message("intention.add.stopcommand");
    }

    @Override
    public @NotNull String getText() {
      return PerformanceTestingBundle.message("intention.add.stopcommand");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      file.addBefore(createCRLF(project), file.add(createStopCommand(project)));
    }

    private static IJPerfFile createFile(Project project, String text) {
      return (IJPerfFile)PsiFileFactory.getInstance(project).createFileFromText("dummy.ijperf", IJPerfLanguage.INSTANCE, text);
    }

    private static PsiElement createStopCommand(Project project) {
      return createFile(project, StopProfileCommand.PREFIX).getFirstChild();
    }

    private static PsiElement createCRLF(Project project) {
      return createFile(project, "\n").getFirstChild();
    }
  }
}
