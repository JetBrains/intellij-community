package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;
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
    LightElement loggerField = new LightFieldBuilder(manager, loggerName, psiLoggerType)
        .setContainingClass(psiClass)
        .setModifiers(PsiModifier.FINAL, PsiModifier.STATIC, PsiModifier.PUBLIC)
        .setNavigationElement(psiAnnotation);

    target.add((Psi) loggerField);

    return true;
  }

}
