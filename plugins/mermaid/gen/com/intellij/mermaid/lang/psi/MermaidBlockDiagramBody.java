// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidBlockDiagramBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidBlockDiagramComplexStatement> getBlockDiagramComplexStatementList();

  @NotNull
  List<MermaidBlockDiagramNodeStatement> getBlockDiagramNodeStatementList();

  @NotNull
  List<MermaidBlockStatement> getBlockStatementList();

  @NotNull
  List<MermaidClassDefStatement> getClassDefStatementList();

  @NotNull
  List<MermaidColumnsStatement> getColumnsStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidFlowchartClassStatement> getFlowchartClassStatementList();

  @NotNull
  List<MermaidSpaceStatement> getSpaceStatementList();

  @NotNull
  List<MermaidStyleStatement> getStyleStatementList();

}
