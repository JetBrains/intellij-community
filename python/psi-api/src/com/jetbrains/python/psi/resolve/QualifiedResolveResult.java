package com.jetbrains.python.psi.resolve;

import com.intellij.psi.ResolveResult;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Knows about the last qualifier that occurred in assignment resolution chain.
 * See {@link com.jetbrains.python.psi.PyReferenceExpression#followAssignmentsChain(com.jetbrains.python.psi.types.TypeEvalContext) followAssignmentsChain()}
 * <br/>
 * User: dcheryasov
 * Date: May 6, 2010 6:55:30 PM
 */
public interface QualifiedResolveResult extends ResolveResult {
  @Nullable
  List<PyExpression> getQualifiers();

  /**
   * @return true iff the resolve result is implicit, that is, not exact but by divination and looks reasonable. 
   */
  boolean isImplicit();
}
