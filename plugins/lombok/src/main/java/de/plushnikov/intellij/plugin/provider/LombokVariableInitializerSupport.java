package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents a support class for ignoring variable initializers in context of DefUseInspection for lombok features.
 * It implements the DefUseInspection.IgnoreVariableInitializerSupport interface.
 */
public class LombokVariableInitializerSupport implements DefUseInspection.IgnoreVariableInitializerSupport {
  @Override
  public boolean ignoreVariableInitializer(@NotNull PsiVariable psiVariable) {
    return psiVariable.hasAnnotation(LombokClassNames.BUILDER_DEFAULT);
  }
}
