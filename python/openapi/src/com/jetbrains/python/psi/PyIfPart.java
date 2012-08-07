package com.jetbrains.python.psi;

/**
 * Branches of an 'if' statement.
 * @see PyElsePart
 * User: dcheryasov
 * Date: Mar 12, 2009 2:16:00 AM
 */
public interface PyIfPart extends PyConditionalStatementPart {
  PyIfPart[] EMPTY_ARRAY = new PyIfPart[0];
  /**
   * @return true for a 'elif' part.
   */
  boolean isElif();

}
