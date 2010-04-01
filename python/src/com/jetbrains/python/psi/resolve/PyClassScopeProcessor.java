package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Processor capable of giving multiple hints on what it's looking for.
 * User: dcheryasov
 * Date: Apr 19, 2009
 */
public interface PyClassScopeProcessor extends PsiScopeProcessor {
  /**
   * @return condition on nodes that might be interesting for the processor.
   * 
   * ??? Instances of NameDefiner are always considered interesting.
   * @see com.jetbrains.python.psi.NameDefiner
   */
  @NotNull
  Condition<PsiElement> getTargetCondition();
}
