// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidSequenceBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidActivateStatement> getActivateStatementList();

  @NotNull
  List<MermaidActorStatement> getActorStatementList();

  @NotNull
  List<MermaidAltStatement> getAltStatementList();

  @NotNull
  List<MermaidAutonumberStatement> getAutonumberStatementList();

  @NotNull
  List<MermaidBoxStatement> getBoxStatementList();

  @NotNull
  List<MermaidBreakStatement> getBreakStatementList();

  @NotNull
  List<MermaidCriticalStatement> getCriticalStatementList();

  @NotNull
  List<MermaidDeactivateStatement> getDeactivateStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidLinkStatement> getLinkStatementList();

  @NotNull
  List<MermaidLinksStatement> getLinksStatementList();

  @NotNull
  List<MermaidLoopStatement> getLoopStatementList();

  @NotNull
  List<MermaidNoteStatement> getNoteStatementList();

  @NotNull
  List<MermaidOptStatement> getOptStatementList();

  @NotNull
  List<MermaidParOverStatement> getParOverStatementList();

  @NotNull
  List<MermaidParStatement> getParStatementList();

  @NotNull
  List<MermaidRectStatement> getRectStatementList();

  @NotNull
  List<MermaidSignalStatement> getSignalStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

}
