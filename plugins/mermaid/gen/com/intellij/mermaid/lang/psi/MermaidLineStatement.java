// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidLineStatement extends MermaidPsiElement {

  @Nullable
  MermaidMarkdownValue getMarkdownValue();

  @NotNull
  MermaidPlotData getPlotData();

  @Nullable
  MermaidString getString();

}
