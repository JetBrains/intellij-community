// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidClassBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidAnnotationStatement> getAnnotationStatementList();

  @NotNull
  List<MermaidClassDiagramClickStatement> getClassDiagramClickStatementList();

  @NotNull
  List<MermaidClassDiagramNoteStatement> getClassDiagramNoteStatementList();

  @NotNull
  List<MermaidClassStatement> getClassStatementList();

  @NotNull
  List<MermaidDirectionStatement> getDirectionStatementList();

  @NotNull
  List<MermaidMemberStatement> getMemberStatementList();

  @NotNull
  List<MermaidNamespaceStatement> getNamespaceStatementList();

  @NotNull
  List<MermaidRelationStatement> getRelationStatementList();

  @NotNull
  List<MermaidStyleStatement> getStyleStatementList();

}
