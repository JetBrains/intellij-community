package de.plushnikov.intellij.plugin.processor;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
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

  @Nullable
  public PsiType inferType(PsiTypeElement typeElement) {
    final PsiElement parent = typeElement.getParent();
    if (!(parent instanceof PsiLocalVariable || parent instanceof PsiParameter)) {
      return null;
    }

    final String typeElementText = typeElement.getText();
    if (!(LOMBOK_VAL_SHORT_NAME.equals(typeElementText) || LOMBOK_VAL_FQN.equals(typeElementText))) {
      return null;
    }

    PsiType psiType;
    if (parent instanceof PsiLocalVariable) {
      psiType = processLocalVariableInitializer(((PsiLocalVariable) parent).getInitializer());
    } else {
      psiType = processParameterDeclaration(((PsiParameter) parent).getDeclarationScope());
    }

    if (null == psiType) {
      psiType = PsiType.getJavaLangObject(typeElement.getManager(), GlobalSearchScope.projectScope(typeElement.getProject()));
    }
    return psiType;
  }

  //TODO ERROR:  'val' is not allowed in old-style for loops
  public PsiType processLocalVariableInitializer(PsiExpression initializer) {
    PsiType result = null;
    if (initializer instanceof PsiArrayInitializerExpression) {
      // TODO add ERROR : 'val' is not compatible with array initializer expressions. Use the full form (new int[] { ... } instead of just { ... })
//      PsiArrayInitializerExpression psiArrayInitializerExpression = (PsiArrayInitializerExpression) initializer;
//      final PsiExpression[] psiArrayInitializer = psiArrayInitializerExpression.getInitializers();
//      if (psiArrayInitializer.length > 0) {
//        final PsiType psiArrayElementType = psiArrayInitializer[0].getType();
//        if (null != psiArrayElementType) {
//          result = new PsiArrayType(psiArrayElementType);
//        }
//      }
    } else if (null != initializer) {
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

  public PsiType processParameterDeclaration(PsiElement parentDeclarationScope) {
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
