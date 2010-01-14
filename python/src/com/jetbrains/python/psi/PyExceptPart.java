package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author dcheryasov
 */
public interface PyExceptPart extends PyElement, NameDefiner, PyStatementPart {
    PyExceptPart[] EMPTY_ARRAY = new PyExceptPart[0];

    @Nullable PyExpression getExceptClass();
    @Nullable PyExpression getTarget();
}
