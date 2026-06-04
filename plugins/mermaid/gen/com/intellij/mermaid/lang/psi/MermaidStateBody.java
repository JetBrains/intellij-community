// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidStateBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidCompositeStateDeclaration> getCompositeStateDeclarationList();

  @NotNull
  List<MermaidCssClassStatement> getCssClassStatementList();

  @NotNull
  List<MermaidDirectionStatement> getDirectionStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidStateClassDefStatement> getStateClassDefStatementList();

  @NotNull
  List<MermaidStateDeclaration> getStateDeclarationList();

  @NotNull
  List<MermaidStateId> getStateIdList();

  @NotNull
  List<MermaidStateNote> getStateNoteList();

  @NotNull
  List<MermaidStateRelationStatement> getStateRelationStatementList();

}
