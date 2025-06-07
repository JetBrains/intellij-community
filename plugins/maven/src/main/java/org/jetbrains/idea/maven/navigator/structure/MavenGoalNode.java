// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.project.MavenProject;

import java.awt.*;

import static icons.ExternalSystemIcons.Task;

abstract class MavenGoalNode extends MavenSimpleNode implements GoalNode {
  private final MavenProject myMavenProject;
  private final String myGoal;
  private final String myDisplayName;

  MavenGoalNode(MavenProjectsStructure structure, GoalsGroupNode parent, String goal, String displayName) {
    super(structure, parent);
    myMavenProject = findParentProjectNode().getMavenProject();
    myGoal = goal;
    myDisplayName = displayName;
    getTemplatePresentation().setIcon(Task);
  }

  @Override
  public MavenNodeType getType() {
    return MavenNodeType.GOAL;
  }

  @Override
  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  @Override
  public String getProjectPath() {
    return myMavenProject.getPath();
  }

  @Override
  public String getGoal() {
    return myGoal;
  }

  @Override
  public String getName() {
    return myDisplayName;
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    String s1 = StringUtil.nullize(myMavenProjectsStructure.getShortcutsManager().getDescription(myMavenProject, myGoal));
    String s2 = StringUtil.nullize(myMavenProjectsStructure.getTasksManager().getDescription(myMavenProject, myGoal));

    String hint;
    if (s1 == null) {
      hint = s2;
    }
    else if (s2 == null) {
      hint = s1;
    }
    else {
      hint = s1 + ", " + s2;
    }

    setNameAndTooltip(presentation, getName(), null, hint);
  }

  @Override
  protected SimpleTextAttributes getPlainAttributes() {
    SimpleTextAttributes original = super.getPlainAttributes();

    int style = original.getStyle();
    Color color = original.getFgColor();
    boolean custom = false;

    if ("test".equals(myGoal) && MavenRunner.getInstance(myProject).getSettings().isSkipTests()) {
      color = SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
      style |= SimpleTextAttributes.STYLE_STRIKEOUT;
      custom = true;
    }
    if (myGoal.equals(myMavenProject.getDefaultGoal())) {
      style |= SimpleTextAttributes.STYLE_BOLD;
      custom = true;
    }
    if (custom) return original.derive(style, color, null, null);
    return original;
  }

  @Override
  protected @Nullable @NonNls String getActionId() {
    return "Maven.RunBuild";
  }

  @Override
  protected @Nullable @NonNls String getMenuId() {
    return "Maven.BuildMenu";
  }

}
