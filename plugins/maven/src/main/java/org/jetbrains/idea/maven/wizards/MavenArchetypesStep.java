/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.MavenArchetype;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 24.09.13
 */
public class MavenArchetypesStep extends ModuleWizardStep implements Disposable {

  private JCheckBox myUseArchetypeCheckBox;
  private JButton myAddArchetypeButton;
  private JPanel myArchetypesPanel;
  private final Tree myArchetypesTree;
  private JScrollPane myArchetypeDescriptionScrollPane;
  private JPanel myMainPanel;
  private JTextArea myArchetypeDescriptionField;

  private Object myCurrentUpdaterMarker;
  private final AsyncProcessIcon myLoadingIcon = new AsyncProcessIcon.Big(getClass() + ".loading");

  private boolean skipUpdateUI;
  private final MavenModuleBuilder myBuilder;
  @Nullable private final StepAdapter myStep;

  public MavenArchetypesStep(MavenModuleBuilder builder, @Nullable StepAdapter step) {
    myBuilder = builder;
    myStep = step;
    Disposer.register(this, myLoadingIcon);

    myArchetypesTree = new Tree();
    myArchetypesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
    JScrollPane archetypesScrollPane = ScrollPaneFactory.createScrollPane(myArchetypesTree);

    myArchetypesPanel.add(archetypesScrollPane, "archetypes");

    JPanel loadingPanel = new JPanel(new GridBagLayout());
    JPanel bp = new JPanel(new BorderLayout(10, 10));
    bp.add(new JLabel("Loading archetype list..."), BorderLayout.NORTH);
    bp.add(myLoadingIcon, BorderLayout.CENTER);

    loadingPanel.add(bp, new GridBagConstraints());

    myArchetypesPanel.add(ScrollPaneFactory.createScrollPane(loadingPanel), "loading");
    ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "archetypes");


    myUseArchetypeCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateComponents();
        archetypeMayBeChanged();
      }
    });

    myAddArchetypeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doAddArchetype();
      }
    });

    myArchetypesTree.setRootVisible(false);
    myArchetypesTree.setShowsRootHandles(true);
    myArchetypesTree.setCellRenderer(new MyRenderer());
    myArchetypesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myArchetypesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateArchetypeDescription();
        archetypeMayBeChanged();
      }
    });

    new TreeSpeedSearch(myArchetypesTree, new Convertor<TreePath, String>() {
      public String convert(TreePath path) {
        MavenArchetype info = getArchetypeInfoFromPathComponent(path.getLastPathComponent());
        return info.groupId + ":" + info.artifactId + ":" + info.version;
      }
    }).setComparator(new SpeedSearchComparator(false));

    myArchetypeDescriptionField.setEditable(false);
    myArchetypeDescriptionField.setBackground(UIUtil.getPanelBackground());

    requestUpdate();
    updateComponents();
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  private void updateComponents() {
    boolean archetypesEnabled = myUseArchetypeCheckBox.isSelected();
    myAddArchetypeButton.setEnabled(archetypesEnabled);
    myArchetypesTree.setEnabled(archetypesEnabled);
    myArchetypesTree.setBackground(archetypesEnabled ? UIUtil.getListBackground() : UIUtil.getPanelBackground());

  }

  @Nullable
  public MavenArchetype getSelectedArchetype() {
    if (!myUseArchetypeCheckBox.isSelected() || myArchetypesTree.isSelectionEmpty()) return null;
    return getArchetypeInfoFromPathComponent(myArchetypesTree.getLastSelectedPathComponent());
  }

  private static MavenArchetype getArchetypeInfoFromPathComponent(Object sel) {
    return (MavenArchetype)((DefaultMutableTreeNode)sel).getUserObject();
  }

  private void updateArchetypeDescription() {
    MavenArchetype sel = getSelectedArchetype();
    String desc = sel == null ? null : sel.description;
    if (StringUtil.isEmptyOrSpaces(desc)) {
      myArchetypeDescriptionScrollPane.setVisible(false);
    }
    else {
      myArchetypeDescriptionScrollPane.setVisible(true);
      myArchetypeDescriptionField.setText(desc);
    }
  }

  @Nullable
  private static TreePath findNodePath(MavenArchetype object, TreeModel model, Object parent) {
    for (int i = 0; i < model.getChildCount(parent); i++) {
      DefaultMutableTreeNode each = (DefaultMutableTreeNode)model.getChild(parent, i);
      if (each.getUserObject().equals(object)) return new TreePath(each.getPath());

      TreePath result = findNodePath(object, model, each);
      if (result != null) return result;
    }
    return null;
  }

  private static TreeNode groupAndSortArchetypes(Set<MavenArchetype> archetypes) {
    List<MavenArchetype> list = new ArrayList<>(archetypes);

    Collections.sort(list, (o1, o2) -> {
      String key1 = o1.groupId + ":" + o1.artifactId;
      String key2 = o2.groupId + ":" + o2.artifactId;

      int result = key1.compareToIgnoreCase(key2);
      if (result != 0) return result;

      return o2.version.compareToIgnoreCase(o1.version);
    });

    Map<String, List<MavenArchetype>> map = new TreeMap<>();

    for (MavenArchetype each : list) {
      String key = each.groupId + ":" + each.artifactId;
      List<MavenArchetype> versions = map.get(key);
      if (versions == null) {
        versions = new ArrayList<>();
        map.put(key, versions);
      }
      versions.add(each);
    }

    DefaultMutableTreeNode result = new DefaultMutableTreeNode("root", true);
    for (List<MavenArchetype> each : map.values()) {
      MavenArchetype eachArchetype = each.get(0);
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(eachArchetype, true);
      for (MavenArchetype eachVersion : each) {
        DefaultMutableTreeNode versionNode = new DefaultMutableTreeNode(eachVersion, false);
        node.add(versionNode);
      }
      result.add(node);
    }

    return result;
  }

  public void requestUpdate() {

    MavenArchetype selectedArch = getSelectedArchetype();
    if (selectedArch == null) {
      selectedArch = myBuilder.getArchetype();
    }
    if (selectedArch != null) myUseArchetypeCheckBox.setSelected(true);

    if (myArchetypesTree.getRowCount() == 0) updateArchetypesList(selectedArch);
  }

  public void updateArchetypesList(final MavenArchetype selected) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myLoadingIcon.setBackground(myArchetypesTree.getBackground());

    ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "loading");

    final Object currentUpdaterMarker = new Object();
    myCurrentUpdaterMarker = currentUpdaterMarker;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final Set<MavenArchetype> archetypes = MavenIndicesManager.getInstance().getArchetypes();

      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (currentUpdaterMarker != myCurrentUpdaterMarker) return; // Other updater has been run.

        ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "archetypes");

        TreeNode root = groupAndSortArchetypes(archetypes);
        TreeModel model = new DefaultTreeModel(root);
        myArchetypesTree.setModel(model);

        if (selected != null) {
          TreePath path = findNodePath(selected, model, model.getRoot());
          if (path != null) {
            myArchetypesTree.expandPath(path.getParentPath());
            TreeUtil.selectPath(myArchetypesTree, path, true);
          }
        }

        updateArchetypeDescription();
      });
    });
  }

  public boolean isSkipUpdateUI() {
    return skipUpdateUI;
  }

  private void archetypeMayBeChanged() {
    MavenArchetype selectedArchetype = getSelectedArchetype();
    if (((myBuilder.getArchetype() == null) != (selectedArchetype == null))) {
      myBuilder.setArchetype(selectedArchetype);
      skipUpdateUI = true;
      try {
        if (myStep != null) {
          myStep.fireStateChanged();
        }
      }
      finally {
        skipUpdateUI = false;
      }
    }
  }


  private void doAddArchetype() {
    MavenAddArchetypeDialog dialog = new MavenAddArchetypeDialog(myMainPanel);
    if (!dialog.showAndGet()) {
      return;
    }

    MavenArchetype archetype = dialog.getArchetype();
    MavenIndicesManager.getInstance().addArchetype(archetype);
    updateArchetypesList(archetype);
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getComponent() {
    return getMainPanel();
  }

  @Override
  public void updateDataModel() {
    MavenArchetype selectedArchetype = getSelectedArchetype();
    myBuilder.setArchetype(selectedArchetype);
  }

  private static class MyRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (!(userObject instanceof MavenArchetype)) return;

      MavenArchetype info = (MavenArchetype)userObject;

      if (leaf) {
        append(info.artifactId, SimpleTextAttributes.GRAY_ATTRIBUTES);
        append(":" + info.version, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        append(info.groupId + ":", SimpleTextAttributes.GRAY_ATTRIBUTES);
        append(info.artifactId, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

}
