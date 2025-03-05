// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ObjectUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil;
import org.jetbrains.idea.maven.utils.MavenUIUtil;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.idea.maven.navigator.MavenProjectsNavigator.TOOL_WINDOW_PLACE_ID;
import static org.jetbrains.idea.maven.navigator.structure.MavenProjectsStructure.MavenStructureDisplayMode.*;

@ApiStatus.Internal
public abstract class MavenSimpleNode extends CachingSimpleNode implements MavenNode {
  private MavenSimpleNode myParent;
  private MavenProjectsStructure.ErrorLevel myErrorLevel = MavenProjectsStructure.ErrorLevel.NONE;
  private MavenProjectsStructure.ErrorLevel myTotalErrorLevel = null;
  protected final MavenProjectsStructure myMavenProjectsStructure;

  public enum MavenNodeType {
    PROJECT,
    GOAL,
    OTHER
  }

  MavenSimpleNode(MavenProjectsStructure structure, MavenSimpleNode parent) {
    super(structure.getProject(), null);
    myMavenProjectsStructure = structure;
    setParent(parent);
  }

  public MavenNodeType getType() {
    return MavenNodeType.OTHER;
  }

  public void setParent(MavenSimpleNode parent) {
    myParent = parent;
  }

  @Override
  public NodeDescriptor getParentDescriptor() {
    return myParent;
  }

  MavenProjectNode findParentProjectNode() {
    return ObjectUtils.doIfNotNull(myParent, it -> it.findProjectNode());
  }

  @Override
  public MavenProjectNode findProjectNode() {
    MavenSimpleNode node = this;
    while (node != null && !(node instanceof MavenProjectNode)) {
      node = node.myParent;
    }
    return (MavenProjectNode)node;
  }

  public boolean isVisible() {
    return getDisplayKind() != MavenProjectsStructure.DisplayKind.NEVER;
  }

  public MavenProjectsStructure.DisplayKind getDisplayKind() {
    var displayMode = myMavenProjectsStructure.getDisplayMode();
    if (displayMode == SHOW_ALL) return MavenProjectsStructure.DisplayKind.NORMAL;

    if (displayMode == SHOW_PROJECTS && getType() == MavenNodeType.PROJECT) return MavenProjectsStructure.DisplayKind.ALWAYS;
    if (displayMode == SHOW_GOALS && getType() == MavenNodeType.GOAL) return MavenProjectsStructure.DisplayKind.ALWAYS;

    return MavenProjectsStructure.DisplayKind.NEVER;
  }

  @Override
  protected SimpleNode[] buildChildren() {
    List<? extends MavenSimpleNode> children = doGetChildren();
    if (children.isEmpty()) return NO_CHILDREN;

    List<MavenSimpleNode> result = new ArrayList<>();
    for (MavenSimpleNode each : children) {
      if (each.isVisible()) result.add(each);
    }
    return result.toArray(new MavenSimpleNode[0]);
  }

  protected List<? extends MavenSimpleNode> doGetChildren() {
    return Collections.emptyList();
  }

  @Override
  public synchronized void cleanUpCache() {
    super.cleanUpCache();
    myTotalErrorLevel = null;
  }

  protected void childrenChanged() {
    MavenSimpleNode each = this;
    while (each != null) {
      each.cleanUpCache();
      each = (MavenSimpleNode)each.getParent();
    }
    myMavenProjectsStructure.updateUpTo(this);
  }

  public synchronized MavenProjectsStructure.ErrorLevel getTotalErrorLevel() {
    if (myTotalErrorLevel == null) {
      myTotalErrorLevel = calcTotalErrorLevel();
    }
    return myTotalErrorLevel;
  }

  private MavenProjectsStructure.ErrorLevel calcTotalErrorLevel() {
    MavenProjectsStructure.ErrorLevel childrenErrorLevel = getChildrenErrorLevel();
    return childrenErrorLevel.compareTo(myErrorLevel) > 0 ? childrenErrorLevel : myErrorLevel;
  }

  public MavenProjectsStructure.ErrorLevel getChildrenErrorLevel() {
    MavenProjectsStructure.ErrorLevel result = MavenProjectsStructure.ErrorLevel.NONE;
    for (SimpleNode each : getChildren()) {
      MavenProjectsStructure.ErrorLevel eachLevel = ((MavenSimpleNode)each).getTotalErrorLevel();
      if (eachLevel.compareTo(result) > 0) result = eachLevel;
    }
    return result;
  }

  public void setErrorLevel(MavenProjectsStructure.ErrorLevel level) {
    if (myErrorLevel == level) return;
    myErrorLevel = level;
    myMavenProjectsStructure.updateUpTo(this);
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    setNameAndTooltip(presentation, getName(), null);
  }

  protected void setNameAndTooltip(@NotNull PresentationData presentation, String name, @Nullable @NlsContexts.Tooltip String tooltip) {
    setNameAndTooltip(presentation, name, tooltip, (String)null);
  }

  protected void setNameAndTooltip(@NotNull PresentationData presentation,
                                   String name,
                                   @Nullable @NlsContexts.Tooltip String tooltip,
                                   @Nullable @NlsSafe String hint) {
    setNameAndTooltip(presentation, name, tooltip, getPlainAttributes());
    if (showDescription() && !StringUtil.isEmptyOrSpaces(hint)) {
      presentation.addText(" (" + hint + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public boolean showDescription() {
    return myMavenProjectsStructure.getDisplayMode() == SHOW_ALL;
  }

  protected void setNameAndTooltip(@NotNull PresentationData presentation,
                                   @NlsSafe String name,
                                   @Nullable @NlsContexts.Tooltip String tooltip,
                                   SimpleTextAttributes attributes) {
    presentation.clearText();
    presentation.addText(name, prepareAttributes(attributes));
    getTemplatePresentation().setTooltip(tooltip);
  }

  private SimpleTextAttributes prepareAttributes(SimpleTextAttributes from) {
    MavenProjectsStructure.ErrorLevel level = getTotalErrorLevel();
    Color waveColor = level == MavenProjectsStructure.ErrorLevel.NONE ? null : JBColor.RED;
    int style = from.getStyle();
    if (waveColor != null) style |= SimpleTextAttributes.STYLE_WAVED;
    return new SimpleTextAttributes(from.getBgColor(), from.getFgColor(), waveColor, style);
  }

  @Language("devkit-action-id")
  @Nullable
  @NonNls
  String getActionId() {
    return null;
  }

  @Nullable
  @NonNls
  String getMenuId() {
    return null;
  }

  public @Nullable VirtualFile getVirtualFile() {
    return null;
  }

  public @Nullable Navigatable getNavigatable() {
    return MavenNavigationUtil.createNavigatableForPom(getProject(), getVirtualFile());
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    String actionId = getActionId();
    if (actionId != null) {
      MavenUIUtil.executeAction(actionId, TOOL_WINDOW_PLACE_ID, inputEvent);
    }
  }
}
