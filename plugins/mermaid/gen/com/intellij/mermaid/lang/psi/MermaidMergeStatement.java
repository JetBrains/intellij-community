// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidMergeStatement extends MermaidGitGraphBranchIdentifierHolder {

  @Nullable
  MermaidCommitIdAttribute getCommitIdAttribute();

  @Nullable
  MermaidCommitTagAttribute getCommitTagAttribute();

  @Nullable
  MermaidCommitTypeAttribute getCommitTypeAttribute();

  @NotNull
  MermaidGitGraphBranchIdentifier getGitGraphBranchIdentifier();

}
