// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidGanttBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidGanttAxisFormatStatement> getGanttAxisFormatStatementList();

  @NotNull
  List<MermaidGanttClickStatement> getGanttClickStatementList();

  @NotNull
  List<MermaidGanttDataStatement> getGanttDataStatementList();

  @NotNull
  List<MermaidGanttDateFormatStatement> getGanttDateFormatStatementList();

  @NotNull
  List<MermaidGanttExcludesStatement> getGanttExcludesStatementList();

  @NotNull
  List<MermaidGanttIncludesStatement> getGanttIncludesStatementList();

  @NotNull
  List<MermaidGanttInclusiveEndDatesStatement> getGanttInclusiveEndDatesStatementList();

  @NotNull
  List<MermaidGanttSectionStatement> getGanttSectionStatementList();

  @NotNull
  List<MermaidGanttTickIntervalStatement> getGanttTickIntervalStatementList();

  @NotNull
  List<MermaidGanttTodayMarkerStatement> getGanttTodayMarkerStatementList();

  @NotNull
  List<MermaidGanttTopAxisStatement> getGanttTopAxisStatementList();

  @NotNull
  List<MermaidGanttWeekdayStatement> getGanttWeekdayStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

}
