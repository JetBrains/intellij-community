// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidErBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidClassDefStatement> getClassDefStatementList();

  @NotNull
  List<MermaidDirectionStatement> getDirectionStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidEntityDeclaration> getEntityDeclarationList();

  @NotNull
  List<MermaidErIdentifier> getErIdentifierList();

  @NotNull
  List<MermaidErIdentifierAlias> getErIdentifierAliasList();

  @NotNull
  List<MermaidErRelationStatement> getErRelationStatementList();

  @NotNull
  List<MermaidErStyleClass> getErStyleClassList();

  @NotNull
  List<MermaidFlowchartClassStatement> getFlowchartClassStatementList();

  @NotNull
  List<MermaidStyleStatement> getStyleStatementList();

}
