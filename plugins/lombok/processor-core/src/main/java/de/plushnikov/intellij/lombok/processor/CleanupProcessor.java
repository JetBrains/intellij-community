package de.plushnikov.intellij.lombok.processor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import de.plushnikov.intellij.lombok.problem.ProblemNewBuilder;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import lombok.Cleanup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Inspect and validate @Cleanup lombok annotation
 *
 * @author Plushnikov Michail
 */
public class CleanupProcessor extends AbstractLombokProcessor {

  public static final String CLASS_NAME = Cleanup.class.getName();

  public CleanupProcessor() {
    super(CLASS_NAME, PsiElement.class);
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    // TODO warning: "You're assigning an auto-cleanup variable to something else. This is a bad idea."
    Collection<LombokProblem> result = new ArrayList<LombokProblem>(2);
    final ProblemNewBuilder problemNewBuilder = new ProblemNewBuilder(result);

    PsiLocalVariable psiVariable = PsiTreeUtil.getParentOfType(psiAnnotation, PsiLocalVariable.class);
    if (null != psiVariable) {
      final String cleanupName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "value", String.class);

      if (StringUtil.isEmptyOrSpaces(cleanupName)) {
        problemNewBuilder.addError("'@Cleanup': value cannot be the empty string");
      } else {
        validateCleanUpMethodExists(psiVariable, cleanupName, problemNewBuilder);
      }

      validateInitializerExist(problemNewBuilder, psiVariable);

    } else {
      problemNewBuilder.addError("'@Cleanup' is legal only on local variable declarations");
    }

    return result;
  }

  private void validateCleanUpMethodExists(@NotNull PsiLocalVariable psiVariable, @NotNull String cleanupName, @NotNull ProblemNewBuilder problemNewBuilder) {
    final PsiType psiType = psiVariable.getType();
    if (psiType instanceof PsiClassType) {
      final PsiClassType psiClassType = (PsiClassType) psiType;
      final PsiClass psiClassOfField = psiClassType.resolve();
      final PsiMethod[] methods;

      if (psiClassOfField != null) {
        methods = psiClassOfField.findMethodsByName(cleanupName, true);
        boolean hasCleanupMethod = false;
        for (PsiMethod method : methods) {
          if (0 == method.getParameterList().getParametersCount()) {
            hasCleanupMethod = true;
          }
        }

        if (!hasCleanupMethod) {
          problemNewBuilder.addError(String.format("'@Cleanup': method '%s()' not found on target class", cleanupName));
        }
      }
    } else {
      problemNewBuilder.addError("'@Cleanup': is legal only on a local variable declaration inside a block");
    }
  }

  private void validateInitializerExist(@NotNull ProblemNewBuilder problemNewBuilder, @NotNull PsiLocalVariable psiVariable) {
    if (null == psiVariable.getInitializer()) {
      problemNewBuilder.addError("'@Cleanup' variable declarations need to be initialized.");
    }
  }

}
