package de.plushnikov.intellij.plugin.processor;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
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
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
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
    super(val.class, PsiElement.class);
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

  protected boolean isVal(@NotNull PsiTypeElement psiTypeElement) {
    final PsiJavaCodeReferenceElement referenceElement = psiTypeElement.getInnermostComponentReferenceElement();
    if (null != referenceElement) {
      final PsiElement psiElement = referenceElement.resolve();
      if (psiElement instanceof PsiClass) {
        final String qualifiedName = ((PsiClass) psiElement).getQualifiedName();
        return LOMBOK_VAL_FQN.equals(qualifiedName) || LOMBOK_VAL_SHORT_NAME.equals(qualifiedName);
      }
    }
    return false;
  }

  @Nullable
  public PsiType inferType(PsiTypeElement typeElement) {
    PsiType psiType = null;

    final PsiElement parent = typeElement.getParent();
    if ((parent instanceof PsiLocalVariable || parent instanceof PsiParameter) && isVal(typeElement)) {

      if (parent instanceof PsiLocalVariable) {
        psiType = processLocalVariableInitializer(((PsiLocalVariable) parent).getInitializer());
      } else {
        psiType = processParameterDeclaration(((PsiParameter) parent).getDeclarationScope());
      }

      if (null == psiType) {
        psiType = PsiType.getJavaLangObject(typeElement.getManager(), GlobalSearchScope.projectScope(typeElement.getProject()));
      }
    }
    return psiType;
  }

  protected PsiType processLocalVariableInitializer(PsiExpression initializer) {
    PsiType result = null;
    if (null != initializer && !(initializer instanceof PsiArrayInitializerExpression)) {
      if (!recursionBreaker.get().contains(initializer)) {
        recursionBreaker.get().add(initializer);
        try {
          result = initializer.getType();
        } finally {
          recursionBreaker.get().remove(initializer);
        }

        if (initializer instanceof PsiNewExpression) {
          final PsiJavaCodeReferenceElement reference = ((PsiNewExpression) initializer).getClassOrAnonymousClassReference();
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
    }
    return result;
  }

  protected PsiType processParameterDeclaration(PsiElement parentDeclarationScope) {
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
