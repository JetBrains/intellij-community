package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.extension.UserMapKeys;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Inspect and validate @EqualsAndHashCode lombok annotation on a class
 * Creates equals/hashcode method for fields of this class
 *
 * @author Plushnikov Michail
 */
public class EqualsAndHashCodeProcessor extends AbstractClassProcessor {

  public static final String EQUALS_METHOD_NAME = "equals";
  public static final String HASH_CODE_METHOD_NAME = "hashCode";
  public static final String CAN_EQUAL_METHOD_NAME = "canEqual";

  public EqualsAndHashCodeProcessor() {
    super(EqualsAndHashCode.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRigthType(psiClass, builder);
    if (result) {
      validateExistingMethods(psiClass, builder);
    }
    final Collection<String> excludeProperty = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "exclude", String.class);
    final Collection<String> ofProperty = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "of", String.class);

    if (!excludeProperty.isEmpty() && !ofProperty.isEmpty()) {
      builder.addWarning("exclude and of are mutually exclusive; the 'exclude' parameter will be ignored",
          PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "exclude", null));
    } else {
      validateExcludeParam(psiClass, builder, psiAnnotation, excludeProperty);
    }
    validateOfParam(psiClass, builder, psiAnnotation, ofProperty);

    validateCallSuperParam(psiAnnotation, psiClass, builder, "equals/hashCode");
    validateCallSuperParamForObject(psiAnnotation, psiClass, builder);

    return result;
  }

  protected void validateCallSuperParamForObject(PsiAnnotation psiAnnotation, PsiClass psiClass, ProblemBuilder builder) {
    Boolean callSuperProperty = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "callSuper", Boolean.class);
    if (null != callSuperProperty && callSuperProperty && !PsiClassUtil.hasSuperClass(psiClass)) {
      builder.addError("Generating equals/hashCode with a supercall to java.lang.Object is pointless.",
          PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", "false"),
          PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "callSuper", null));
    }
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("@EqualsAndHashCode is only supported on a class type");
      result = false;
    }
    return result;
  }

  protected boolean validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;

    if (areMethodsAlreadyExists(psiClass)) {
      final boolean needsCanEqual = shouldGenerateCanEqual(psiClass);
      builder.addWarning(String.format("Not generating equals%s: A method with one of those names already exists. (Either all or none of these methods will be generated).",
          needsCanEqual ? ", hashCode and canEquals" : " and hashCode"));
      return false;
    }

    return result;
  }

  private boolean areMethodsAlreadyExists(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    return PsiMethodUtil.hasMethodByName(classMethods, EQUALS_METHOD_NAME, HASH_CODE_METHOD_NAME, CAN_EQUAL_METHOD_NAME);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.addAll(createEqualAndHashCode(psiClass, psiAnnotation, true));
  }

  protected Collection<PsiMethod> createEqualAndHashCode(@NotNull PsiClass psiClass, @NotNull PsiElement psiNavTargetElement, boolean tryGenerateCanEqual) {
    if (areMethodsAlreadyExists(psiClass)) {
      return Collections.emptyList();
    }
    //TODO
    /*
    PsiField[] equalsFields = PsiField.EMPTY_ARRAY;
    PsiField[] hashCodeFields = PsiField.EMPTY_ARRAY;
    PsiField[] nonNullFields = PsiField.EMPTY_ARRAY;
    GenerateEqualsHelper helper = new GenerateEqualsHelper(psiClass.getProject(), psiClass, equalsFields, hashCodeFields, nonNullFields, true);
    Collection<PsiMethod> generatedMethods = helper.generateMembers();
    */

    Collection<PsiMethod> result = new ArrayList<PsiMethod>(3);
    result.add(createEqualsMethod(psiClass, psiNavTargetElement));
    result.add(createHashCodeMethod(psiClass, psiNavTargetElement));

    final boolean shouldGenerateCanEqual = tryGenerateCanEqual && shouldGenerateCanEqual(psiClass);
    if (shouldGenerateCanEqual) {
      result.add(createCanEqualMethod(psiClass, psiNavTargetElement));
    }

    Collection<PsiField> equalsAndHashCodeFields = PsiFieldUtil.filterFieldsByModifiers(psiClass.getFields(), PsiModifier.STATIC, PsiModifier.TRANSIENT);
    UserMapKeys.addReadUsageFor(equalsAndHashCodeFields);

    return result;
  }

  private boolean shouldGenerateCanEqual(@NotNull PsiClass psiClass) {
    boolean isFinal = psiClass.hasModifierProperty(PsiModifier.FINAL);
    boolean isNotDirectDescendantOfObject = PsiClassUtil.hasSuperClass(psiClass);

    return !isFinal || isNotDirectDescendantOfObject;
  }

  @NotNull
  private PsiMethod createEqualsMethod(@NotNull PsiClass psiClass, @NotNull PsiElement psiNavTargetElement) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = String.format("return true;");

    return new LombokLightMethodBuilder(psiManager, EQUALS_METHOD_NAME)
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.BOOLEAN)
        .withContainingClass(psiClass)
        .withNavigationElement(psiNavTargetElement)
        .withParameter("other", PsiType.getJavaLangObject(psiManager, GlobalSearchScope.allScope(psiClass.getProject())))
        .withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));
  }

  @NotNull
  private PsiMethod createHashCodeMethod(@NotNull PsiClass psiClass, @NotNull PsiElement psiNavTargetElement) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = String.format("return 0;");

    return new LombokLightMethodBuilder(psiManager, HASH_CODE_METHOD_NAME)
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.INT)
        .withContainingClass(psiClass)
        .withNavigationElement(psiNavTargetElement)
        .withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));
  }

  @NotNull
  private PsiMethod createCanEqualMethod(@NotNull PsiClass psiClass, @NotNull PsiElement psiNavTargetElement) {
    final PsiManager psiManager = psiClass.getManager();

    final String blockText = String.format("return other instanceof %s;", psiClass.getName());

    return new LombokLightMethodBuilder(psiManager, CAN_EQUAL_METHOD_NAME)
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.BOOLEAN)
        .withContainingClass(psiClass)
        .withNavigationElement(psiNavTargetElement)
        .withParameter("other", PsiType.getJavaLangObject(psiManager, GlobalSearchScope.allScope(psiClass.getProject())))
        .withBody(PsiMethodUtil.createCodeBlockFromText(blockText, psiClass));
  }
}
