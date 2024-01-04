// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface NameReq extends PsiElement {

  @Nullable
  Extras getExtras();

  @NotNull
  List<HashOption> getHashOptionList();

  @Nullable
  QuotedMarker getQuotedMarker();

  @NotNull
  SimpleName getSimpleName();

  @Nullable
  Versionspec getVersionspec();

}
