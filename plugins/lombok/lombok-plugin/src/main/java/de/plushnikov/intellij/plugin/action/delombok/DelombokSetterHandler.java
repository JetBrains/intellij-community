package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class DelombokSetterHandler implements CodeInsightActionHandler {

  private final SetterProcessor setterProcessor;
  private final SetterFieldProcessor setterFieldProcessor;

  public DelombokSetterHandler() {
    setterProcessor = new SetterProcessor();
    setterFieldProcessor = new SetterFieldProcessor();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (null != psiClass) {
      processClass(project, psiClass);

      UndoUtil.markPsiFileForUndo(file);
    }
  }

  protected void processClass(@NotNull Project project, @NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> psiAnnotations = setterProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = setterProcessor.process(psiClass, Processor.ProcessorModus.DELOMBOK);
    for (Object psiElement : psiElements) {
      psiClass.add((PsiElement) psiElement);
    }

    for (PsiAnnotation psiAnnotation : psiAnnotations) {
      psiAnnotation.delete();
    }

    processFields(psiClass);
  }

  private void processFields(@NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> psiAnnotations = setterFieldProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = setterFieldProcessor.process(psiClass, Processor.ProcessorModus.DELOMBOK);
    for (Object psiElement : psiElements) {
      psiClass.add((PsiMethod) psiElement);
    }

    for (PsiAnnotation psiAnnotation : psiAnnotations) {
      psiAnnotation.delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
