// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidClassBlock extends MermaidDiagramBlock {

  @NotNull
  List<MermaidAnnotation> getAnnotationList();

  @NotNull
  List<MermaidAttribute> getAttributeList();

  @NotNull
  List<MermaidDirective> getDirectiveList();

}
