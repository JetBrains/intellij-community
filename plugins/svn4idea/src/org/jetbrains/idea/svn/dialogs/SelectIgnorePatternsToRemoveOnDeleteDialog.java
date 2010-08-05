/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.idea.svn.IgnoredFileInfo;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class SelectIgnorePatternsToRemoveOnDeleteDialog extends DialogWrapper {
  private final JPanel myPanel;
  private final CheckboxTree myTree;
  private final Collection<IgnoredFileInfo> myResult;

  public SelectIgnorePatternsToRemoveOnDeleteDialog(final Project project, final Map<String, IgnoredFileInfo> data) {
    super(project, true);

    myResult = new ArrayList<IgnoredFileInfo>();

    myPanel = new JPanel(new GridBagLayout());
    myTree = new CheckboxTree(new MyRenderer(), createTree(data), new CheckboxTreeBase.CheckPolicy(false, false, true, true));
    myTree.setBorder(BorderFactory.createLineBorder(UIUtil.getHeaderActiveColor(), 1));

    final JLabel prompt = new JLabel(SvnBundle.message("svn.dialog.select.ignore.patterns.to.remove.prompt"));
    prompt.setUI(new MultiLineLabelUI());

    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0);

    gb.insets.bottom = 5;
    myPanel.add(prompt, gb);
    
    gb.insets.top = 5;
    ++ gb.gridy;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.BOTH;
    gb.weightx = 1;
    gb.weighty = 1;
    myPanel.add(myTree, gb);
    
    ++ gb.gridx;
    gb.anchor = GridBagConstraints.EAST;
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    gb.weighty = 0;
    myPanel.add(createTreeToolbarPanel().getComponent(), gb);

    setTitle(SvnBundle.message("svn.dialog.select.ignore.patterns.to.remove.title"));

    init();
    TreeUtil.expandAll(myTree);
  }

  @Override
  protected void doOKAction() {
    final TreeModel treeModel = myTree.getModel();
    final CheckedTreeNode root = (CheckedTreeNode)treeModel.getRoot();
    final Enumeration files = root.children();

    while (files.hasMoreElements()) {
      final MyFileNode fileNode = (MyFileNode) files.nextElement();
      final IgnoredFileInfo info = new IgnoredFileInfo(fileNode.myFile, fileNode.myPropValue);
      final Enumeration patterns = fileNode.children();

      while (patterns.hasMoreElements()) {
        final MyPatternNode patternNode = (MyPatternNode) patterns.nextElement();
        if (patternNode.isChecked()) {
          info.addPattern(patternNode.myPattern);
        }
      }
      if (! info.getPatterns().isEmpty()) {
        myResult.add(info);
      }
    }

    super.doOKAction();
  }

  public Collection<IgnoredFileInfo> getResult() {
    return myResult;
  }

  private ActionToolbar createTreeToolbarPanel() {
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();

    TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 3);
      }

      public boolean canCollapse() {
        return true;
      }
    };

    final AnAction expandAllAction = actionManager.createExpandAllAction(treeExpander, myTree);
    final AnAction collapseAllAction = actionManager.createCollapseAllAction(treeExpander, myTree);
    final SelectAllAction selectAllAction = new SelectAllAction(true);
    final SelectAllAction unSelectAllAction = new SelectAllAction(false);

    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)), myTree);
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)), myTree);
    selectAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      myTree);

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(expandAllAction);
    actions.add(collapseAllAction);
    actions.add(selectAllAction);
    actions.add(unSelectAllAction);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false);
  }

  private CheckedTreeNode createTree(final Map<String, IgnoredFileInfo> data) {
    final CheckedTreeNode root = new CheckedTreeNode("root");

    final List<String> dirs = new ArrayList<String>(data.keySet());
    Collections.sort(dirs);
    for (String dir : dirs) {
      final IgnoredFileInfo info = data.get(dir);

      final MyFileNode fileNode = new MyFileNode(info.getFile(), info.getOldPatterns());
      root.add(fileNode);
      final List<String> patterns = info.getPatterns();
      Collections.sort(patterns);
      for (String pattern : patterns) {
        final MyPatternNode patternNode = new MyPatternNode(pattern);
        fileNode.add(patternNode);
      }
    }
    return root;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private class MyRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final ColoredTreeCellRenderer textRenderer = getTextRenderer();
      if (value instanceof MyConsumer) {
        ((MyConsumer) value).consume(textRenderer);
      }
    }
  }

  private interface MyConsumer extends Consumer<ColoredTreeCellRenderer> {}

  private final static SimpleTextAttributes ourIgnoredAttributes =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.IGNORED.getColor());

  private class MyFileNode extends CheckedTreeNode implements MyConsumer, Comparable<MyFileNode> {
    private final File myFile;
    private final Set<String> myPropValue;
    private final String myParentPath;

    private MyFileNode(final File file, final Set<String> propValue) {
      super(file);
      myPropValue = propValue;
      myFile = file;
      myParentPath = FileUtil.toSystemIndependentName(file.getParent());

      setEnabled(false);
    }

    public void consume(ColoredTreeCellRenderer renderer) {
      renderer.append(myFile.getName(), ourIgnoredAttributes);
      renderer.append(" (" + myParentPath + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      renderer.setIcon(Icons.DIRECTORY_CLOSED_ICON);
    }

    public int compareTo(MyFileNode o) {
      final int parentCompare = myParentPath.compareTo(o.myParentPath);
      if (parentCompare == 0) {
        return myFile.compareTo(o.myFile);
      }
      return parentCompare;
    }
  }

  public class MyPatternNode extends CheckedTreeNode implements MyConsumer {
    private final String myPattern;

    private MyPatternNode(final String pattern) {
      super(pattern);
      myPattern = pattern;
    }

    public void consume(ColoredTreeCellRenderer coloredTreeCellRenderer) {
      coloredTreeCellRenderer.append(myPattern, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    public String getPattern() {
      return myPattern;
    }

    public File getFile() {
      return (File) ((MyFileNode) getParent()).getUserObject();
    }
  }

  private class SelectAllAction extends AnAction {
    private final boolean mySelect;

    private SelectAllAction(boolean select) {
      super(select ? "Select All" : "Unselect All", select ? "Select all items" : "Unselect all items",
            IconLoader.getIcon(select ? "/actions/selectall.png" : "/actions/unselectall.png"));
      mySelect = select;
    }

    public void actionPerformed(AnActionEvent e) {
      final TreeModel treeModel = myTree.getModel();
      final CheckedTreeNode root = (CheckedTreeNode) treeModel.getRoot();
      select(root);
      myTree.repaint();
    }

    private void select(final CheckedTreeNode node) {
      if (node instanceof MyPatternNode) {
        node.setChecked(mySelect);
      } else {
        final int cnt = node.getChildCount();
        for (int i = 0; i < cnt; i++) {
          select((CheckedTreeNode) node.getChildAt(i));
        }
      }
    }
  }
}
