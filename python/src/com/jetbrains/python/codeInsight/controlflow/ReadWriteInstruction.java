package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NonNls;

public class ReadWriteInstruction extends InstructionImpl {

  public enum ACCESS {
    READ(true, false),
    WRITE(false, true),
    WRITETYPE(false, true),
    READWRITE(true, true);

    private final boolean isWrite;
    private final boolean isRead;

    ACCESS(boolean read, boolean write) {
      isRead = read;
      isWrite = write;
    }
    public boolean isWriteAccess(){
      return isWrite;
    }
    public boolean isReadAccess(){
      return isRead;
    }
  }

  public String myName;
  private final ACCESS myAccess;

  private ReadWriteInstruction(final ControlFlowBuilder builder,
                              final PsiElement element,
                              final String name,
                              final ACCESS access) {
    super(builder, element);
    myName = name;
    myAccess = access;
  }

  public String getName() {
    return myName;
  }

  public ACCESS getAccess() {
    return myAccess;
  }

  public static ReadWriteInstruction read(final ControlFlowBuilder builder,
                              final PyElement element,
                              final String name) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.READ);
  }

  public static ReadWriteInstruction write(final ControlFlowBuilder builder,
                              final PyElement element,
                              final String name) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.WRITE);
  }

  public static ReadWriteInstruction writeType(final ControlFlowBuilder builder,
                              final PyElement element,
                              final String name) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.WRITETYPE);
  }

  public static ReadWriteInstruction readWrite(final ControlFlowBuilder builder,
                              final PyElement element,
                              final String name) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.READWRITE);
  }

  public static ReadWriteInstruction newInstruction(final ControlFlowBuilder builder,
                              final PsiElement element,
                              final String name,
                              final ACCESS access) {
    return new ReadWriteInstruction(builder, element, name, access);
  }

  @NonNls
  public String getElementPresentation() {
    return myAccess + " ACCESS: " + myName;
  }
}
