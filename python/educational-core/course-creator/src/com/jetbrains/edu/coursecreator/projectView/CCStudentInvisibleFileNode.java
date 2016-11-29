package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;

/**
 * represents a file which is invisible for student in student mode
 */
public class CCStudentInvisibleFileNode extends PsiFileNode {
  private final String myName;

  public CCStudentInvisibleFileNode(Project project,
                                    PsiFile value,
                                    ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myName = value.getName();
  }

  public CCStudentInvisibleFileNode(Project project,
                                    PsiFile value,
                                    ViewSettings viewSettings,
                                    String name) {
    super(project, value, viewSettings);
    myName = name;
  }


  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    data.clearText();
    data.addText(myName, SimpleTextAttributes.GRAY_ATTRIBUTES);
  }
}
