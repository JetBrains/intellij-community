/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

public abstract class InstructionBase implements Instruction, Cloneable{
  public Instruction clone() {
    try {
      return (Instruction)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

}