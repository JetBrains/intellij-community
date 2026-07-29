// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidRequirementDiagramBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidClassDefStatement> getClassDefStatementList();

  @NotNull
  List<MermaidDirectionStatement> getDirectionStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidElementDef> getElementDefList();

  @NotNull
  List<MermaidFlowchartClassStatement> getFlowchartClassStatementList();

  @NotNull
  List<MermaidRelationshipDef> getRelationshipDefList();

  @NotNull
  List<MermaidRequirementDef> getRequirementDefList();

  @NotNull
  List<MermaidStyleStatement> getStyleStatementList();

}
