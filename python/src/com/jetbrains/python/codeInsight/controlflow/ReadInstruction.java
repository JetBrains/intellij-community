package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NonNls;

public class ReadInstruction extends InstructionImpl {
  public String myName;

  public ReadInstruction(final ControlFlowBuilder builder, final PyElement element, final String name) {
    super(builder, element);
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @NonNls
  public String getElementPresentation() {
    return "READ ACCESS: " + myName;
  }
}