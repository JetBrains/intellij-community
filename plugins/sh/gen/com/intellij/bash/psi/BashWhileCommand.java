// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashWhileCommand extends BashCommand {

  @Nullable
  BashCompoundList getCompoundList();

  @Nullable
  BashDoBlock getDoBlock();

  @NotNull
  PsiElement getWhile();

}
