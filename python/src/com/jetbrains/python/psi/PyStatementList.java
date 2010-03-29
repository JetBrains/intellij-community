package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PyStatementList extends PyElement {
  PyStatementList[] EMPTY_ARRAY = new PyStatementList[0];

  PyStatement[] getStatements();
}
