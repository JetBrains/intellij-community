// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShCompoundList extends ShCompositeElement {

  @NotNull
  List<ShCommand> getCommandList();

  @NotNull
  List<ShHeredoc> getHeredocList();

}
