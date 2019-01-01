// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSelectCommand extends BashCommand {

  @Nullable
  BashBlock getBlock();

  @Nullable
  BashListTerminator getListTerminator();

  @NotNull
  List<BashString> getStringList();

  @Nullable
  PsiElement getSemi();

  @NotNull
  PsiElement getSelect();

}
