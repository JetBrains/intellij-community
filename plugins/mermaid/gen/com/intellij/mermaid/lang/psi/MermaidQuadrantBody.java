// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidQuadrantBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidAxisDetailsStatement> getAxisDetailsStatementList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidPointStatement> getPointStatementList();

  @NotNull
  List<MermaidQuadrantDetailsStatement> getQuadrantDetailsStatementList();

  @NotNull
  List<MermaidTitleStatement> getTitleStatementList();

}
