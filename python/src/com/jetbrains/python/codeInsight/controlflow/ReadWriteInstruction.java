package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class ReadWriteInstruction extends InstructionImpl {
  final Function<TypeEvalContext, PyType> EXPR_TYPE = new Function<TypeEvalContext, PyType>() {
    @Override
    public PyType fun(TypeEvalContext context) {
      return myElement instanceof PyExpression ? context.getType((PyExpression)myElement) : null;
    }
  };

  public enum ACCESS {
    READ(true, false, false),
    WRITE(false, true, false),
    ASSERTTYPE(false, false, true),
    READWRITE(true, true, false);

    private final boolean isWrite;
    private final boolean isRead;
    private final boolean isAssertType;

    ACCESS(boolean read, boolean write, boolean assertType) {
      isRead = read;
      isWrite = write;
      isAssertType = assertType;
    }

    public boolean isWriteAccess() {
      return isWrite;
    }

    public boolean isReadAccess() {
      return isRead;
    }

    public boolean isAssertTypeAccess() {
      return isAssertType;
    }
  }

  private final String myName;
  private final ACCESS myAccess;
  private final Function<TypeEvalContext, PyType> myGetType;

  private ReadWriteInstruction(final ControlFlowBuilder builder,
                               final PsiElement element,
                               final String name,
                               final ACCESS access) {
    this(builder, element, name, access, null);
  }

  private ReadWriteInstruction(final ControlFlowBuilder builder,
                               final PsiElement element,
                               final String name,
                               final ACCESS access,
                               @Nullable final Function<TypeEvalContext, PyType> getType) {
    super(builder, element);
    myName = name;
    myAccess = access;
    myGetType = getType != null ? getType : EXPR_TYPE;
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

  public static ReadWriteInstruction newInstruction(final ControlFlowBuilder builder,
                                                    final PsiElement element,
                                                    final String name,
                                                    final ACCESS access) {
    return new ReadWriteInstruction(builder, element, name, access);
  }

  public static ReadWriteInstruction assertType(final ControlFlowBuilder builder,
                                                final PsiElement element,
                                                final String name,
                                                final Function<TypeEvalContext, PyType> getType) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.ASSERTTYPE, getType);
  }

  @Nullable
  public PyType getType(TypeEvalContext context) {
    return myGetType.fun(context);
  }

  @NonNls
  @Override
  public String getElementPresentation() {
    return myAccess + " ACCESS: " + myName;
  }
}
