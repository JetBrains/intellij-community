// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashUntilCommand extends BashCommand {

  @NotNull
  List<BashCompoundList> getCompoundListList();

  @NotNull
  PsiElement getDo();

  @NotNull
  PsiElement getDone();

  @NotNull
  PsiElement getUntil();

}
