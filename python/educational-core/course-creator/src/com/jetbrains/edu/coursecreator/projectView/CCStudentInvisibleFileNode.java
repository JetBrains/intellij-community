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
public class CCStudentInvisibleFileNode extends PsiFileNode{

  public CCStudentInvisibleFileNode(Project project,
                                    PsiFile value,
                                    ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }


  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    String text = data.getPresentableText();
    data.clearText();
    data.addText(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
  }
}
