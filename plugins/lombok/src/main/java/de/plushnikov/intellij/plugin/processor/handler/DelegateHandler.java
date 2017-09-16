package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.ShouldGenerateFullCodeBlock;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import lombok.Delegate;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Handler for Delegate annotation processing, for fields and for methods
 */
public class DelegateHandler {

  public DelegateHandler() {
  }

  public boolean validate(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull PsiType psiType, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder builder) {
    boolean result = true;

    if (psiModifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
      builder.addError("@Delegate is legal only on instance fields or no-argument instance methods.");
      result = false;
    }

    final Collection<PsiType> types = collectDelegateTypes(psiAnnotation, psiType);
    result &= validateTypes(types, builder);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);
    result &= validateTypes(excludes, builder);

    return result;
  }

  private Collection<PsiType> collectDelegateTypes(PsiAnnotation psiAnnotation, PsiType psiType) {
    Collection<PsiType> types = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "types", PsiType.class);
    if (types.isEmpty()) {
      types = Collections.singletonList(psiType);
    }
    return types;
  }

  private boolean validateTypes(Collection<PsiType> psiTypes, ProblemBuilder builder) {
    boolean result = true;
    for (PsiType psiType : psiTypes) {
      if (!checkConcreteClass(psiType)) {
        builder.addError("@Delegate can only use concrete class types, not wildcards, arrays, type variables, or primitives. '%s' is wrong class type",
          psiType.getCanonicalText());
        result = false;
      } else {
        result &= validateRecursion(psiType, builder);
      }
    }
    return result;
  }

  private boolean validateRecursion(PsiType psiType, ProblemBuilder builder) {
    final PsiClass psiClass = PsiTypesUtil.getPsiClass(psiType);
    if (null != psiClass) {
      final DelegateAnnotationElementVisitor delegateAnnotationElementVisitor = new DelegateAnnotationElementVisitor(psiType, builder);
      psiClass.acceptChildren(delegateAnnotationElementVisitor);
      return delegateAnnotationElementVisitor.isValid();
    }
    return true;
  }


  private boolean checkConcreteClass(@NotNull PsiType psiType) {
    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType) psiType).resolve();
      return !(psiClass instanceof PsiTypeParameter);
    }
    return false;
  }

  private Collection<PsiType> collectExcludeTypes(PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "excludes", PsiType.class);
  }

  public <T extends PsiMember & PsiNamedElement> void generateElements(@NotNull T psiElement, @NotNull PsiType psiElementType, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Project project = psiElement.getProject();
    final PsiManager manager = psiElement.getContainingFile().getManager();

    final Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods = new HashSet<Pair<PsiMethod, PsiSubstitutor>>();

    final Collection<PsiType> types = collectDelegateTypes(psiAnnotation, psiElementType);
    addMethodsOfTypes(types, includesMethods);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods = new HashSet<Pair<PsiMethod, PsiSubstitutor>>();
    PsiClassType javaLangObjectType = PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(project));
    addMethodsOfType(javaLangObjectType, excludeMethods);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);
    addMethodsOfTypes(excludes, excludeMethods);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> methodsToDelegate = findMethodsToDelegate(includesMethods, excludeMethods);
    if (!methodsToDelegate.isEmpty()) {
      final PsiClass psiClass = psiElement.getContainingClass();
      if (null != psiClass) {
        for (Pair<PsiMethod, PsiSubstitutor> pair : methodsToDelegate) {
          target.add(generateDelegateMethod(psiClass, psiElement, psiAnnotation, pair.getFirst(), pair.getSecond()));
        }
      }
    }
  }


  private void addMethodsOfTypes(Collection<PsiType> types, Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods) {
    for (PsiType type : types) {
      addMethodsOfType(type, includesMethods);
    }
  }

  private void addMethodsOfType(PsiType psiType, Collection<Pair<PsiMethod, PsiSubstitutor>> allMethods) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(psiType);

    final PsiClass psiClass = resolveResult.getElement();
    if (null != psiClass) {
      collectAllMethods(allMethods, psiClass, resolveResult.getSubstitutor());
    }
  }

  private void collectAllMethods(Collection<Pair<PsiMethod, PsiSubstitutor>> allMethods, @NotNull PsiClass psiStartClass, @NotNull PsiSubstitutor classSubstitutor) {
    PsiClass psiClass = psiStartClass;
    while (null != psiClass) {
      PsiMethod[] psiMethods = psiClass.getMethods();
      for (PsiMethod psiMethod : psiMethods) {
        if (!psiMethod.isConstructor() && psiMethod.hasModifierProperty(PsiModifier.PUBLIC) && !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {

          Pair<PsiMethod, PsiSubstitutor> newMethodSubstitutorPair = new Pair<PsiMethod, PsiSubstitutor>(psiMethod, classSubstitutor);

          boolean acceptMethod = true;
          for (Pair<PsiMethod, PsiSubstitutor> uniquePair : allMethods) {
            if (PsiElementUtil.methodMatches(newMethodSubstitutorPair, uniquePair)) {
              acceptMethod = false;
              break;
            }
          }
          if (acceptMethod) {
            allMethods.add(newMethodSubstitutorPair);
          }
        }
      }

      for (PsiClass interfaceClass : psiClass.getInterfaces()) {
        classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(interfaceClass, psiClass, classSubstitutor);

        collectAllMethods(allMethods, interfaceClass, classSubstitutor);
      }

      psiClass = psiClass.getSuperClass();
    }
  }

  private Collection<Pair<PsiMethod, PsiSubstitutor>> findMethodsToDelegate(Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods, Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods) {
    final Collection<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
    for (Pair<PsiMethod, PsiSubstitutor> includesMethodPair : includesMethods) {
      boolean acceptMethod = true;
      for (Pair<PsiMethod, PsiSubstitutor> excludeMethodPair : excludeMethods) {
        if (PsiElementUtil.methodMatches(includesMethodPair, excludeMethodPair)) {
          acceptMethod = false;
          break;
        }
      }
      if (acceptMethod) {
        result.add(includesMethodPair);
      }
    }
    return result;
  }

  @NotNull
  private <T extends PsiModifierListOwner & PsiNamedElement> PsiMethod generateDelegateMethod(@NotNull PsiClass psiClass, @NotNull T psiElement, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull PsiSubstitutor psiSubstitutor) {
    final PsiType returnType = psiSubstitutor.substitute(psiMethod.getReturnType());

    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(psiClass.getManager(), psiMethod.getName())
      .withModifier(PsiModifier.PUBLIC)
      .withMethodReturnType(returnType)
      .withContainingClass(psiClass)
      //Have to go to original method, or some refactoring action will not work (like extract parameter oder change signature)
      .withNavigationElement(psiMethod);

    for (PsiTypeParameter typeParameter : psiMethod.getTypeParameters()) {
      methodBuilder.withTypeParameter(typeParameter);
    }

    final PsiReferenceList throwsList = psiMethod.getThrowsList();
    for (PsiClassType psiClassType : throwsList.getReferencedTypes()) {
      methodBuilder.withException(psiClassType);
    }

    final PsiParameterList parameterList = psiMethod.getParameterList();

    final PsiParameter[] psiParameters = parameterList.getParameters();
    for (int parameterIndex = 0; parameterIndex < psiParameters.length; parameterIndex++) {
      final PsiParameter psiParameter = psiParameters[parameterIndex];
      final PsiType psiParameterType = psiSubstitutor.substitute(psiParameter.getType());
      final String generatedParameterName = StringUtils.defaultIfEmpty(psiParameter.getName(), "p" + parameterIndex);
      methodBuilder.withParameter(generatedParameterName, psiParameterType);
    }

    methodBuilder.withBody(createCodeBlock(psiClass, psiElement, psiMethod, returnType, psiParameters));

    return methodBuilder;
  }

  @NotNull
  private <T extends PsiModifierListOwner & PsiNamedElement> PsiCodeBlock createCodeBlock(@NotNull PsiClass psiClass, @NotNull T psiElement, @NotNull PsiMethod psiMethod, @NotNull PsiType returnType, @NotNull PsiParameter[] psiParameters) {
    final String blockText;
    if (isShouldGenerateFullBodyBlock()) {
      final StringBuilder paramString = new StringBuilder();

      for (int parameterIndex = 0; parameterIndex < psiParameters.length; parameterIndex++) {
        final PsiParameter psiParameter = psiParameters[parameterIndex];
        final String generatedParameterName = StringUtils.defaultIfEmpty(psiParameter.getName(), "p" + parameterIndex);
        paramString.append(generatedParameterName).append(',');
      }

      if (paramString.length() > 0) {
        paramString.deleteCharAt(paramString.length() - 1);
      }

      final boolean isMethodCall = psiElement instanceof PsiMethod;
      blockText = String.format("%sthis.%s%s.%s(%s);",
        PsiType.VOID.equals(returnType) ? "" : "return ",
        psiElement.getName(),
        isMethodCall ? "()" : "",
        psiMethod.getName(),
        paramString.toString());
    } else {
      blockText = "return " + PsiTypeUtil.getReturnValueOfType(returnType) + ";";
    }
    return PsiMethodUtil.createCodeBlockFromText(blockText, psiClass);
  }

  private boolean isShouldGenerateFullBodyBlock() {
    return ShouldGenerateFullCodeBlock.getInstance().isStateActive();
  }

  private static class DelegateAnnotationElementVisitor extends JavaElementVisitor {
    private final PsiType psiType;
    private final ProblemBuilder builder;
    private boolean valid;

    DelegateAnnotationElementVisitor(PsiType psiType, ProblemBuilder builder) {
      this.psiType = psiType;
      this.builder = builder;
      this.valid = true;
    }

    @Override
    public void visitField(PsiField psiField) {
      checkModifierListOwner(psiField);
    }

    @Override
    public void visitMethod(PsiMethod psiMethod) {
      checkModifierListOwner(psiMethod);
    }

    private void checkModifierListOwner(PsiModifierListOwner modifierListOwner) {
      if (PsiAnnotationSearchUtil.isAnnotatedWith(modifierListOwner, Delegate.class, lombok.experimental.Delegate.class)) {
        builder.addError("@Delegate does not support recursion (delegating to a type that itself has @Delegate members). " +
          "Member \"%s\" is @Delegate in type \"%s\"", ((PsiMember) modifierListOwner).getName(), psiType.getPresentableText());
        valid = false;
      }
    }

    public boolean isValid() {
      return valid;
    }
  }
}
