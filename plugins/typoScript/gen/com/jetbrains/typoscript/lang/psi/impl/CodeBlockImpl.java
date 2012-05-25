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
package com.jetbrains.typoscript.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.typoscript.lang.TypoScriptElementTypes.*;
import com.jetbrains.typoscript.lang.psi.TypoScriptCompositeElementImpl;
import com.jetbrains.typoscript.lang.psi.*;

public class CodeBlockImpl extends TypoScriptCompositeElementImpl implements CodeBlock {

  public CodeBlockImpl(ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public List<Assignment> getAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Assignment.class);
  }

  @Override
  @NotNull
  public List<CodeBlock> getCodeBlockList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CodeBlock.class);
  }

  @Override
  @NotNull
  public List<ConditionElement> getConditionElementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ConditionElement.class);
  }

  @Override
  @NotNull
  public List<Copying> getCopyingList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Copying.class);
  }

  @Override
  @NotNull
  public List<IncludeStatementElement> getIncludeStatementElementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, IncludeStatementElement.class);
  }

  @Override
  @NotNull
  public List<MultilineValueAssignment> getMultilineValueAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MultilineValueAssignment.class);
  }

  @Override
  @NotNull
  public ObjectPath getObjectPath() {
    return findNotNullChildByClass(ObjectPath.class);
  }

  @Override
  @NotNull
  public List<Unsetting> getUnsettingList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Unsetting.class);
  }

  @Override
  @NotNull
  public List<ValueModification> getValueModificationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ValueModification.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) ((Visitor)visitor).visitCodeBlock(this);
    else super.accept(visitor);
  }

}
