package de.plushnikov.intellij.lombok.processor.field;

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
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.MethodUtils;
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

  public <Psi extends PsiElement> boolean process(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    boolean result = false;

    final PsiClass psiClass = psiField.getContainingClass();
    if (null == psiClass) {
      return result;
    }

    final Project project = psiField.getProject();
    final PsiManager manager = psiField.getContainingFile().getManager();

    final PsiType psiType = psiField.getType();
    if (psiType instanceof PsiClassType) {
      final PsiClassType objectType = PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(project));
      final PsiClass objectClass = objectType.resolve();

      PsiClassType psiClassType = (PsiClassType) psiType;
      PsiClassType.ClassResolveResult resolveResult = psiClassType.resolveGenerics();
      PsiSubstitutor fieldClassSubstitutor = resolveResult.getSubstitutor();

      PsiClass fieldClass = resolveResult.getElement();
      if (null != fieldClass && null != objectClass) {
        final Collection<PsiMethod> methodsToDelegate = collectAllMethods(fieldClass, objectClass);

        final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        for (PsiMethod psiMethod : methodsToDelegate) {
          LightMethod myLightMethod = generateDelegateMethod(psiClass, manager, elementFactory, psiMethod, fieldClassSubstitutor);
          target.add((Psi) myLightMethod);
        }

        result = !methodsToDelegate.isEmpty();
      }
    }
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
          removeMethod = MethodUtils.methodMatches(psiMethod, objMethod.getReturnTypeNoResolve(), objMethod.getName(), objMethod.getParameterList());
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
  private LightMethod generateDelegateMethod(@NotNull PsiClass psiClass, @NotNull PsiManager manager, @NotNull PsiElementFactory elementFactory, @NotNull PsiMethod psiMethod, @Nullable PsiSubstitutor psiSubstitutor) {
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

      PsiMethod delegateMethod = elementFactory.createMethodFromText(builder.toString(), psiClass);
      return prepareMethod(manager, delegateMethod, psiClass, psiMethod);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
