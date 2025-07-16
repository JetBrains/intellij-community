package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.psi.LombokDelegateMethod;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Handler for Delegate annotation processing, for fields and for methods
 */
public final class DelegateHandler {

  private static final String TYPES_PARAMETER = "types";
  private static final String EXCLUDES_PARAMETER = "excludes";

  public static boolean validate(@NotNull PsiModifierListOwner psiModifierListOwner,
                                 @NotNull PsiType delegateTargetType,
                                 @NotNull PsiAnnotation psiAnnotation,
                                 @NotNull ProblemSink problemSink) {
    boolean result = true;

    if (psiModifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
      problemSink.addErrorMessage("inspection.message.delegate.legal.only.on.instance.fields");
      result = false;
    }

    final Collection<PsiType> types = collectDelegateTypes(psiAnnotation, delegateTargetType);
    result &= validateTypes(types, problemSink);
    result &= validateTypesMethodsExistsInDelegateTargetType(types, delegateTargetType, problemSink);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);
    result &= validateTypes(excludes, problemSink);
    result &= validateTypesMethodsExistsInDelegateTargetType(excludes, delegateTargetType, problemSink);

    return result;
  }

  private static boolean validateTypesMethodsExistsInDelegateTargetType(@NotNull Collection<PsiType> types,
                                                                        @NotNull PsiType delegateTargetType,
                                                                        @NotNull ProblemSink sink) {
    boolean result = true;

    final Set<PsiType> typesToCheck = new HashSet<>(types);
    typesToCheck.remove(delegateTargetType);

    if (!typesToCheck.isEmpty()) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(delegateTargetType);
      final PsiClass psiDelegateTargetClass = resolveResult.getElement();
      if (null != psiDelegateTargetClass) {
        final Collection<MethodSignatureBackedByPsiMethod> delegateTargetSignatures =
          ContainerUtil.map(psiDelegateTargetClass.getVisibleSignatures(),
                            signature -> MethodSignatureBackedByPsiMethod.create(signature.getMethod(), resolveResult.getSubstitutor()));

        final Collection<HierarchicalMethodSignature> invalidMethodSignature = new ArrayList<>();
        for (PsiType psiType : typesToCheck) {
          final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
          if (null != psiClass) {
            final Collection<HierarchicalMethodSignature> methodSignatures = psiClass.getVisibleSignatures();

            for (HierarchicalMethodSignature methodSignature : methodSignatures) {
              final MethodSignatureBackedByPsiMethod matchingSignature =
                ContainerUtil.find(delegateTargetSignatures,
                                   signature -> MethodSignatureUtil.areSignaturesErasureEqual(signature, methodSignature));
              if (matchingSignature == null) {
                invalidMethodSignature.add(methodSignature);
              }
            }
          }
        }

        if (!invalidMethodSignature.isEmpty()) {
          final String invalidMethodNames =
            invalidMethodSignature.stream().map(MethodSignatureBackedByPsiMethod::getName).collect(NlsMessages.joiningAnd());

          @SuppressWarnings("unchecked")
          final Supplier<LocalQuickFix>[] fixes = invalidMethodSignature.stream().map(MethodSignatureBackedByPsiMethod::getMethod)
            .map((method) -> ((Supplier<LocalQuickFix>)() -> LocalQuickFix.from(
              new DeleteElementFix(method, CommonQuickFixBundle.message("fix.remove.title.x", JavaElementKind.METHOD.object(), method.getName())))))
            .toArray(Supplier[]::new);

          sink.addWarningMessage("inspection.message.delegate.unknown.type.method", invalidMethodSignature.size(), invalidMethodNames).withLocalQuickFixes(fixes);
        }
      }
    }

    return result;
  }

  private static Collection<PsiType> collectDelegateTypes(PsiAnnotation psiAnnotation, PsiType psiType) {
    Collection<PsiType> types = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, TYPES_PARAMETER, PsiType.class, List.of());
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
    return PsiAnnotationUtil.getAnnotationValues(psiAnnotation, EXCLUDES_PARAMETER, PsiType.class, List.of());
  }

  public static <T extends PsiMember & PsiNamedElement> void generateElements(@NotNull T psiElement,
                                                                              @NotNull PsiType delegateTargetType,
                                                                              @NotNull PsiAnnotation psiAnnotation,
                                                                              @NotNull List<? super PsiElement> target) {
    if (!(psiElement.getContainingClass() instanceof PsiExtensibleClass containingPsiClass)) {
      return;
    }

    final Collection<PsiType> includes = collectDelegateTypes(psiAnnotation, delegateTargetType);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods = new ArrayList<>();
    for (PsiType psiType : includes) {
      addMethodsOfType(psiType, includesMethods);
    }

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods = new ArrayList<>();
    for (PsiType psiType : excludes) {
      addMethodsOfType(psiType, excludeMethods);
    }

    // Add all already implemented methods to exclude list (includes methods from java.lang.Object too)
    collectAllOwnMethods(containingPsiClass, excludeMethods);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> methodsToDelegate = findMethodsToDelegate(includesMethods, excludeMethods);
    for (Pair<PsiMethod, PsiSubstitutor> pair : methodsToDelegate) {
      target.add(generateDelegateMethod(containingPsiClass, psiElement, psiAnnotation, pair.getFirst(), pair.getSecond()));
    }
  }

  private static void addMethodsOfType(PsiType psiType, Collection<Pair<PsiMethod, PsiSubstitutor>> results) {
    if (!(psiType instanceof PsiClassType classType)) return;
    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    if (null != psiClass) {
      final PsiSubstitutor classResolveResultSubstitutor = classResolveResult.getSubstitutor();
      for (PsiMethod psiMethod : psiClass.getAllMethods()) {
        if (isAcceptableMethod(psiMethod)) {
          final PsiClass containingClass = psiMethod.getContainingClass();
          if (containingClass != null) {
            final PsiSubstitutor psiSubstitutor =
              TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, classResolveResultSubstitutor);
            if (!isAlreadyPresent(psiMethod, psiSubstitutor, results)) {
              results.add(Pair.pair(psiMethod, psiSubstitutor));
            }
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

  private static @NotNull <T extends PsiModifierListOwner & PsiNamedElement> PsiMethod generateDelegateMethod(@NotNull PsiClass psiClass,
                                                                                                              @NotNull T psiElement,
                                                                                                              @NotNull PsiAnnotation psiAnnotation,
                                                                                                              @NotNull PsiMethod psiMethod,
                                                                                                              @NotNull PsiSubstitutor psiSubstitutor) {
    final PsiType returnType = psiSubstitutor.substitute(psiMethod.getReturnType());

    final LombokLightMethodBuilder methodBuilder = new LombokDelegateMethod(psiMethod)
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

  private static @NotNull <T extends PsiModifierListOwner & PsiNamedElement> String createCodeBlockText(@NotNull T psiElement,
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

    PsiParameter[] firstMethodParameters = firstMethodParameterList.getParameters();
    PsiParameter[] secondMethodParameters = secondMethodParameterList.getParameters();
    for (int i = 0; i < firstMethodParameters.length; i++) {
      final PsiType firstMethodParameterListParameterType = firstSubstitutor.substitute(firstMethodParameters[i].getType());
      final PsiType secondMethodParameterListParameterType = secondSubstitutor.substitute(secondMethodParameters[i].getType());

      final PsiType firstParameterType = TypeConversionUtil.erasure(firstMethodParameterListParameterType, firstSubstitutor);
      final PsiType secondParameterType = TypeConversionUtil.erasure(secondMethodParameterListParameterType, secondSubstitutor);

      if (!PsiElementUtil.typesAreEquivalent(firstParameterType, secondParameterType)) {
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
