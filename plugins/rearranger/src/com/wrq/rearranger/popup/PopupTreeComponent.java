/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.wrq.rearranger.Rearranger;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;
import com.wrq.rearranger.util.IconUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.List;

/** Builds a JTree that can be used in a file structure popup or live rearranger popup. */
public class PopupTreeComponent {
  private static final Logger LOG = Logger.getInstance("#" + PopupTreeComponent.class.getName());
  private final FilePopupEntry myPsiFileEntry;
  private final List<RuleInstance> myResultRuleInstances;
  private final RearrangerSettings  settings;
  private       boolean             rearrangementOccurred;

  public PopupTreeComponent(RearrangerSettings settings,
                            List<RuleInstance> resultRuleInstances,
                            final FilePopupEntry psiFileEntry)
  {
    this.settings = settings;
    this.myResultRuleInstances = resultRuleInstances;
    this.myPsiFileEntry = psiFileEntry;
  }

  public boolean isRearrangementOccurred() {
    return rearrangementOccurred;
  }

  public void setRearrangementOccurred(boolean rearrangementOccurred) {
    this.rearrangementOccurred = rearrangementOccurred;
  }

  public JTree createTree() {
    DefaultMutableTreeNode top = createAllNodes();
    final JTree tree = new JTree(top);
    tree.putClientProperty("JTree.lineStyle", "Angled");
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);
    tree.setCellRenderer(new JavaObjectRenderer());
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setEditable(false);
    return tree;
  }

  public PopupTree createLiveRearrangerTree() {
    DefaultMutableTreeNode top = createAllNodes();
    // discard top nodes if only one child
//        while (top.getChildCount() == 1) {        // TODO - enable
//            // remove that child, make its child(ren) top's child(ren).
//            DefaultMutableTreeNode child = (DefaultMutableTreeNode) top.getChildAt(0);
//            child.removeFromParent();
//            for (int i = 0; i < child.getChildCount(); i++) {
//                DefaultMutableTreeNode grandchild = (DefaultMutableTreeNode) child.getChildAt(i);
//                top.add(grandchild);
//            }
//        }
    final PopupTree tree = new PopupTree(top, this);
    tree.putClientProperty("JTree.lineStyle", "Angled");
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);
    tree.setCellRenderer(new JavaObjectRenderer());
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setEditable(false);
    tree.getSelectionModel().addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          LOG.debug("selection changed as follows (" + e.getPaths().length + " path(s)):");
          TreePath lastPathAdded = null;
          int i = 0;

          for (TreePath path : e.getPaths()) {
            if (e.isAddedPath(path)) {
              lastPathAdded = path;
            }
            LOG.debug(
              "path " +
              i +
              (e.isAddedPath(path) ? " added:" : " removed:") +
              path
            );
            i++;
          }
          if (lastPathAdded == null) {
            // nothing added, so all selected paths still have same parent.
            return;
          }
          /**
           * ensure that all selection rows have the same parent.  If not, take the last added
           * path, and remove any rows with a different parent.  (So simply start with the last
           * added path and remove any rows with a different parent.)
           */
          DefaultMutableTreeNode lastNodeAdded = null;
          lastNodeAdded = (DefaultMutableTreeNode)lastPathAdded.getLastPathComponent();
          boolean haveSameParent = true;
          for (TreePath path : tree.getSelectionPaths()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            if (lastNodeAdded == null) {
              lastNodeAdded = node;
            }
            if (node != lastNodeAdded) {
              if (tree.haveCommonAncestors(lastNodeAdded, node) < 0) {
                // parents not identical, so remove node from selection.
                haveSameParent = false;
                break;
              }
            }
          }
          if (!haveSameParent) {
            // to avoid reentry into this listener, temporarily remove it
            tree.getSelectionModel().removeTreeSelectionListener(this);
            tree.clearSelection();
            tree.addSelectionPath(lastPathAdded);
            tree.getSelectionModel().addTreeSelectionListener(this);
          }
        }
      }
    );

    AbstractAction moveUpAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        LOG.debug("moveUpAction, " + tree.getSelectionPaths().length + " paths in selection");
        if (tree.getSelectionPaths().length == 0) {
          return;
        }
        /**
         * find first selection row.
         */
        int selectionRow = Integer.MAX_VALUE;
        for (TreePath path : tree.getSelectionPaths()) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          int nodeIndex = node.getParent().getIndex(node);
          if (selectionRow > nodeIndex) {
            selectionRow = nodeIndex;
          }
        }
        if (selectionRow > 0) {
          selectionRow--;
        }
        tree.moveSelection(selectionRow, true);
      }
    };
    AbstractAction moveDownAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        LOG.debug("moveDownAction, " + tree.getSelectionPaths().length + " paths in selection");
        if (tree.getSelectionPaths().length == 0) {
          return;
        }
        /**
         * find last selection row.
         */
        int selectionRow = -1;
        for (TreePath path : tree.getSelectionPaths()) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          final DefaultMutableTreeNode parent = ((DefaultMutableTreeNode)node.getParent());
          int nodeIndex = parent.getIndex(node);
          if (selectionRow < nodeIndex) {
            selectionRow = nodeIndex;
          }
        }
        selectionRow++;
        tree.moveSelection(selectionRow, false);
      }
    };
    AbstractAction enterKeyAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        LOG.debug("exit with enter key");
        tree.setExitedWithEnterKey(true);
        for (Container p = tree.getParent(); p != null; p = p.getParent()) {
          if (p instanceof JDialog) {
            p.setVisible(false);
          }
        }
      }
    };
    tree.getInputMap().put(
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      "enterKeyAction"
    );
    tree.getActionMap().put("enterKeyAction", enterKeyAction);
    tree.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
      KeyStroke.getKeyStroke(
        KeyEvent.VK_UP,
        InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
      ),
      "moveUpAction"
    );

    tree.getActionMap().put("moveUpAction", moveUpAction);
    tree.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
      KeyStroke.getKeyStroke(
        KeyEvent.VK_DOWN,
        InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
      ),
      "moveDownAction"
    );

    tree.getActionMap().put("moveDownAction", moveDownAction);
    tree.setSelectionPath(tree.getPathForRow(0));
    return tree;
  }

  public DefaultMutableTreeNode createAllNodes() {
    DefaultMutableTreeNode top = new RearrangerTreeNode(myPsiFileEntry, "root");
    createNodes(top);
    return top;
  }

  private void createNodes(DefaultMutableTreeNode top) {
    for (RuleInstance ruleInstance : myResultRuleInstances) {
      ruleInstance.addRuleInstanceToPopupTree(top, settings);
    }
  }

  class JavaObjectRenderer
    extends DefaultTreeCellRenderer
  {
    Icon findIcon(String iconName) {
      if (iconName == null) {
        return null;
      }
      URL iconURL = this.getClass().getClassLoader().getResource(iconName + ".png");
      if (iconURL == null) {
        return IconUtil.getIcon(iconName);
      }
      else {
        return new ImageIcon(iconURL, Rearranger.COMPONENT_NAME);
      }
    }

    public Component getTreeCellRendererComponent(final JTree tree,
                                                  final Object value,
                                                  final boolean sel,
                                                  final boolean expanded,
                                                  final boolean leaf,
                                                  final int row,
                                                  final boolean hasFocus)
    {
      Object userObject = null;
      if (value instanceof DefaultMutableTreeNode) {
        userObject = ((DefaultMutableTreeNode)value).getUserObject();
      }
      // obtain a component that will render the appropriate item.
      if (userObject instanceof FilePopupEntry) {
        FilePopupEntry entry = (FilePopupEntry)userObject;
        final String iconTypeName = entry.getTypeIconName();
        final String[] iconNames = entry.getAdditionalIconNames();
        final JLabel textLabel = entry.getPopupEntryText(settings);
        JPanel panel = new JPanel(new GridBagLayout()) {
          public void paint(Graphics g) {
//                        System.out.println("paint " + textLabel.getText() + ", sel=" + sel +
//                                ", hasFocus=" + hasFocus +
//                                ", selected=" + selected +
//                                ", bg=" + getBackground().toString().replaceFirst(".*\\[", "[") +
//                                ", bg sel=" + getBackgroundSelectionColor().toString().replaceFirst(".*\\[", "[") +
//                                ", border=" + getBorderSelectionColor().toString().replaceFirst(".*\\[", "[")
//                        );
            if (sel && !hasFocus) {
              Color bgColor = getBackground();
              setBackground(Color.WHITE);
              super.paint(g);
              setBackground(bgColor);
              Color bsColor = getBorderSelectionColor();

              if (bsColor != null) {
                g.setColor(bsColor);
                if (getComponentOrientation().isLeftToRight()) {
                  g.drawRect(
                    0, 0, getWidth() - 1,
                    getHeight() - 1
                  );
                }
                else {
                  g.drawRect(
                    0, 0, getWidth() - 1,
                    getHeight() - 1
                  );
                }
              }
            }
            else {
              super.paint(g);
            }
          }
        };
        Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
        constraints.weightedLastRow();
        Icon typeIcon = findIcon(iconTypeName == null ? "fileTypes/unknown" : iconTypeName);
        panel.setBackground(Color.WHITE);
        if (sel) {
//                    Color fg = textLabel.getForeground();
//                    Color bg = textLabel.getBackground();
//                    textLabel.setForeground(bg);
//                    textLabel.setBackground(new Color(181, 190, 214));
//                    Color fg = panel.getForeground();
//                    Color bg = panel.getBackground();
//                    panel.setForeground(bg);
          panel.setBackground(new Color(181, 190, 214));
        }
        else {
          textLabel.setBackground(Color.WHITE);
        }
        if (typeIcon != null) {
          JLabel typeLabel = new JLabel(typeIcon);
          panel.add(typeLabel, constraints.nextCol());
        }
        if (iconNames != null) {
          for (String iconName : iconNames) {
            Icon addlIcon = findIcon(iconName);
            JLabel plLabel = new JLabel(addlIcon);
            panel.add(plLabel, constraints.nextCol());
          }
        }
        constraints.insets = new Insets(0, 3, 0, 0);
        panel.add(textLabel, constraints.lastCol());
        panel.repaint();
        return panel;
      }
      LOG.debug("getTreeCellRendererComponent: userObject not IFilePopupEntry;" + userObject);
      return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
    }
  }
}
