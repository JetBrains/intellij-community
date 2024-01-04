// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface UrlReq extends PsiElement {

  @Nullable
  Extras getExtras();

  @Nullable
  QuotedMarker getQuotedMarker();

  @NotNull
  SimpleName getSimpleName();

  @NotNull
  Urlspec getUrlspec();

}
