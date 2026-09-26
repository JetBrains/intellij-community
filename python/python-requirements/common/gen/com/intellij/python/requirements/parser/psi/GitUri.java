// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface GitUri extends PsiElement {

  @Nullable
  Fragment getFragment();

  @Nullable
  GitUriPath getGitUriPath();

  @Nullable
  Host getHost();

}
