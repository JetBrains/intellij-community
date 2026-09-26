// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface NameReq extends PsiElement {

  @Nullable
  EditableOption getEditableOption();

  @Nullable
  Extras getExtras();

  @NotNull
  List<LongOption> getLongOptionList();

  @NotNull
  PackageName getPackageName();

  @Nullable
  QuotedMarker getQuotedMarker();

  @Nullable
  Versionspec getVersionspec();

}
