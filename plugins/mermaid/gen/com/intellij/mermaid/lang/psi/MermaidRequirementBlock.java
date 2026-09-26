// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MermaidRequirementBlock extends MermaidDiagramBlock {

  @NotNull
  List<MermaidRequirementIdAttribute> getRequirementIdAttributeList();

  @NotNull
  List<MermaidRequirementRiskAttribute> getRequirementRiskAttributeList();

  @NotNull
  List<MermaidRequirementTextAttribute> getRequirementTextAttributeList();

  @NotNull
  List<MermaidRequirementVerifyMethodAttribute> getRequirementVerifyMethodAttributeList();

}
