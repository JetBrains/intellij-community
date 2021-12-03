package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.AccessModifier;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Plushnikov Michail
 */
public final class PsiClassUtil {

  /**
   * Workaround to get all of original Methods of the psiClass, without calling PsiAugmentProvider infinitely
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern methods of the class
   */
  @NotNull
  public static Collection<PsiMethod> collectClassMethodsIntern(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiExtensibleClass) {
      if (psiClass.isRecord()) {
        return collectRecordMethods((PsiExtensibleClass) psiClass);
      }

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
    if (psiClass.isRecord()) {
      return collectRecordFields(psiClass);
    } else if (psiClass instanceof PsiExtensibleClass) {
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

  private static Collection<PsiField> collectRecordFields(@NotNull PsiClass psiClass) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
    Set<PsiField> fields = Arrays.stream(psiClass.getRecordComponents())
      .filter(c -> c.getName() != null && c.getTypeElement() != null)
      .map(c -> {
        String type = c.getTypeElement().getText();
        if (type.endsWith("...")) {
          type = type.substring(0, type.length() - 3) + "[]";
        }
        PsiField field = factory.createFieldFromText(String.format("private final %s %s;", type, c.getName()), psiClass);
        return new LightRecordField(psiClass.getManager(), field, psiClass, c);
      })
      .collect(Collectors.toSet());

    fields.addAll(((PsiExtensibleClass) psiClass).getOwnFields());
    return fields;
  }

  private static Collection<PsiMethod> collectRecordMethods(@NotNull PsiExtensibleClass psiClass) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
    List<PsiMethod> ownMethods = psiClass.getOwnMethods();
    PsiRecordComponent[] components = psiClass.getRecordComponents();

    // Getters
    Set<PsiMethod> methods = Arrays.stream(components)
      .filter(c -> c.getName() != null && c.getTypeElement() != null)
      .filter(c -> !ContainerUtil.exists(ownMethods, m -> m.getName().equals(c.getName()) && m.getParameterList().isEmpty()))
      .map(c -> {
        String type = c.getTypeElement().getText();
        if (type.endsWith("...")) {
          type = type.substring(0, type.length() - 3) + "[]";
        }
        PsiMethod method = factory.createMethodFromText("public " + type + " " + c.getName() + "(){ return " + c.getName() + "; }", c.getContainingClass());
        return new LightRecordMethod(psiClass.getManager(), method, psiClass, c);
      })
      .collect(Collectors.toSet());

    // Canonical constructor
    if (!ContainerUtil.exists(ownMethods, m -> JavaPsiRecordUtil.isCompactConstructor(m) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(m))) {
      String constructorText = psiClass.getName() + psiClass.getRecordHeader().getText() + "{"
                  + StringUtil.join(components, c -> "this." + c.getName() + "=" + c.getName() + ";", "\n")
                  + "}";
      PsiMethod constructor = factory.createMethodFromText(constructorText, psiClass);

      AccessModifier modifier = psiClass.getModifierList() == null ? AccessModifier.PUBLIC : AccessModifier.fromModifierList(psiClass.getModifierList());
      constructor.getModifierList().setModifierProperty(modifier.toPsiModifier(), true);
      methods.add(new LightRecordCanonicalConstructor(constructor, psiClass));
    }
    return methods;
  }

  @NotNull
  public static Collection<PsiMethod> collectClassConstructorIntern(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> psiMethods = collectClassMethodsIntern(psiClass);
    return ContainerUtil.filter(psiMethods, PsiMethod::isConstructor);
  }

  @NotNull
  public static Collection<PsiMethod> collectClassStaticMethodsIntern(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> psiMethods = collectClassMethodsIntern(psiClass);
    return ContainerUtil.filter(psiMethods, psiMethod -> psiMethod.hasModifierProperty(PsiModifier.STATIC));
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
  public static PsiClassType getWildcardClassType(@NotNull PsiClass psiClass) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    if (psiClass.hasTypeParameters()) {
      PsiType[] wildcardTypes = new PsiType[psiClass.getTypeParameters().length];
      Arrays.fill(wildcardTypes, PsiWildcardType.createUnbounded(psiClass.getManager()));
      return elementFactory.createType(psiClass, wildcardTypes);
    }
    return elementFactory.createType(psiClass);
  }

  /**
   * Creates a PsiType for a PsiClass enriched with generic substitution information if available
   */
  @NotNull
  public static PsiClassType getTypeWithGenerics(@NotNull PsiClass psiClass) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiType[] psiTypes = Stream.of(psiClass.getTypeParameters()).map(factory::createType).toArray(PsiType[]::new);
    if (psiTypes.length > 0)
      return factory.createType(psiClass, psiTypes);
    else
      return factory.createType(psiClass);
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
