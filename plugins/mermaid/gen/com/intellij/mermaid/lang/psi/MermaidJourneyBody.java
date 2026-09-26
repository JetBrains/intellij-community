// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidJourneyBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidJourneyDataStatement> getJourneyDataStatementList();

  @NotNull
  List<MermaidJourneySectionStatement> getJourneySectionStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

}
