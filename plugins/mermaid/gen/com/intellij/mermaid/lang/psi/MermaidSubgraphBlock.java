// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidSubgraphBlock extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidClassDefStatement> getClassDefStatementList();

  @NotNull
  List<MermaidDirectionStatement> getDirectionStatementList();

  @NotNull
  List<MermaidFlowchartClassStatement> getFlowchartClassStatementList();

  @NotNull
  List<MermaidFlowchartClickStatement> getFlowchartClickStatementList();

  @NotNull
  List<MermaidLinkStyleStatement> getLinkStyleStatementList();

  @NotNull
  List<MermaidStyleStatement> getStyleStatementList();

  @NotNull
  List<MermaidSubgraphStatement> getSubgraphStatementList();

  @NotNull
  List<MermaidVertexStatement> getVertexStatementList();

}
