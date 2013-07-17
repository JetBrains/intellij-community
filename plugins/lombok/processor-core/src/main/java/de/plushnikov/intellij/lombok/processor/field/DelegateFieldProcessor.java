package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiElementUtil;
import lombok.Delegate;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Inspect and validate @Delegate lombok annotation on a field
 * Creates delegation methods for this field
 *
 * @author Plushnikov Michail
 */
public class DelegateFieldProcessor extends AbstractLombokFieldProcessor {

  public DelegateFieldProcessor() {
    super(Delegate.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final PsiClass psiClass = psiField.getContainingClass();
    if (null == psiClass) {
      result = false;
    }

    final Collection<PsiType> types = collectDelegateTypes(psiAnnotation, psiField);
    result &= validateTypes(types, builder);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);
    result &= validateTypes(excludes, builder);

    //TODO Error: delegation of methods that doesn't exists on type
    return result;
  }

  private boolean validateTypes(Collection<PsiType> excludes, ProblemBuilder builder) {
    boolean result = true;
    for (PsiType type : excludes) {
      if (!(type instanceof PsiClassType)) {
        builder.addError(String.format(
            "'@Delegate' can only use concrete class types, not wildcards, arrays, type variables, or primitives. '%s' is wrong class type",
            type.getCanonicalText()));
        result = false;
      }
    }
    return result;
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final PsiClass psiClass = psiField.getContainingClass();
    final Project project = psiField.getProject();
    final PsiManager manager = psiField.getContainingFile().getManager();

    final Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods = new HashSet<Pair<PsiMethod, PsiSubstitutor>>();

    final Collection<PsiType> types = collectDelegateTypes(psiAnnotation, psiField);
    addMethodsOfTypes(types, includesMethods);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods = new HashSet<Pair<PsiMethod, PsiSubstitutor>>();
    PsiClassType javaLangObjectType = PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(project));
    addMethodsOfType(javaLangObjectType, excludeMethods);

    final Collection<PsiType> excludes = collectExcludeTypes(psiAnnotation);
    addMethodsOfTypes(excludes, excludeMethods);

    final Collection<Pair<PsiMethod, PsiSubstitutor>> methodsToDelegate = findMethodsToDelegate(includesMethods, excludeMethods);
    if (!methodsToDelegate.isEmpty()) {
      for (Pair<PsiMethod, PsiSubstitutor> pair : methodsToDelegate) {
        target.add((Psi) generateDelegateMethod(psiClass, psiAnnotation, pair.getFirst(), pair.getSecond()));
      }
      UserMapKeys.addGeneralUsageFor(psiField);
    }
  }

  private Collection<PsiType> collectDelegateTypes(PsiAnnotation psiAnnotation, PsiField psiField) {
    Collection<PsiType> types = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "types", PsiType.class);
    if (types.isEmpty()) {
      final PsiType psiType = psiField.getType();
      types = Collections.singletonList(psiType);
    }
    return types;
  }

  private Collection<PsiType> collectExcludeTypes(PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "excludes", PsiType.class);
  }

  private void addMethodsOfTypes(Collection<PsiType> types, Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods) {
    for (PsiType type : types) {
      addMethodsOfType(type, includesMethods);
    }
  }

  private void addMethodsOfType(PsiType psiType, Collection<Pair<PsiMethod, PsiSubstitutor>> allMethods) {
    PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(psiType);
    if (null != classResolveResult) {
      PsiClass psiClass = classResolveResult.getElement();
      PsiSubstitutor classSubstitutor = classResolveResult.getSubstitutor();
      if (null != psiClass) {
        List<Pair<PsiMethod, PsiSubstitutor>> acceptedMethods = psiClass.getAllMethodsAndTheirSubstitutors();
        for (Pair<PsiMethod, PsiSubstitutor> pair : acceptedMethods) {
          PsiMethod psiMethod = pair.getFirst();
          if (!psiMethod.isConstructor() && psiMethod.hasModifierProperty(PsiModifier.PUBLIC) && !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            // replace Substitutor, one from pair seems to be wrong?
            allMethods.add(new Pair<PsiMethod, PsiSubstitutor>(psiMethod, classSubstitutor));
          }
        }
      }
    }
  }

  private void removeDuplicateMethods(Collection<Pair<PsiMethod, PsiSubstitutor>> allMethods) {
    if (allMethods.isEmpty()) {
      return;
    }

    Collection<Pair<PsiMethod, PsiSubstitutor>> processedMethods = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
    Iterator<Pair<PsiMethod, PsiSubstitutor>> iterator = allMethods.iterator();
    while (iterator.hasNext()) {
      Pair<PsiMethod, PsiSubstitutor> pair = iterator.next();
      boolean acceptMethod = true;
      for (Pair<PsiMethod, PsiSubstitutor> uniquePair : processedMethods) {
        if (PsiElementUtil.methodMatches(pair, uniquePair)) {
          acceptMethod = false;
          break;
        }
      }
      if (acceptMethod) {
        processedMethods.add(pair);
      } else {
        iterator.remove();
      }
    }
  }

  private Collection<Pair<PsiMethod, PsiSubstitutor>> findMethodsToDelegate(Collection<Pair<PsiMethod, PsiSubstitutor>> includesMethods, Collection<Pair<PsiMethod, PsiSubstitutor>> excludeMethods) {
    removeDuplicateMethods(includesMethods);
    removeDuplicateMethods(excludeMethods);

    if (excludeMethods.isEmpty()) {
      return includesMethods;
    }

    Collection<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
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
  private PsiMethod generateDelegateMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @Nullable PsiSubstitutor psiSubstitutor) {
    final PsiType returnType = null == psiSubstitutor ? psiMethod.getReturnType() : psiSubstitutor.substitute(psiMethod.getReturnType());

    LombokLightMethodBuilder methodBuilder = LombokPsiElementFactory.getInstance().
        createLightMethod(psiClass.getManager(), psiMethod.getName())
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(returnType)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);

    for (PsiTypeParameter typeParameter : psiMethod.getTypeParameters()) {
      methodBuilder.withTypeParameter(new LightTypeParameter(typeParameter));
    }

    final PsiParameterList parameterList = psiMethod.getParameterList();

    int parameterIndex = 0;
    for (PsiParameter psiParameter : parameterList.getParameters()) {
      final PsiType psiParameterType = null == psiSubstitutor ? psiParameter.getType() : psiSubstitutor.substitute(psiParameter.getType());
      String psiParameterName = psiParameter.getName();
      methodBuilder.withParameter(StringUtils.defaultIfEmpty(psiParameterName, "p" + parameterIndex), psiParameterType);
      parameterIndex++;
    }

    return methodBuilder;
  }
}
