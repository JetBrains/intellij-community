// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShSimpleCommand extends ShCommand {

  @NotNull
  ShCommand getCommand();

  @NotNull
  List<ShSimpleCommandElement> getSimpleCommandElementList();

  //WARNING: getReferences(...) is skipped
  //matching getReferences(ShSimpleCommand, ...)
  //methods are not found in ShPsiImplUtil

}
