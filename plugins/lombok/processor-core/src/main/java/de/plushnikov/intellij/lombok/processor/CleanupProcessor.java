package de.plushnikov.intellij.lombok.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import lombok.Synchronized;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Plushnikov Michail
 */
public class CleanupProcessor extends AbstractLombokProcessor {

  public static final String CLASS_NAME = Synchronized.class.getName();

  public CleanupProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(PsiAnnotation psiAnnotation) {
    // error: cleanupName cannot be the empty string
    // error: "@Cleanup is legal only on local variable declarations."
    // error: "@Cleanup variable declarations need to be initialized."
    // error: "@Cleanup is legal only on a local variable declaration inside a block."
    // warning: "You're assigning an auto-cleanup variable to something else. This is a bad idea."
    return Collections.emptyList();
  }

}
