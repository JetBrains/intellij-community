package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightFieldBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.AbstractLombokClassProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public abstract class AbstractLogProcessor extends AbstractLombokClassProcessor {

  private final static String loggerName = "log";
  private final String loggerType;
  private final String loggerInitializer;

  protected AbstractLogProcessor(@NotNull String supportedAnnotation, @NotNull String loggerType, @NotNull String loggerInitializer) {
    super(supportedAnnotation, PsiField.class);
    this.loggerType = loggerType;
    this.loggerInitializer = loggerInitializer;
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();

    PsiType psiLoggerType = JavaPsiFacade.getElementFactory(project).createTypeFromText(loggerType, psiClass);
    LightFieldBuilder loggerField = new LightFieldBuilder(manager, loggerName, psiLoggerType)
        .setContainingClass(psiClass)
        .setModifiers(PsiModifier.FINAL, PsiModifier.STATIC, PsiModifier.PUBLIC);

    target.add((Psi) loggerField);

    return true;
  }

}
