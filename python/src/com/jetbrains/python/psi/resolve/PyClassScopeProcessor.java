package com.jetbrains.python.psi.resolve;

import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Processor capable of giving multiple hints on what it's looking for.
 * User: dcheryasov
 * Date: Apr 19, 2009
 */
public interface PyClassScopeProcessor extends PsiScopeProcessor {
  /**
   * @return set of element types that might be interesting for the processor.
   */
  @NotNull
  TokenSet getTargetTokenSet();
}
