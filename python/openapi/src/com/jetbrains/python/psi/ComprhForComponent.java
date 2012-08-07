package com.jetbrains.python.psi;

/**
 * The "for" part of list comprehensions and generators.
 * User: dcheryasov
 * Date: Jul 31, 2008
 */
public interface ComprhForComponent extends ComprehensionComponent {
  PyExpression getIteratorVariable();
  PyExpression getIteratedList();
}
