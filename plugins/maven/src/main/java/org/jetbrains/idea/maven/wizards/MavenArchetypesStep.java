// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenArchetypeManager;
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
import java.util.List;
import java.util.*;

/**
 * @author Dmitry Avdeev
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
  private final AbstractMavenModuleBuilder myBuilder;
  @Nullable private final StepAdapter myStep;

  public MavenArchetypesStep(AbstractMavenModuleBuilder builder, @Nullable StepAdapter step) {
    myBuilder = builder;
    myStep = step;
    Disposer.register(this, myLoadingIcon);

    myArchetypesTree = new Tree();
    myArchetypesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
    JScrollPane archetypesScrollPane = ScrollPaneFactory.createScrollPane(myArchetypesTree);

    myArchetypesPanel.add(archetypesScrollPane, "archetypes");

    JPanel loadingPanel = new JPanel(new GridBagLayout());
    JPanel bp = new JPanel(new BorderLayout(10, 10));
    bp.add(new JLabel(MavenWizardBundle.message("maven.structure.wizard.loading.archetypes.list")), BorderLayout.NORTH);
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
      @Override
      public void actionPerformed(ActionEvent e) {
        doAddArchetype();
      }
    });

    myArchetypesTree.setRootVisible(false);
    myArchetypesTree.setShowsRootHandles(true);
    myArchetypesTree.setCellRenderer(new MyRenderer());
    myArchetypesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myArchetypesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateArchetypeDescription();
        archetypeMayBeChanged();
      }
    });

    new TreeSpeedSearch(myArchetypesTree, false, path -> {
      MavenArchetype info = getArchetypeInfoFromPathComponent(path.getLastPathComponent());
      return info.groupId + ":" + info.artifactId + ":" + info.version;
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

    list.sort((o1, o2) -> {
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

    myLoadingIcon.setBackground(RenderingUtil.getBackground(myArchetypesTree));

    ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "loading");

    final Object currentUpdaterMarker = new Object();
    myCurrentUpdaterMarker = currentUpdaterMarker;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final Set<MavenArchetype> archetypes = MavenArchetypeManager.getInstance(findProject()).getArchetypes();

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

  // todo DefaultProject usage may lead to plugin classloader leak on the plugin unload
  @NotNull
  private static Project findProject() {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project[] openProjects = projectManager.getOpenProjects();
    return openProjects.length != 0 ? openProjects[0] : projectManager.getDefaultProject();
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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      MavenIndicesManager.getInstance(findProject()).addArchetype(archetype);
      ApplicationManager.getApplication().invokeLater(() -> updateArchetypesList(archetype));
    });
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
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
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
