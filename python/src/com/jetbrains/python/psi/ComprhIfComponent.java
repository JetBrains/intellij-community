package com.jetbrains.python.psi;

/**
 * The "if" part of list comprehensions and generators.
 * User: dcheryasov
 * Date: Jul 31, 2008
 */
public interface ComprhIfComponent {
    PyExpression getTest();
}
