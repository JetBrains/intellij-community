package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class StudyDirectoryNode extends PsiDirectoryNode {
  protected static final JBColor LIGHT_GREEN = new JBColor(new Color(0, 134, 0), new Color(98, 150, 85));

  public StudyDirectoryNode(@NotNull final Project project,
                            PsiDirectory value,
                            ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected void setPresentation(PresentationData data, String name, Icon icon) {
    data.setPresentableText(name);
    data.setIcon(icon);
  }

  protected static void updatePresentation(PresentationData data, String name, JBColor color, Icon icon, @Nullable String additionalInfo) {
    data.clearText();
    data.addText(name, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
    if (additionalInfo != null) {
      data.addText(" (" + additionalInfo + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    data.setIcon(icon);
  }

  @Override
  protected boolean hasProblemFileBeneath() {
    return false;
  }

  @Nullable
  public abstract AbstractTreeNode modifyChildNode(AbstractTreeNode childNode);

  public abstract PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory value);

}
