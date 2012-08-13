package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyWeakClassType extends PyClassTypeImpl implements PyWeakType {
  public PyWeakClassType(@NotNull PyClass source, boolean isDefinition) {
    super(source, isDefinition);
  }
}
