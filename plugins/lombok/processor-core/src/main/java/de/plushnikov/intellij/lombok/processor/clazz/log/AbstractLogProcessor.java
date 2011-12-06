package de.plushnikov.intellij.lombok.processor.clazz.log;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.AbstractLombokClassProcessor;
import de.plushnikov.intellij.lombok.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Base lombok processor class for logger processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLogProcessor extends AbstractLombokClassProcessor {

  private final static String loggerName = "log";
  private final String loggerType;
  private final String loggerInitializer;

  protected AbstractLogProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull String loggerType, @NotNull String loggerInitializer) {
    super(supportedAnnotationClass, PsiField.class);
    this.loggerType = loggerType;
    this.loggerInitializer = loggerInitializer;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isInterface() || psiClass.isAnnotationType()) {
      builder.addError("@Log is legal only on classes and enums");
      result = false;
    }
    if (result && hasFieldByName(psiClass, loggerName)) {
      builder.addError(String.format("Not generating field %s: A field with same name already exists", loggerName));
      result = false;
    }
    return result;
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
    PsiType psiLoggerType = psiElementFactory.createTypeFromText(loggerType, psiClass);
    LombokLightFieldBuilder loggerField = LombokPsiElementFactory.getInstance().createLightField(manager, loggerName, psiLoggerType)
        .setContainingClass(psiClass)
        .addModifier(PsiModifier.FINAL).addModifier(PsiModifier.STATIC).addModifier(PsiModifier.PRIVATE)
        .withNavigationElement(psiAnnotation);

    final String classQualifiedName = psiClass.getQualifiedName();
    final String className = null != classQualifiedName ? classQualifiedName : psiClass.getName();
    PsiExpression initializer = psiElementFactory.createExpressionFromText(String.format(loggerInitializer, className), psiClass);
    loggerField.setInitializer(initializer);

    target.add((Psi) loggerField);
  }

  protected boolean hasFieldByName(@NotNull PsiClass psiClass, String... fieldNames) {
    final PsiField[] psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      for (String fieldName : fieldNames) {
        if (psiField.getName().equals(fieldName)) {
          return true;
        }
      }
    }
    return false;
  }
}
