// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidBlockDiagramNodeStatementInner extends MermaidPsiElement {

  @NotNull
  List<MermaidBlockDiagramArrow> getBlockDiagramArrowList();

  @NotNull
  List<MermaidBlockDiagramNode> getBlockDiagramNodeList();

  @Nullable
  MermaidBlockSize getBlockSize();

}
