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
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class DelombokGetterHandler implements CodeInsightActionHandler {

  private final GetterProcessor getterProcessor;
  private final GetterFieldProcessor getterFieldProcessor;

  public DelombokGetterHandler() {
    getterProcessor = new GetterProcessor();
    getterFieldProcessor = new GetterFieldProcessor();
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
    Collection<PsiAnnotation> psiAnnotations = getterProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = getterProcessor.process(psiClass, Processor.ProcessorModus.DELOMBOK);
    for (Object psiElement : psiElements) {
      psiClass.add((PsiElement) psiElement);
    }

    for (PsiAnnotation psiAnnotation : psiAnnotations) {
      psiAnnotation.delete();
    }

    processFields(psiClass);
  }

  private void processFields(@NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> psiAnnotations = getterFieldProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = getterFieldProcessor.process(psiClass, Processor.ProcessorModus.DELOMBOK);
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
