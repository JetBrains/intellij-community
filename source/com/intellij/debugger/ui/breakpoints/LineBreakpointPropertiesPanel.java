/*
 * Class LineBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

public class LineBreakpointPropertiesPanel extends BreakpointPropertiesPanel {
  public LineBreakpointPropertiesPanel(Project project) {
    super(project);
  }
}