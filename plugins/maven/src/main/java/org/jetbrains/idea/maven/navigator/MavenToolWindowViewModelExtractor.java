// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.PatchedDefaultMutableTreeNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.viewModel.definition.*;
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenToolWindowViewModelExtractor implements ToolWindowViewModelExtractor {

  private static final Logger myLogger = Logger.getInstance(MavenToolWindowViewModelExtractor.class);

  @Override
  public ToolWindowViewModelContent extractViewModel(ToolWindow toolWindow, Project project) {
    MavenProjectsNavigator.getInstance(project).headlessInit();
    Content mavenContent = toolWindow.getContentManager().getContents()[0];
    MavenProjectsNavigatorPanel mavenPanel = (MavenProjectsNavigatorPanel)mavenContent.getComponent();
    SimpleTree tree = mavenPanel.getTree();
    ToolWindowEx toolWindowEx = (ToolWindowEx)toolWindow;

    TreeViewModel treeModel = extractViewModel(tree);
    ToolWindowEx.ToolWindowDecoration decoration = toolWindowEx.getDecoration();
    myLogger.assertTrue(decoration != null, String.format("couldn't extract decoration of the toolwindow with id %s", toolWindow.getId()));

    DataContext context = DataManager.getInstance().getDataContext(mavenPanel);
    ActionBarViewModel actionBarViewModel = getFromRegisteredActions(context);
    return new SimpleToolWindowContent(actionBarViewModel, treeModel);
  }

  @Override
  public ToolWindowViewModelDescription extractDescription(ToolWindow toolWindow) {
    ToolWindowEx toolWindowEx = (ToolWindowEx)toolWindow;

    ToolWindowEx.ToolWindowDecoration decoration = toolWindowEx.getDecoration();
    myLogger.assertTrue(decoration != null, String.format("couldn't extract decoration of the toolwindow with id %s", toolWindow.getId()));

    return new SimpleToolWindowDescription(decoration.getIcon(), toolWindow.getId(), toolWindow.getTitle(), new ToolWindowPosition(toolWindow.getAnchor(), toolWindowEx.isSplitMode()));
  }


  private static ActionBarViewModel getFromRegisteredActions(DataContext context) {
    DefaultActionGroup defaultActionGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("Maven.NavigatorActionsToolbar");
    AnAction[] childrenActions = defaultActionGroup.getChildActionsOrStubs();

    final ArrayList<ActionViewModel> iconActions = new ArrayList<>();

    for (AnAction action : childrenActions) {


      ActionViewModel actionViewModel = getActionViewModel(context, action);

      iconActions.add(actionViewModel);
    }

    return new ActionBarViewModel(true, iconActions);
  }

  @NotNull
  private static ActionViewModel getActionViewModel(DataContext context, AnAction action) {
    Icon icon = action.getTemplatePresentation().getIcon();

    if (action instanceof ActionStub) {
      ActionStub actionStub = (ActionStub)action;
      String iconPath = actionStub.getIconPath();
      if (iconPath != null) {
        icon = IconLoader.findIcon(iconPath, action.getClass(), actionStub.getPlugin().getPluginClassLoader(), null, true);
      }
      action = ActionManager.getInstance().getAction(actionStub.getId());
    }

    if (icon == null) {
      //todo this is stub
      icon = EmptyIcon.ICON_0;
    }

    ActionViewModel actionViewModel;
    final AnAction tooltipAction = action;
    if (action instanceof ToggleAction) {
      //todo lazy tooltip text
      actionViewModel = new ToggleIconAction(icon, action.getTemplatePresentation().getText(), () -> {
        tooltipAction.actionPerformed(AnActionEvent.createFromAnAction(tooltipAction, null, "", context));
      }, true);
    }
    else if (action instanceof Separator) {
      actionViewModel = new com.intellij.ui.viewModel.definition.Separator();
    }
    else {
      actionViewModel = new IconAction(icon, action.getTemplatePresentation().getText(), () -> {
        tooltipAction.actionPerformed(AnActionEvent.createFromAnAction(tooltipAction, null, "", context));
      });
    }
    return actionViewModel;
  }

  // TODO also seems like could be made more generic @see SimpleNode
  private TreeViewModel extractViewModel(SimpleTree tree) {
    PatchedDefaultMutableTreeNode rawRoot = (PatchedDefaultMutableTreeNode)tree.getModel().getRoot();
    MavenProjectsStructure.MavenSimpleNode mavenRoot = (MavenProjectsStructure.MavenSimpleNode) rawRoot.getUserObject();
    ViewModelNode viewModelRoot = new ViewModelNode(mavenRoot.getName(), () -> {
      //do nothing
    }, true, mavenRoot.getIcon());

    processRecursive(mavenRoot, viewModelRoot);

    return new TreeViewModel(viewModelRoot, tree.isRootVisible());
  }

  // TODO move somewhere else as it may be useful for other toolwindows
  private static void processRecursive(SimpleNode currentSimpleNode, ViewModelNode currentViewModelNode) {
    SimpleNode[] children = currentSimpleNode.getChildren();
    List<ViewModelNode> childrenViewModel = new ArrayList<>();
    for (SimpleNode child : children) {
      ViewModelNode childViewModelNode = new ViewModelNode(child.getName(), () -> {
      }, hasChildren(child), child.getIcon());
      childrenViewModel.add(childViewModelNode);

      processRecursive(child, childViewModelNode);
    }

    currentViewModelNode.setChildren(childrenViewModel);
  }

  private static Boolean hasChildren(SimpleNode child) {
    if (child.isAlwaysShowPlus()) return true;
    if (child.isAlwaysLeaf()) return false;
    return child.getChildCount() > 0;
  }

  @Override
  public boolean isApplicable(String toolWindowId) {
    return toolWindowId == MavenProjectsNavigator.TOOL_WINDOW_ID;
  }
}
