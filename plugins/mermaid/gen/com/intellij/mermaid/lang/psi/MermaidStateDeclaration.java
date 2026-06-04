// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidStateDeclaration extends MermaidPsiElement {

  @Nullable
  MermaidAnnotation getAnnotation();

  @Nullable
  MermaidComplexLabel getComplexLabel();

  @Nullable
  MermaidDescription getDescription();

  @Nullable
  MermaidStateDeclarationHeader getStateDeclarationHeader();

  @Nullable
  MermaidStateId getStateId();

}
