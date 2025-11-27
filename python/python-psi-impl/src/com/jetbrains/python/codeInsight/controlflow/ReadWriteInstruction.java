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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReadWriteInstruction extends InstructionImpl {
  private static InstructionTypeCallback instructionTypeCallback(@Nullable PsiElement element) {
    return element instanceof PyExpression expression
           ? context -> Ref.create(context.getType(expression))
           : context -> Ref.create(null);
  }

  public enum ACCESS {
    READ(true, false, false, false),
    WRITE(false, true, false, false),
    ASSERTTYPE(false, false, true, false),
    READWRITE(true, true, false, false),
    DELETE(false, false, false, true);

    private final boolean isWrite;
    private final boolean isRead;
    private final boolean isAssertType;
    private final boolean isDelete;

    ACCESS(boolean read, boolean write, boolean assertType, boolean delete) {
      isRead = read;
      isWrite = write;
      isAssertType = assertType;
      isDelete = delete;
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

    public boolean isDeleteAccess() {
      return isDelete;
    }
  }

  private final @Nullable String myName;
  private final @NotNull ACCESS myAccess;
  private final @NotNull InstructionTypeCallback myGetType;

  private ReadWriteInstruction(final @NotNull ControlFlowBuilder builder,
                               final @Nullable PsiElement element,
                               final @Nullable String name,
                               final @NotNull ACCESS access) {
    this(builder, element, name, access, null);
  }

  private ReadWriteInstruction(final @NotNull ControlFlowBuilder builder,
                               final @Nullable PsiElement element,
                               final @Nullable String name,
                               final @NotNull ACCESS access,
                               final @Nullable InstructionTypeCallback getType) {
    super(builder, element);
    myName = name;
    myAccess = access;
    myGetType = getType != null ? getType : instructionTypeCallback(element);
  }

  public @Nullable String getName() {
    return myName;
  }

  public @NotNull ACCESS getAccess() {
    return myAccess;
  }

  public static @NotNull ReadWriteInstruction read(final @NotNull ControlFlowBuilder builder,
                                                   final @Nullable PyElement element,
                                                   final @Nullable String name) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.READ);
  }

  public static @NotNull ReadWriteInstruction write(final @NotNull ControlFlowBuilder builder,
                                                    final @Nullable PyElement element,
                                                    final @Nullable String name) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.WRITE);
  }

  public static @NotNull ReadWriteInstruction newInstruction(final @NotNull ControlFlowBuilder builder,
                                                             final @Nullable PsiElement element,
                                                             final @Nullable String name,
                                                             final @NotNull ACCESS access) {
    return new ReadWriteInstruction(builder, element, name, access);
  }

  public static @NotNull ReadWriteInstruction assertType(final @NotNull ControlFlowBuilder builder,
                                                         final @Nullable PsiElement element,
                                                         final @Nullable String name,
                                                         final @Nullable InstructionTypeCallback getType) {
    return new ReadWriteInstruction(builder, element, name, ACCESS.ASSERTTYPE, getType);
  }

  public @Nullable Ref<PyType> getType(TypeEvalContext context, @Nullable PsiElement anchor) {
    return myGetType.getType(context);
  }

  @Override
  public @NotNull @NonNls String getElementPresentation() {
    return myAccess + " ACCESS: " + myName;
  }
}
