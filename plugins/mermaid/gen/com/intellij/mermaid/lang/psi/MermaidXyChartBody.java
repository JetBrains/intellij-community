// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidXyChartBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidBarStatement> getBarStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidLineStatement> getLineStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

  @NotNull
  List<MermaidXAxisStatement> getXAxisStatementList();

  @NotNull
  List<MermaidYAxisStatement> getYAxisStatementList();

}
