package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.util.PsiElementUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Delegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Plushnikov Michail
 */
public class DelegateFieldProcessor extends AbstractLombokFieldProcessor {

  public static final String CLASS_NAME = Delegate.class.getName();

  public DelegateFieldProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final PsiClass psiClass = psiField.getContainingClass();
    final Project project = psiField.getProject();
    final PsiManager manager = psiField.getContainingFile().getManager();

    final PsiType psiType = psiField.getType();

    final PsiClassType objectType = PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(project));
    final PsiClass objectClass = objectType.resolve();

    PsiClassType psiClassType = (PsiClassType) psiType;
    PsiClassType.ClassResolveResult resolveResult = psiClassType.resolveGenerics();
    PsiSubstitutor fieldClassSubstitutor = resolveResult.getSubstitutor();

    PsiClass fieldClass = resolveResult.getElement();
    if (null != fieldClass && null != objectClass) {
      final Collection<PsiMethod> methodsToDelegate = collectAllMethods(fieldClass, objectClass);

      if (!methodsToDelegate.isEmpty()) {
        for (PsiMethod psiMethod : methodsToDelegate) {
          target.add((Psi) generateDelegateMethod(psiClass, psiMethod, fieldClassSubstitutor));
        }
        UserMapKeys.addGeneralUsageFor(psiField);
      }
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final PsiClass psiClass = psiField.getContainingClass();
    if (null == psiClass) {
      result = false;
    }

    final PsiType psiType = psiField.getType();
    if (!(psiType instanceof PsiClassType)) {
      result = false;
    }
    //Error: @Delegate can only use concrete class types, not wildcards, arrays, type variables, or primitives.
    return result;
  }

  private Collection<PsiMethod> collectAllMethods(@NotNull PsiClass rootClass, @NotNull PsiClass objectClass) {
    final PsiMethod[] objectClassMethods = objectClass.getMethods();

    Collection<PsiMethod> result = new ArrayList<PsiMethod>();
    // take all methods
    Collections.addAll(result, rootClass.getAllMethods());

    final Iterator<PsiMethod> methodIterator = result.iterator();
    while (methodIterator.hasNext()) {
      PsiMethod psiMethod = methodIterator.next();

      boolean removeMethod = psiMethod.isConstructor();
      if (!removeMethod) {
        final PsiModifierList modifierList = psiMethod.getModifierList();
        removeMethod = !modifierList.hasModifierProperty(PsiModifier.PUBLIC) || modifierList.hasModifierProperty(PsiModifier.STATIC);
      }
      if (!removeMethod) {
        for (PsiMethod objMethod : objectClassMethods) {
          removeMethod = PsiElementUtil.methodMatches(psiMethod, objMethod.getReturnTypeNoResolve(), objMethod.getName(), objMethod.getParameterList());
          if (removeMethod) {
            break;
          }
        }
      }

      if (removeMethod) {
        methodIterator.remove();
      }
    }

    return result;
  }

  @NotNull
  private PsiMethod generateDelegateMethod(@NotNull PsiClass psiClass, @NotNull PsiMethod psiMethod, @Nullable PsiSubstitutor psiSubstitutor) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(PsiModifier.PUBLIC);
      builder.append(' ');
      final PsiType returnType = null == psiSubstitutor ? psiMethod.getReturnType() : psiSubstitutor.substitute(psiMethod.getReturnType());
      builder.append(null == returnType ? "" : returnType.getCanonicalText());
      builder.append(' ');
      builder.append(psiMethod.getName());
      builder.append('(');

      PsiParameterList parameterList = psiMethod.getParameterList();
      if (parameterList.getParametersCount() > 0) {
        for (PsiParameter psiParameter : parameterList.getParameters()) {
          final PsiType psiParameterType = null == psiSubstitutor ? psiParameter.getType() : psiSubstitutor.substitute(psiParameter.getType());
          builder.append(psiParameterType.getCanonicalText()).append(' ').append(psiParameter.getName()).append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
      }
      builder.append(')');
      builder.append("{ }");

      return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiMethod);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
