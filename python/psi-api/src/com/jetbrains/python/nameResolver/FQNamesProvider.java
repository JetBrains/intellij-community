package com.jetbrains.python.nameResolver;

import org.jetbrains.annotations.NotNull;

/**
 * Some enum value that represents one or more fully qualified names for some function
 * @author Ilya.Kazakevich
 */
public interface FQNamesProvider {
  /**
   * @return one or more fully qualified names
   */
  @NotNull
  String[] getNames();
}
