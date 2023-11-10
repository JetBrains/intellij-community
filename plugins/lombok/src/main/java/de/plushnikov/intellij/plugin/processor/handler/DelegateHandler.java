package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for Delegate annotation processing, for fields and for methods
 */
public final class DelegateHandler {

  public static boolean validate(@NotNull PsiModifierListOwner psiModifierListOwner,
                                 @NotNull PsiType psiType,
                                 @NotNull PsiAnnotation psiAnnotation,
                                 @NotNull ProblemSink problemSink) {
    boolean result = true;

    if (psiModifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
      problemSink.addErrorMessage("inspection.message.delegate.legal.only.on.instance.fields");
      result = false;
    }

    final Collection<PsiType> types = collectDelegateTypes(psiAnnotation, psiType);
    result &= validateTypes(types, problemSink);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);
    result &= validateTypes(excludes, problemSink);

    return result;
  }

  private static Collection<PsiType> collectDelegateTypes(PsiAnnotation psiAnnotation, PsiType psiType) {
    Collection<PsiType> types = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "types", PsiType.class);
    if (types.isEmpty()) {
      types = Collections.singletonList(psiType);
    }
    return types;
  }

  private static boolean validateTypes(Collection<PsiType> psiTypes, ProblemSink problemSink) {
    boolean result = true;
    for (PsiType psiType : psiTypes) {
      if (!checkConcreteClass(psiType)) {
        problemSink.addErrorMessage("inspection.message.delegate.can.only.use.concrete.class.types", psiType.getCanonicalText());
        result = false;
      }
      else {
        result &= validateRecursion(psiType, problemSink);
      }
    }
    return result;
  }

  private static boolean validateRecursion(PsiType psiType, ProblemSink problemSink) {
    final PsiClass psiClass = PsiTypesUtil.getPsiClass(psiType);
    if (null != psiClass) {
      final DelegateAnnotationElementVisitor delegateAnnotationElementVisitor = new DelegateAnnotationElementVisitor(psiType, problemSink);
      psiClass.acceptChildren(delegateAnnotationElementVisitor);
      return delegateAnnotationElementVisitor.isValid();
    }
    return true;
  }


  private static boolean checkConcreteClass(@NotNull PsiType psiType) {
    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)psiType).resolve();
      return !(psiClass instanceof PsiTypeParameter);
    }
    return false;
  }

  private static Collection<PsiType> collectExcludeTypes(PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "excludes", PsiType.class);
  }

  public static <T extends PsiMember & PsiNamedElement> void generateElements(@NotNull T psiElement,
                                                                              @NotNull PsiType psiElementType,
                                                                              @NotNull PsiAnnotation psiAnnotation,
                                                                              @NotNull List<? super PsiElement> target) {
    if (!(psiElement.getContainingClass() instanceof PsiExtensibleClass containingPsiClass)) {
      return;
    }

    final Collection<PsiType> includes = collectDelegateTypes(psiAnnotation, psiElementType);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods = new ArrayList<>();
    addMethodsOfTypes(includes, includesMethods);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods = new ArrayList<>();
    addMethodsOfTypes(excludes, excludeMethods);

    // Add all already implemented methods to exclude list (includes methods from java.lang.Object too)
    collectAllOwnMethods(containingPsiClass, excludeMethods);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> methodsToDelegate = findMethodsToDelegate(includesMethods, excludeMethods);
    for (Pair<PsiMethod, PsiSubstitutor> pair : methodsToDelegate) {
      target.add(generateDelegateMethod(containingPsiClass, psiElement, psiAnnotation, pair.getFirst(), pair.getSecond()));
    }
  }

  private static void addMethodsOfTypes(Collection<PsiType> types, Collection<Pair<PsiMethod, PsiSubstitutor>> results) {
    for (PsiType type : types) {
      addMethodsOfType(type, results);
    }
  }

  private static void addMethodsOfType(PsiType psiType, Collection<Pair<PsiMethod, PsiSubstitutor>> results) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(psiType);
    final PsiClass psiClass = resolveResult.getElement();
    if (null != psiClass && psiType instanceof PsiClassType psiClassType) {
      final PsiTypeParameter[] classTypeParameters = psiClass.getTypeParameters();
      final PsiType[] parameters = psiClassType.getParameters();

      PsiSubstitutor enrichedSubstitutor = PsiSubstitutor.EMPTY;
      if (classTypeParameters.length == parameters.length) {
        for (int i = 0; i < parameters.length; i++) {
          enrichedSubstitutor = enrichedSubstitutor.put(classTypeParameters[i], parameters[i]);
        }
      }

      for (Pair<PsiMethod, PsiSubstitutor> pair : psiClass.getAllMethodsAndTheirSubstitutors()) {
        final PsiMethod psiMethod = pair.getFirst();
        if (isAcceptableMethod(psiMethod)) {
          final PsiSubstitutor psiSubstitutor = pair.getSecond().putAll(enrichedSubstitutor);
          if (!isAlreadyPresent(psiMethod, psiSubstitutor, results)) {
            results.add(Pair.pair(psiMethod, psiSubstitutor));
          }
        }
      }
    }
  }

  private static void collectAllOwnMethods(@NotNull PsiExtensibleClass psiStartClass, Collection<Pair<PsiMethod, PsiSubstitutor>> results) {
    PsiExtensibleClass psiClass = psiStartClass;
    do {
      for (PsiMethod psiMethod : psiClass.getOwnMethods()) {
        if (isAcceptableMethod(psiMethod)) {
          if (!isAlreadyPresent(psiMethod, PsiSubstitutor.EMPTY, results)) {
            results.add(Pair.pair(psiMethod, PsiSubstitutor.EMPTY));
          }
        }
      }

      if (psiClass.getSuperClass() instanceof PsiExtensibleClass psiExtensibleSuperClass) {
        psiClass = psiExtensibleSuperClass;
      }
      else {
        psiClass = null;
      }
    }
    while (null != psiClass);
  }

  private static boolean isAcceptableMethod(PsiMethod psiMethod) {
    return !psiMethod.isConstructor() &&
           psiMethod.hasModifierProperty(PsiModifier.PUBLIC) &&
           !psiMethod.hasModifierProperty(PsiModifier.STATIC);
  }

  private static Collection<Pair<PsiMethod, PsiSubstitutor>> findMethodsToDelegate(Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods,
                                                                                   Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods) {
    final Collection<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<>();
    for (Pair<PsiMethod, PsiSubstitutor> includesMethodPair : includesMethods) {
      if (!isAlreadyPresent(includesMethodPair.getFirst(), includesMethodPair.getSecond(), excludeMethods)) {
        result.add(includesMethodPair);
      }
    }
    return result;
  }

  private static boolean isAlreadyPresent(PsiMethod psiMethod, PsiSubstitutor psiSubstitutor,
                                          Collection<Pair<PsiMethod, PsiSubstitutor>> searchedPairs) {
    boolean acceptMethod = true;
    for (Pair<PsiMethod, PsiSubstitutor> someMethodPair : searchedPairs) {
      if (methodMatches(psiMethod, psiSubstitutor, someMethodPair.getFirst(), someMethodPair.getSecond())) {
        acceptMethod = false;
        break;
      }
    }
    return !acceptMethod;
  }

  @NotNull
  private static <T extends PsiModifierListOwner & PsiNamedElement> PsiMethod generateDelegateMethod(@NotNull PsiClass psiClass,
                                                                                                     @NotNull T psiElement,
                                                                                                     @NotNull PsiAnnotation psiAnnotation,
                                                                                                     @NotNull PsiMethod psiMethod,
                                                                                                     @NotNull PsiSubstitutor psiSubstitutor) {
    final PsiType returnType = psiSubstitutor.substitute(psiMethod.getReturnType());

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiClass.getManager(), psiMethod.getName())
      .withModifier(PsiModifier.PUBLIC)
      .withMethodReturnType(returnType)
      .withContainingClass(psiClass)
      //Have to go to original method, or some refactoring action will not work (like extract parameter oder change signature)
      .withNavigationElement(psiMethod);

    for (PsiTypeParameter typeParameter : psiMethod.getTypeParameters()) {
      final LightTypeParameterBuilder lightTypeParameter =
        new LightTypeParameterBuilder(typeParameter.getName(), psiMethod, typeParameter.getIndex());
      for (PsiClassType extendsListType : typeParameter.getExtendsListTypes()) {
        lightTypeParameter.getExtendsList().addReference((PsiClassType)psiSubstitutor.substitute(extendsListType));
      }
      for (PsiClassType implementsListType : typeParameter.getImplementsListTypes()) {
        lightTypeParameter.getImplementsList().addReference((PsiClassType)psiSubstitutor.substitute(implementsListType));
      }
      methodBuilder.withTypeParameter(lightTypeParameter);
    }

    final PsiReferenceList throwsList = psiMethod.getThrowsList();
    for (PsiClassType psiClassType : throwsList.getReferencedTypes()) {
      methodBuilder.withException(psiClassType);
    }

    final PsiParameterList parameterList = psiMethod.getParameterList();
    final PsiParameter[] psiParameters = parameterList.getParameters();
    for (final PsiParameter psiParameter : psiParameters) {
      final PsiType psiParameterType = psiSubstitutor.substitute(psiParameter.getType());
      methodBuilder.withParameter(psiParameter.getName(), psiParameterType);
    }

    final String codeBlockText = createCodeBlockText(psiElement, psiMethod, returnType, psiParameters);
    methodBuilder.withBodyText(codeBlockText);

    return methodBuilder;
  }

  @NotNull
  private static <T extends PsiModifierListOwner & PsiNamedElement> String createCodeBlockText(@NotNull T psiElement,
                                                                                               @NotNull PsiMethod psiMethod,
                                                                                               @NotNull PsiType returnType,
                                                                                               PsiParameter @NotNull [] psiParameters) {
    final String paramString = Arrays.stream(psiParameters).map(PsiParameter::getName).collect(Collectors.joining(","));
    final boolean isMethodCall = psiElement instanceof PsiMethod;
    return String.format("%sthis.%s%s.%s(%s);",
                         PsiTypes.voidType().equals(returnType) ? "" : "return ",
                         psiElement.getName(),
                         isMethodCall ? "()" : "",
                         psiMethod.getName(),
                         paramString);
  }

  public static boolean methodMatches(@NotNull PsiMethod firstMethod, @NotNull PsiSubstitutor firstSubstitutor,
                                      @NotNull PsiMethod secondMethod, @NotNull PsiSubstitutor secondSubstitutor) {
    if (!firstMethod.getName().equals(secondMethod.getName())) {
      return false;
    }

    PsiParameterList firstMethodParameterList = firstMethod.getParameterList();
    PsiParameterList secondMethodParameterList = secondMethod.getParameterList();
    if (firstMethodParameterList.getParametersCount() != secondMethodParameterList.getParametersCount()) {
      return false;
    }

    PsiParameter[] firstMethodParameterListParameters = firstMethodParameterList.getParameters();
    PsiParameter[] secondMethodParameterListParameters = secondMethodParameterList.getParameters();
    for (int i = 0; i < firstMethodParameterListParameters.length; i++) {
      PsiType firstMethodParameterListParameterType = firstSubstitutor.substitute(firstMethodParameterListParameters[i].getType());
      PsiType secondMethodParameterListParameterType = secondSubstitutor.substitute(secondMethodParameterListParameters[i].getType());
      if (PsiTypes.nullType().equals(firstMethodParameterListParameterType)) {
        continue;
      }
      if (!PsiElementUtil.typesAreEquivalent(firstMethodParameterListParameterType, secondMethodParameterListParameterType)) {
        return false;
      }
    }

    return true;
  }

  private static class DelegateAnnotationElementVisitor extends JavaElementVisitor {
    private final PsiType psiType;
    private final ProblemSink builder;
    private boolean valid;

    DelegateAnnotationElementVisitor(PsiType psiType, ProblemSink builder) {
      this.psiType = psiType;
      this.builder = builder;
      this.valid = true;
    }

    @Override
    public void visitField(@NotNull PsiField psiField) {
      checkModifierListOwner(psiField);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod psiMethod) {
      checkModifierListOwner(psiMethod);
    }

    private void checkModifierListOwner(PsiModifierListOwner modifierListOwner) {
      if (PsiAnnotationSearchUtil.isAnnotatedWith(modifierListOwner, LombokClassNames.DELEGATE, LombokClassNames.EXPERIMENTAL_DELEGATE)) {
        builder.addErrorMessage("inspection.message.delegate.does.not.support.recursion.delegating",
                                ((PsiMember)modifierListOwner).getName(), psiType.getPresentableText());
        valid = false;
      }
    }

    public boolean isValid() {
      return valid;
    }
  }
}
