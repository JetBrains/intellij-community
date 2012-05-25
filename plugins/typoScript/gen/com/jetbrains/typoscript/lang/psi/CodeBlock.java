/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CodeBlock extends TypoScriptCompositeElement {

  @NotNull
  List<Assignment> getAssignmentList();

  @NotNull
  List<CodeBlock> getCodeBlockList();

  @NotNull
  List<ConditionElement> getConditionElementList();

  @NotNull
  List<Copying> getCopyingList();

  @NotNull
  List<IncludeStatementElement> getIncludeStatementElementList();

  @NotNull
  List<MultilineValueAssignment> getMultilineValueAssignmentList();

  @NotNull
  ObjectPath getObjectPath();

  @NotNull
  List<Unsetting> getUnsettingList();

  @NotNull
  List<ValueModification> getValueModificationList();

}
