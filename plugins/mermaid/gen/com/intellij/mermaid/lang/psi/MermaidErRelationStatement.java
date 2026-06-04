// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidErRelationStatement extends MermaidPsiElement {

  @Nullable
  MermaidComplexLabel getComplexLabel();

  @NotNull
  List<MermaidErIdentifier> getErIdentifierList();

  @NotNull
  MermaidRelationship getRelationship();

  @Nullable
  MermaidString getString();

}
