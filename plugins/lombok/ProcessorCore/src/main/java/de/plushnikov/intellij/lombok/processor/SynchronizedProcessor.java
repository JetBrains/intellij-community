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
public class SynchronizedProcessor extends AbstractLombokProcessor {

  public static final String CLASS_NAME = Synchronized.class.getName();

  public SynchronizedProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(PsiAnnotation psiAnnotation) {
    //error: @Synchronized is legal only on methods.
    //error: "@Synchronized is legal only on concrete methods."    not abstract
    //error: "The field " + lockName + " does not exist."

    return Collections.emptyList();
  }
}
