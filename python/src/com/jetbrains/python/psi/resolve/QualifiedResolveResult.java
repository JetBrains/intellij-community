package com.jetbrains.python.psi.resolve;

import com.intellij.psi.ResolveResult;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * Knows about the last qualifier that occurred in assignment resolution chain.
 * See {@link com.jetbrains.python.psi.PyReferenceExpression#followAssignmentsChain() followAssignmentsChain()}
 * <br/>
 * User: dcheryasov
 * Date: May 6, 2010 6:55:30 PM
 */
public interface QualifiedResolveResult extends ResolveResult {
  @Nullable
  PyExpression getLastQualifier();
}
