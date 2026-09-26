// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MarkerExpr extends PsiElement {

  @Nullable
  MarkerName getMarkerName();

  @Nullable
  MarkerOp getMarkerOp();

  @Nullable
  MarkerOr getMarkerOr();

  @Nullable
  PythonStr getPythonStr();

}
