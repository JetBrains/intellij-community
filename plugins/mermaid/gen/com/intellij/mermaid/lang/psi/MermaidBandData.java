// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidBandData extends MermaidPsiElement {

  @NotNull
  List<MermaidMarkdownValue> getMarkdownValueList();

  @NotNull
  List<MermaidString> getStringList();

}
