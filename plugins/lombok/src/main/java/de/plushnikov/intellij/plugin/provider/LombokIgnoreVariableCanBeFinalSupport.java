package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.localCanBeFinal.IgnoreVariableCanBeFinalSupport;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Ignore VariableCanBeFinal-Inspection for lombok val annotation
 */
public final class LombokIgnoreVariableCanBeFinalSupport implements IgnoreVariableCanBeFinalSupport {

  @Override
  public boolean ignoreVariable(@NotNull PsiVariable psiVariable) {
    return (psiVariable instanceof PsiLocalVariable psiLocalVariable && ValProcessor.isVal(psiLocalVariable));
  }
}
