// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashCommand extends BashCompositeElement {

  @Nullable
  BashCommand getCommand();

  @Nullable
  BashHeredoc getHeredoc();

  @NotNull
  List<BashRedirection> getRedirectionList();

}
