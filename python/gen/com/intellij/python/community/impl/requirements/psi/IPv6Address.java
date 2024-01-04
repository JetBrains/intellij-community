// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface IPv6Address extends PsiElement {

  @Nullable
  H16 getH16();

  @NotNull
  List<H16Colon> getH16ColonList();

  @Nullable
  Ls32 getLs32();

}
