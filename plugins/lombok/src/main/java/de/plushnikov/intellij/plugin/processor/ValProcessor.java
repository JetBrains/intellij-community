package de.plushnikov.intellij.plugin.processor;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class ValProcessor extends AbstractProcessor {

  private static final String LOMBOK_VAL_FQN = "lombok.val";
  private static final String LOMBOK_VAL_SHORT_NAME = "val";
  private static final String LOMBOK_VAR_FQN = "lombok.var";
  private static final String LOMBOK_VAR_EXPERIMENTAL_FQN = "lombok.experimental.var";
  private static final String LOMBOK_VAR_SHORT_NAME = "var";

  public ValProcessor() {
    super(PsiElement.class, val.class, lombok.experimental.var.class, lombok.var.class);
  }

  public static boolean isVal(@NotNull PsiLocalVariable psiLocalVariable) {
    return psiLocalVariable.getInitializer() != null && isSameName(psiLocalVariable.getTypeElement().getText());
  }

  public static boolean isValOrVar(@NotNull PsiLocalVariable psiLocalVariable) {
    return psiLocalVariable.getInitializer() != null && isValOrVar(psiLocalVariable.getTypeElement().getText());
  }

  private static boolean isSameName(String className) {
    return LOMBOK_VAL_SHORT_NAME.equals(className) || LOMBOK_VAL_FQN.equals(className);
  }

  private static boolean isVar(String className) {
    return LOMBOK_VAR_SHORT_NAME.equals(className) || LOMBOK_VAR_FQN.equals(className) || LOMBOK_VAR_EXPERIMENTAL_FQN.equals(className);
  }

  private static boolean isValOrVar(String className) {
    return isSameName(className) || isVar(className);
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

  public void verifyVariable(@NotNull final PsiLocalVariable psiLocalVariable, @NotNull final ProblemsHolder holder) {
    boolean isVal = isSameName(psiLocalVariable.getTypeElement().getText());
    boolean isVar = isVar(psiLocalVariable.getTypeElement().getText());
    final String ann = isVal ? "val" : "var";
    if (isVal || isVar) {
      final PsiExpression initializer = psiLocalVariable.getInitializer();
      if (initializer == null) {
        holder.registerProblem(psiLocalVariable, "'" + ann + "' on a local variable requires an initializer expression", ProblemHighlightType.ERROR);
      } else if (initializer instanceof PsiArrayInitializerExpression) {
        holder.registerProblem(psiLocalVariable, "'" + ann + "' is not compatible with array initializer expressions. Use the full form (new int[] { ... } instead of just { ... })", ProblemHighlightType.ERROR);
      } else if (initializer instanceof PsiLambdaExpression) {
        holder.registerProblem(psiLocalVariable, "'" + ann + "' is not allowed with lambda expressions.", ProblemHighlightType.ERROR);
      } else if (isVal) {
        final PsiElement typeParentParent = psiLocalVariable.getParent();
        if (typeParentParent instanceof PsiDeclarationStatement && typeParentParent.getParent() instanceof PsiForStatement) {
          holder.registerProblem(psiLocalVariable, "'" + ann + "' is not allowed in old-style for loops", ProblemHighlightType.ERROR);
        }
      }
    }
  }

  public void verifyParameter(@NotNull final PsiParameter psiParameter, @NotNull final ProblemsHolder holder) {
    final PsiTypeElement typeElement = psiParameter.getTypeElement();
    boolean isVal = null != typeElement && isSameName(typeElement.getText());
    boolean isVar = null != typeElement && isVar(typeElement.getText());
    if (isVar || isVal) {
      PsiElement scope = psiParameter.getDeclarationScope();
      boolean isForeachStatement = scope instanceof PsiForeachStatement;
      boolean isForStatement = scope instanceof PsiForStatement;
      if (isVal && !isForeachStatement) {
        holder.registerProblem(psiParameter, "'val' works only on local variables and on foreach loops", ProblemHighlightType.ERROR);
      } else if (isVar && !(isForeachStatement || isForStatement)) {
        holder.registerProblem(psiParameter, "'var' works only on local variables and on for/foreach loops", ProblemHighlightType.ERROR);
      }
    }
  }

  private boolean isValOrVarForEach(@NotNull PsiParameter psiParameter) {
    return psiParameter.getParent() instanceof PsiForeachStatement && isValOrVar(psiParameter.getTypeElement().getText());
  }

  @Nullable
  public PsiType inferType(PsiTypeElement typeElement) {
    PsiType psiType = null;

    final PsiElement parent = typeElement.getParent();
    if ((parent instanceof PsiLocalVariable && isValOrVar((PsiLocalVariable) parent)) ||
      (parent instanceof PsiParameter && isValOrVarForEach((PsiParameter) parent))) {

      if (parent instanceof PsiLocalVariable) {
        psiType = processLocalVariableInitializer(((PsiLocalVariable) parent).getInitializer());
      } else {
        psiType = processParameterDeclaration(((PsiParameter) parent).getDeclarationScope());
      }

      if (null == psiType) {
        psiType = PsiType.getJavaLangObject(typeElement.getManager(), GlobalSearchScope.allScope(typeElement.getProject()));
      }
    }
    return psiType;
  }

  private PsiType processLocalVariableInitializer(final PsiExpression psiExpression) {
    PsiType result = null;
    if (null != psiExpression && !(psiExpression instanceof PsiArrayInitializerExpression)) {

      if (psiExpression instanceof PsiConditionalExpression) {
        result = RecursionManager.doPreventingRecursion(psiExpression, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            final PsiExpression thenExpression = ((PsiConditionalExpression) psiExpression).getThenExpression();
            final PsiExpression elseExpression = ((PsiConditionalExpression) psiExpression).getElseExpression();

            final PsiType thenType = null != thenExpression ? thenExpression.getType() : null;
            final PsiType elseType = null != elseExpression ? elseExpression.getType() : null;

            if (thenType == null) {
              return elseType;
            }
            if (elseType == null) {
              return thenType;
            }

            if (TypeConversionUtil.isAssignable(thenType, elseType, false)) {
              return thenType;
            }
            if (TypeConversionUtil.isAssignable(elseType, thenType, false)) {
              return elseType;
            }
            return thenType;
          }
        });
      } else {
        result = RecursionManager.doPreventingRecursion(psiExpression, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return psiExpression.getType();
          }
        });
      }

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
