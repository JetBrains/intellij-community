package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.lombok.UserMapKeys;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class EqualsAndHashCodeProcessor extends AbstractLombokClassProcessor {

  private static final String CLASS_NAME = EqualsAndHashCode.class.getName();
  public static final String EQUALS_METHOD_NAME = "equals";
  public static final String HASH_CODE_METHOD_NAME = "hashCode";
  public static final String CAN_EQUAL_METHOD_NAME = "canEqual";

  public EqualsAndHashCodeProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    if (!hasMethodByName(classMethods, EQUALS_METHOD_NAME, HASH_CODE_METHOD_NAME)) {
      boolean shouldGenerateCanEqual = shouldGenerateCanEqual(psiClass);

      if (!shouldGenerateCanEqual || !hasMethodByName(classMethods, CAN_EQUAL_METHOD_NAME)) {
        Project project = psiClass.getProject();
        PsiManager manager = psiClass.getContainingFile().getManager();
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

        PsiMethod equalsMethod = createEqualsMethod(psiClass, elementFactory);
        target.add((Psi) prepareMethod(manager, equalsMethod, psiClass, psiAnnotation));

        PsiMethod hashcodeMethod = createHashCodeMethod(psiClass, elementFactory);
        target.add((Psi) prepareMethod(manager, hashcodeMethod, psiClass, psiAnnotation));

        if (shouldGenerateCanEqual) {
          PsiMethod canEqualsMethod = createCanEqualMethod(psiClass, elementFactory);
          target.add((Psi) prepareMethod(manager, canEqualsMethod, psiClass, psiAnnotation));
        }

        Collection<PsiField> equalsAndHashCodeFields = filterFieldsByModifiers(psiClass.getFields(), PsiModifier.STATIC, PsiModifier.TRANSIENT);
        UserMapKeys.addReadUsageFor(equalsAndHashCodeFields);
      }
    } else {
      //TODO create warning in code
      //Not generating methodName(): A method with that name already exists
    }
  }

  private boolean shouldGenerateCanEqual(@NotNull PsiClass psiClass) {
    boolean result = true;
    final PsiClass superClass = psiClass.getSuperClass();
    if (null == superClass && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      result = false;
    }
    if (null != superClass && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      final Project project = psiClass.getProject();
      final PsiManager manager = psiClass.getContainingFile().getManager();
      final PsiClassType javaLangObject = PsiType.getJavaLangObject(manager, GlobalSearchScope.projectScope(project));

      result = !superClass.equals(javaLangObject.resolve());
    }
    return result;
  }

  @NotNull
  private PsiMethod createEqualsMethod(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    return elementFactory.createMethodFromText(
        "@java.lang.Override public boolean " + EQUALS_METHOD_NAME + "(final java.lang.Object other) { return super.equals(other); }",
        psiClass);
  }

  @NotNull
  private PsiMethod createHashCodeMethod(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    return elementFactory.createMethodFromText(
        "@java.lang.Override public int " + HASH_CODE_METHOD_NAME + "() { return super.hashCode(); }",
        psiClass);
  }

  @NotNull
  private PsiMethod createCanEqualMethod(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    return elementFactory.createMethodFromText(
        "public boolean " + CAN_EQUAL_METHOD_NAME + "(final java.lang.Object other) { return other instanceof " + psiClass.getName() + "; }",
        psiClass);
  }
}
