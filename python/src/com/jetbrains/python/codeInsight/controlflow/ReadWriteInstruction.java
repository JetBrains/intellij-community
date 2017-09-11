/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReadWriteInstruction extends InstructionImpl {
  final InstructionTypeCallback EXPR_TYPE = new InstructionTypeCallback() {
    @Nullable
    @Override
    public Ref<PyType> getType(TypeEvalContext context, @Nullable PsiElement anchor) {
      return Ref.create(myElement instanceof PyExpression ? context.getType((PyExpression)myElement) : null);
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
  private final InstructionTypeCallback myGetType;

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
                               @Nullable final InstructionTypeCallback getType) {
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
                                                final InstructionTypeCallback getType) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.ASSERTTYPE, getType);
  }

  @Nullable
  public Ref<PyType> getType(TypeEvalContext context, @Nullable PsiElement anchor) {
    return myGetType.getType(context, anchor);
  }

  @NotNull
  @NonNls
  @Override
  public String getElementPresentation() {
    return myAccess + " ACCESS: " + myName;
  }
}
