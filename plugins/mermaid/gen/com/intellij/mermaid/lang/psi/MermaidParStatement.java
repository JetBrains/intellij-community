// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidParStatement extends MermaidFoldableElement {

  @Nullable
  MermaidAndHeader getAndHeader();

  @NotNull
  MermaidParHeader getParHeader();

  @NotNull
  List<MermaidSequenceBody> getSequenceBodyList();

}
