package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public interface PyStatementListContainer {
  @NotNull
  PyStatementList getStatementList();
}
