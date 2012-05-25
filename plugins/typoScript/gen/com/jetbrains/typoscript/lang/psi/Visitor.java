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

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;

public class Visitor extends PsiElementVisitor {

  public void visitAssignment(@NotNull Assignment o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitCodeBlock(@NotNull CodeBlock o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitConditionElement(@NotNull ConditionElement o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitCopying(@NotNull Copying o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitIncludeStatementElement(@NotNull IncludeStatementElement o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitMultilineValueAssignment(@NotNull MultilineValueAssignment o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitObjectPath(@NotNull ObjectPath o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitUnsetting(@NotNull Unsetting o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitValueModification(@NotNull ValueModification o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitTypoScriptCompositeElement(@NotNull TypoScriptCompositeElement o) {
    visitElement(o);
  }

}
