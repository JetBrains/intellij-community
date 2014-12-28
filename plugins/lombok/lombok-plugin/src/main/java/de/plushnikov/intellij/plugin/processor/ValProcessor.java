package de.plushnikov.intellij.plugin.processor;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.psi.PsiAnnotation;
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
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class ValProcessor extends AbstractProcessor {

  private static final String LOMBOK_VAL_FQN = "lombok.val";
  private static final String LOMBOK_VAL_SHORT_NAME = "val";

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
    if (parent instanceof PsiLocalVariable && ((PsiLocalVariable) parent).getInitializer() != null ||
        parent instanceof PsiParameter && ((PsiParameter) parent).getDeclarationScope() instanceof PsiForeachStatement) {
      final String text = typeElement.getText();
      if (LOMBOK_VAL_SHORT_NAME.equals(text) || LOMBOK_VAL_FQN.equals(text)) {
        final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
        if (referenceElement != null) {
          final PsiElement resolve = referenceElement.resolve();
          if (resolve instanceof PsiClass) {
            if (parent instanceof PsiLocalVariable) {
              final PsiExpression initializer = ((PsiLocalVariable) parent).getInitializer();
              final PsiType initializerType = initializer.getType();
              if (initializer instanceof PsiNewExpression) {
                final PsiJavaCodeReferenceElement reference = ((PsiNewExpression) initializer).getClassOrAnonymousClassReference();
                if (reference != null) {
                  final PsiReferenceParameterList parameterList = reference.getParameterList();
                  if (parameterList != null) {
                    final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
                    if (elements.length == 1 && elements[0].getType() instanceof PsiDiamondType) {
                      return TypeConversionUtil.erasure(initializerType);
                    }
                  }
                }
              }
              return initializerType;
            }
            final PsiForeachStatement foreachStatement = (PsiForeachStatement) ((PsiParameter) parent).getDeclarationScope();
            final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
            if (iteratedValue != null) {
              return JavaGenericsUtil.getCollectionItemType(iteratedValue);
            }
          }
        }
      }
    }
    return null;
  }
}
