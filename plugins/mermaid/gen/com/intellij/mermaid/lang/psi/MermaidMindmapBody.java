// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidMindmapBody extends MermaidDiagramBlock {

  @NotNull
  List<MermaidDirective> getDirectiveList();

  @NotNull
  List<MermaidIconStatement> getIconStatementList();

  @NotNull
  List<MermaidMindmapClassStatement> getMindmapClassStatementList();

  @NotNull
  List<MermaidMindmapNodeStatement> getMindmapNodeStatementList();

}
