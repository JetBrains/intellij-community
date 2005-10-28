/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.commander;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 23, 2005
 */
public class TopLevelNode extends AbstractTreeNode {
  private static final Icon ourUpLevelIcon = IconLoader.getIcon("/nodes/upLevel.png");

  public TopLevelNode(Project project, Object value) {
    super(project, value);
    myName = "[ .. ]";
    myOpenIcon = myClosedIcon = ourUpLevelIcon;
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  public String getName() {
    return super.getName();
  }

  protected void update(PresentationData presentation) {
  }

}
