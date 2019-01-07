// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashIncludeCommand extends BashCommand {

  @NotNull
  List<BashSimpleCommandElement> getSimpleCommandElementList();

  @Nullable
  PsiElement getWord();

}
