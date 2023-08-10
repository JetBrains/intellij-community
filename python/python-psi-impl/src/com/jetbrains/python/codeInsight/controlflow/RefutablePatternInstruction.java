package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyPattern;
import org.jetbrains.annotations.NotNull;

public class RefutablePatternInstruction extends InstructionImpl {
  private final boolean myMatched;

  public RefutablePatternInstruction(@NotNull ControlFlowBuilder builder,
                                     @NotNull PyPattern element, boolean matched) {
    super(builder, element);
    myMatched = matched;
  }

  @Override
  public @NotNull PsiElement getElement() {
    PsiElement element = super.getElement();
    assert element != null;
    return element;
  }

  public boolean isMatched() {
    return myMatched;
  }

  @Override
  public @NotNull String getElementPresentation() {
    return (myMatched ? "matched" : "refutable") + " pattern: " + getElement().getText();
  }
}
