package de.plushnikov.intellij.plugin.processor.clazz.log;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKeys;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Base lombok processor class for logger processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLogProcessor extends AbstractClassProcessor {

  private final String loggerType;
  private final String loggerInitializer;
  private final String loggerCategory;

  AbstractLogProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull String loggerType, @NotNull String loggerInitializer, @NotNull String loggerCategory) {
    super(PsiField.class, supportedAnnotationClass);
    this.loggerType = loggerType;
    this.loggerInitializer = loggerInitializer;
    this.loggerCategory = loggerCategory;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_LOG_ENABLED);
  }

  @NotNull
  public static String getLoggerName(@NotNull PsiClass psiClass) {
    return ConfigDiscovery.getInstance().getStringLombokConfigProperty(ConfigKeys.LOG_FIELDNAME, psiClass);
  }

  public static boolean isLoggerStatic(@NotNull PsiClass psiClass) {
    return ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(ConfigKeys.LOG_FIELD_IS_STATIC, psiClass);
  }

  @NotNull
  public String getLoggerType() {
    return loggerType;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isInterface() || psiClass.isAnnotationType()) {
      builder.addError("@Log is legal only on classes and enums");
      result = false;
    }
    if (result) {
      final String loggerName = getLoggerName(psiClass);
      if (hasFieldByName(psiClass, loggerName)) {
        builder.addError("Not generating field %s: A field with same name already exists", loggerName);
        result = false;
      }
    }
    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.add(createLoggerField(psiClass, psiAnnotation));
  }

  private LombokLightFieldBuilder createLoggerField(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final Project project = psiClass.getProject();
    final PsiManager manager = psiClass.getContainingFile().getManager();

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
    PsiType psiLoggerType = psiElementFactory.createTypeFromText(loggerType, psiClass);
    LombokLightFieldBuilder loggerField = new LombokLightFieldBuilder(manager, getLoggerName(psiClass), psiLoggerType)
        .withContainingClass(psiClass)
        .withModifier(PsiModifier.FINAL)
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(psiAnnotation);
    if (isLoggerStatic(psiClass)) {
      loggerField.withModifier(PsiModifier.STATIC);
    }

    final String loggerInitializerParameter = createLoggerInitializeParameter(psiClass, psiAnnotation);
    final PsiExpression initializer = psiElementFactory.createExpressionFromText(String.format(loggerInitializer, loggerInitializerParameter), psiClass);
    loggerField.setInitializer(initializer);
    return loggerField;
  }

  @NotNull
  private String createLoggerInitializeParameter(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String loggerInitializerParameter;
    final String topic = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "topic");
    if (StringUtil.isEmptyOrSpaces(topic)) {
      loggerInitializerParameter = String.format(loggerCategory, psiClass.getName());
    } else {
      loggerInitializerParameter = '"' + topic + '"';
    }
    return loggerInitializerParameter;
  }

  private boolean hasFieldByName(@NotNull PsiClass psiClass, String... fieldNames) {
    final Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      for (String fieldName : fieldNames) {
        if (fieldName.equals(psiField.getName())) {
          return true;
        }
      }
    }
    return false;
  }

}
