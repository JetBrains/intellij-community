package de.plushnikov.intellij.plugin.processor;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ValProcessor extends AbstractProcessor {

  private static final String LOMBOK_VAL_FQN = "lombok.val";
  private static final String LOMBOK_VAL_SHORT_NAME = "val";

  private final static ThreadLocal<Set<PsiExpression>> recursionBreaker = new ThreadLocal<Set<PsiExpression>>() {
    @Override
    protected Set<PsiExpression> initialValue() {
      return new HashSet<PsiExpression>();
    }
  };

  public ValProcessor() {
    super(PsiElement.class, val.class);
  }

  public boolean isEnabled(@NotNull Project project) {
    return isEnabled(PropertiesComponent.getInstance(project));
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_VAL_ENABLED);
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    return Collections.emptyList();
  }

  public void verifyTypeElement(@NotNull final PsiTypeElement psiTypeElement, @NotNull final ProblemsHolder holder) {
    final PsiElement typeParent = psiTypeElement.getParent();

    if (isVal(psiTypeElement)) {
      if (typeParent instanceof PsiField || (typeParent instanceof PsiParameter && !(((PsiParameter) typeParent).getDeclarationScope() instanceof PsiForeachStatement))) {
        holder.registerProblem(psiTypeElement, "'val' works only on local variables and on foreach loops", ProblemHighlightType.ERROR);
      } else if (typeParent instanceof PsiLocalVariable) {
        final PsiLocalVariable psiVariable = (PsiLocalVariable) typeParent;
        final PsiExpression initializer = psiVariable.getInitializer();
        if (initializer == null) {
          holder.registerProblem(psiTypeElement, "'val' on a local variable requires an initializer expression", ProblemHighlightType.ERROR);
        } else if (initializer instanceof PsiArrayInitializerExpression) {
          holder.registerProblem(psiTypeElement, "'val' is not compatible with array initializer expressions. Use the full form (new int[] { ... } instead of just { ... })", ProblemHighlightType.ERROR);
        } else if (initializer instanceof PsiLambdaExpression) {
          holder.registerProblem(psiTypeElement, "'val' is not allowed with lambda expressions.", ProblemHighlightType.ERROR);
        } else {
          final PsiElement typeParentParent = typeParent.getParent();
          if (typeParentParent instanceof PsiDeclarationStatement && typeParentParent.getParent() instanceof PsiForStatement) {
            holder.registerProblem(psiTypeElement, "'val' is not allowed in old-style for loops", ProblemHighlightType.ERROR);
          }
        }
      }
    }
  }

  public static boolean isVal(@NotNull PsiLocalVariable psiLocalVariable) {
    return psiLocalVariable.getInitializer() != null && isSameName(psiLocalVariable.getTypeElement().getText());
  }

  private boolean isVal(@NotNull PsiParameter psiParameter) {
    return psiParameter.getParent() instanceof PsiForeachStatement && isSameName(psiParameter.getTypeElement().getText());
  }

  private boolean isVal(@NotNull PsiTypeElement psiTypeElement) {
    final PsiElement parent = psiTypeElement.getParent();
    if (parent instanceof PsiLocalVariable && ((PsiLocalVariable) parent).getInitializer() != null ||
        parent instanceof PsiParameter && parent.getParent() instanceof PsiForeachStatement) {
      return isSameName(psiTypeElement.getText());
    }
    return false;
  }

  private static boolean isSameName(String className) {
    return LOMBOK_VAL_SHORT_NAME.equals(className) || LOMBOK_VAL_FQN.equals(className);
  }

  @Nullable
  public PsiType inferType(PsiTypeElement typeElement) {
    PsiType psiType = null;

    final PsiElement parent = typeElement.getParent();
    if ((parent instanceof PsiLocalVariable && isVal((PsiLocalVariable) parent)) ||
        (parent instanceof PsiParameter && isVal((PsiParameter) parent))) {

      if (parent instanceof PsiLocalVariable) {
        System.out.println("TypeElement: " + typeElement.hashCode() + " Parent: " + parent.hashCode());
        psiType = processLocalVariableInitializer(typeElement, parent, ((PsiLocalVariable) parent).getInitializer());
      } else {
        psiType = processParameterDeclaration(((PsiParameter) parent).getDeclarationScope());
      }

      if (null == psiType) {
        psiType = PsiType.getJavaLangObject(typeElement.getManager(), GlobalSearchScope.allScope(typeElement.getProject()));
      }
    }
    return psiType;
  }

  private static class FieldLombokCachedValueProvider implements CachedValueProvider<PsiType> {

    private final PsiElement psiElement;
    private final PsiExpression psiExpression;

    FieldLombokCachedValueProvider(PsiElement psiElement, PsiExpression psiExpression) {
      this.psiElement = psiElement;
      this.psiExpression = psiExpression;
    }

    @Nullable
    @Override
    public Result<PsiType> compute() {
      final PsiType result;
      if (psiExpression instanceof PsiMethodCallExpression) {
//        final PsiMethod psiMethod = ((PsiMethodCallExpression) psiExpression).resolveMethod();
//        final PsiType returnType = psiMethod.getReturnType();
        final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) psiExpression).getMethodExpression();
        result = methodExpression.getType();
      } else {
        result = psiExpression.getType();
      }
      return new Result<PsiType>(result, psiElement, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    }
  }

  private PsiType processLocalVariableInitializer(PsiTypeElement typeElement, PsiElement parent, PsiExpression psiExpression) {
    PsiType result = null;
    if (null != psiExpression && !(psiExpression instanceof PsiArrayInitializerExpression)) {

      result = CachedValuesManager.getCachedValue(typeElement, new FieldLombokCachedValueProvider(parent, psiExpression));

//      if (!recursionBreaker.get().contains(psiExpression)) {
//        recursionBreaker.get().add(psiExpression);
//        try {
//          result = psiExpression.getType();
//        } finally {
//          recursionBreaker.get().remove(psiExpression);
//        }

        if (psiExpression instanceof PsiNewExpression) {
          final PsiJavaCodeReferenceElement reference = ((PsiNewExpression) psiExpression).getClassOrAnonymousClassReference();
          if (reference != null) {
            final PsiReferenceParameterList parameterList = reference.getParameterList();
            if (parameterList != null) {
              final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
              if (elements.length == 1 && elements[0].getType() instanceof PsiDiamondType) {
                result = TypeConversionUtil.erasure(result);
              }
            }
          }
        }
      }
//    else {
//        if (psiExpression instanceof PsiMethodCallExpression) {
//          final PsiMethod psiMethod = ((PsiMethodCallExpression) psiExpression).resolveMethod();
//          if (null != psiMethod) {
//            result = psiMethod.getReturnType();
//          }
//        }
//      }
//    }
    return result;
  }

  private PsiType processParameterDeclaration(PsiElement parentDeclarationScope) {
    PsiType result = null;
    if (parentDeclarationScope instanceof PsiForeachStatement) {
      final PsiForeachStatement foreachStatement = (PsiForeachStatement) parentDeclarationScope;
      final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
      if (iteratedValue != null) {
        result = JavaGenericsUtil.getCollectionItemType(iteratedValue);
      }
    }
    return result;
  }
}
