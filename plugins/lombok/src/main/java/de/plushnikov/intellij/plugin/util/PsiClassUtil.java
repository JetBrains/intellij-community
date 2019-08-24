package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Plushnikov Michail
 */
public class PsiClassUtil {

  /**
   * Workaround to get all of original Methods of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern methods of the class
   */
  @NotNull
  public static Collection<PsiMethod> collectClassMethodsIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      return new ArrayList<>(((PsiExtensibleClass) psiClass).getOwnMethods());
    } else {
      return filterPsiElements(psiClass, PsiMethod.class);
    }
  }

  /**
   * Workaround to get all of original Fields of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all of fields from
   * @return all intern fields of the class
   */
  @NotNull
  public static Collection<PsiField> collectClassFieldsIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      return ((PsiExtensibleClass) psiClass).getOwnFields();
    } else {
      return filterPsiElements(psiClass, PsiField.class);
    }
  }

  /**
   * Workaround to get all of original inner classes of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all inner classes from
   * @return all inner classes of the class
   */
  @NotNull
  public static Collection<PsiClass> collectInnerClassesIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      return ((PsiExtensibleClass) psiClass).getOwnInnerClasses();
    } else {
      return filterPsiElements(psiClass, PsiClass.class);
    }
  }

  @NotNull
  public static Collection<PsiMember> collectClassMemberIntern(@NotNull PsiClass psiClass) {
    return Arrays.stream(psiClass.getChildren()).filter(e -> e instanceof PsiField || e instanceof PsiMethod).map(PsiMember.class::cast).collect(Collectors.toList());
  }

  private static <T extends PsiElement> Collection<T> filterPsiElements(@NotNull PsiClass psiClass, @NotNull Class<T> desiredClass) {
    return Arrays.stream(psiClass.getChildren()).filter(desiredClass::isInstance).map(desiredClass::cast).collect(Collectors.toList());
  }

  @NotNull
  public static Collection<PsiMethod> collectClassConstructorIntern(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> psiMethods = collectClassMethodsIntern(psiClass);
    return psiMethods.stream().filter(PsiMethod::isConstructor).collect(Collectors.toList());
  }

  @NotNull
  public static Collection<PsiMethod> collectClassStaticMethodsIntern(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> psiMethods = collectClassMethodsIntern(psiClass);
    return psiMethods.stream().filter(psiMethod -> psiMethod.hasModifierProperty(PsiModifier.STATIC)).collect(Collectors.toList());
  }

  public static boolean hasSuperClass(@NotNull final PsiClass psiClass) {
    final PsiClass superClass = psiClass.getSuperClass();
    return (null != superClass && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())
      || !superTypesIsEmptyOrObjectOnly(psiClass));
  }

  private static boolean superTypesIsEmptyOrObjectOnly(@NotNull final PsiClass psiClass) {
    // It returns abstract classes, but also Object.
    final PsiClassType[] superTypes = psiClass.getSuperTypes();
    return superTypes.length != 1 || CommonClassNames.JAVA_LANG_OBJECT.equals(superTypes[0].getCanonicalText());
  }

  @NotNull
  public static PsiType getWildcardClassType(@NotNull PsiClass psiClass) {
    if (psiClass.hasTypeParameters()) {
      PsiType[] wildcardTypes = new PsiType[psiClass.getTypeParameters().length];
      Arrays.fill(wildcardTypes, PsiWildcardType.createUnbounded(psiClass.getManager()));
      return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, wildcardTypes);
    }
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  /**
   * Creates a PsiType for a PsiClass enriched with generic substitution information if available
   */
  @NotNull
  public static PsiType getTypeWithGenerics(@NotNull PsiClass psiClass) {
    PsiTypeParameter[] classTypeParameters = psiClass.getTypeParameters();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    if (classTypeParameters.length > 0) {
      Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<>();
      for (PsiTypeParameter typeParameter : classTypeParameters) {
        substitutionMap.put(typeParameter, factory.createType(typeParameter));
      }
      return factory.createType(psiClass, factory.createSubstitutor(substitutionMap));
    } else {
      return factory.createType(psiClass);
    }
  }

  /**
   * Workaround to get inner class of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to search for inner class
   * @return inner class if found
   */
  public static Optional<PsiClass> getInnerClassInternByName(@NotNull PsiClass psiClass, @NotNull String className) {
    Collection<PsiClass> innerClasses = collectInnerClassesIntern(psiClass);
    return innerClasses.stream().filter(innerClass -> className.equals(innerClass.getName())).findAny();
  }

  public static Collection<String> getNames(Collection<? extends PsiMember> psiMembers) {
    return psiMembers.stream().map(PsiMember::getName).collect(Collectors.toSet());
  }
}
