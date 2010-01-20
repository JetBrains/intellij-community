package com.jetbrains.python.codeInsight.controlflow;

import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author oleg
 */
public interface Instruction {
  @Nullable
  public PyElement getElement();

  public Collection<Instruction> allSucc();

  public Collection<Instruction> allPred();

  String getElementPresentation();

  public int num();
}
