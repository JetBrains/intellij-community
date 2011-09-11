package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class DataProcessor extends AbstractLombokClassProcessor {

  private static final String CLASS_NAME = Data.class.getName();

  private final Collection<? extends LombokClassProcessor> internProcessors =
      Arrays.asList(
          new RequiredArgsConstructorProcessor(),
          new GetterProcessor(), new SetterProcessor(),
          new EqualsAndHashCodeProcessor(),
          new ToStringProcessor());

  public DataProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    boolean result = true;
    for (LombokClassProcessor processor : internProcessors) {
      result &= processor.process(psiClass, psiAnnotation, target);
    }
    return result;
  }

}
