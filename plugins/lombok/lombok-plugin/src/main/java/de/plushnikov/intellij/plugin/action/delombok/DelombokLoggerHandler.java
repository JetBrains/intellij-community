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
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.CommonsLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4j2Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.LogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.XSlf4jProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DelombokLoggerHandler implements CodeInsightActionHandler {

  private final Collection<AbstractLogProcessor> logProcessors;

  public DelombokLoggerHandler() {
    logProcessors = Arrays.asList(
        new CommonsLogProcessor(), new Log4jProcessor(), new Log4j2Processor(),
        new LogProcessor(), new Slf4jProcessor(), new XSlf4jProcessor());
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
    for (AbstractLogProcessor logProcessor : logProcessors) {

      final PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiClass, logProcessor.getSupportedAnnotation());
      if (null != psiAnnotation) {
        List<? super PsiElement> classFields = logProcessor.process(psiClass, Processor.ProcessorModus.DELOMBOK);
        for (Object classField : classFields) {
          psiClass.add((PsiElement) classField);
        }

        psiAnnotation.delete();
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
