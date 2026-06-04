// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidRelation extends MermaidPsiElement {

  @NotNull
  List<MermaidCardinality> getCardinalityList();

  @NotNull
  MermaidLineType getLineType();

  @Nullable
  MermaidRelationTypeLeft getRelationTypeLeft();

  @Nullable
  MermaidRelationTypeRight getRelationTypeRight();

}
