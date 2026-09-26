// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidTimelineBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidTimelineDataStatement> getTimelineDataStatementList();

  @NotNull
  List<MermaidTimelineSectionStatement> getTimelineSectionStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

}
