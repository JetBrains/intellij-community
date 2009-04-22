package com.jetbrains.python.psi.resolve;

import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.NameDefiner;
import org.jetbrains.annotations.NotNull;

/**
 * Processor capable of giving multiple hints on what it's looking for.
 * User: dcheryasov
 * Date: Apr 19, 2009
 */
public interface PyClassScopeProcessor extends PsiScopeProcessor {
  /**
   * @return classes of nodes that might be interesting for the processor.
   * ??? Instances of NameDefiner are always considered interesting.
   * ??? An empty list makes processor see only NameDefiners.
   * @see com.jetbrains.python.psi.NameDefiner 
   */
  @NotNull
  Class[] getPossibleTargets();

  Class[] NAME_DEFINER_ONLY = {NameDefiner.class};
}
