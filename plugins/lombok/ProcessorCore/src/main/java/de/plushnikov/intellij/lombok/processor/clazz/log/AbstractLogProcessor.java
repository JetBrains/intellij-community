package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.processor.clazz.AbstractLombokClassProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public abstract class AbstractLogProcessor extends AbstractLombokClassProcessor {

  private final String loggerDefinition;

  protected AbstractLogProcessor(String supportedAnnotation, Class suppertedClass, String pLoggerDefinition) {
    super(supportedAnnotation, suppertedClass);
    loggerDefinition = pLoggerDefinition;
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiField valuesMethod = elementFactory.createFieldFromText(loggerDefinition, psiClass);
//TODO implement me
    return true;
  }

}
