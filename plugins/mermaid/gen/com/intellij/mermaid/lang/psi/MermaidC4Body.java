// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidC4Body extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidBoundaryStatement> getBoundaryStatementList();

  @NotNull
  List<MermaidC4ComponentStatement> getC4ComponentStatementList();

  @NotNull
  List<MermaidDirectionStatement> getDirectionStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

}
