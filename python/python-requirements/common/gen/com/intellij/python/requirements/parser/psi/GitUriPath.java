// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface GitUriPath extends PsiElement {

  @NotNull
  List<GitUriPathSegment> getGitUriPathSegmentList();

  @Nullable
  VcsRevision getVcsRevision();

}
