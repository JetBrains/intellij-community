// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidSankeyRecordStatement extends MermaidPsiElement {

  @Nullable
  MermaidComplexSankeyText getComplexSankeyText();

  @NotNull
  List<MermaidIdentifyingComplexSankeyText> getIdentifyingComplexSankeyTextList();

  @NotNull
  List<MermaidIdentifyingQuotedSankeyField> getIdentifyingQuotedSankeyFieldList();

  @Nullable
  MermaidQuotedSankeyField getQuotedSankeyField();

}
