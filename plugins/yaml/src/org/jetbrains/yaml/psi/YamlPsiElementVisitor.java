// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public abstract class YamlPsiElementVisitor extends PsiElementVisitor {
  public void visitAlias(@NotNull YAMLAlias alias) {
    visitValue(alias);
  }

  public void visitAnchor(@NotNull YAMLAnchor anchor) {
    visitElement(anchor);
  }

  public void visitCompoundValue(@NotNull YAMLCompoundValue compoundValue) {
    visitValue(compoundValue);
  }

  public void visitDocument(@NotNull YAMLDocument document) {
    visitElement(document);
  }

  public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
    visitElement(keyValue);
  }

  public void visitMapping(@NotNull YAMLMapping mapping) {
    visitCompoundValue(mapping);
  }

  public void visitSequenceItem(@NotNull YAMLSequenceItem sequenceItem) {
    visitElement(sequenceItem);
  }

  public void visitQuotedText (@NotNull YAMLQuotedText quotedText) {
    visitScalar(quotedText);
  }

  public void visitScalar(@NotNull YAMLScalar scalar) {
    visitValue(scalar);
  }

  public void visitScalarList(@NotNull YAMLScalarList scalarList) {
    visitScalar(scalarList);
  }

  public void visitScalarText(@NotNull YAMLScalarText scalarText) {
    visitScalar(scalarText);
  }

  public void visitValue(@NotNull YAMLValue value) {
    visitElement(value);
  }

  public void visitSequence(@NotNull YAMLSequence sequence) {
    visitCompoundValue(sequence);
  }
}
