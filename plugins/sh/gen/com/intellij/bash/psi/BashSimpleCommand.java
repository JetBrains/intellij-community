// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public interface BashSimpleCommand extends BashCommand {

  @NotNull
  List<BashSimpleCommandElement> getSimpleCommandElementList();

  @NotNull
  PsiReference[] getReferences();

}
