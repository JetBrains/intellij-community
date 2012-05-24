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

public class MultilineValueAssignmentImpl extends TypoScriptCompositeElementImpl implements MultilineValueAssignment {

  public MultilineValueAssignmentImpl(ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public ObjectPath getObjectPath() {
    return findNotNullChildByClass(ObjectPath.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) ((Visitor)visitor).visitMultilineValueAssignment(this);
    else super.accept(visitor);
  }

}
