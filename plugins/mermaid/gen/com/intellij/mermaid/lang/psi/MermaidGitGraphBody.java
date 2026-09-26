// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidGitGraphBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAccStatement> getAccStatementList();

  @NotNull
  List<MermaidBranchStatement> getBranchStatementList();

  @NotNull
  List<MermaidCheckoutStatement> getCheckoutStatementList();

  @NotNull
  List<MermaidCherryPickStatement> getCherryPickStatementList();

  @NotNull
  List<MermaidCommitStatement> getCommitStatementList();

  @NotNull
  List<MermaidMergeStatement> getMergeStatementList();

}
