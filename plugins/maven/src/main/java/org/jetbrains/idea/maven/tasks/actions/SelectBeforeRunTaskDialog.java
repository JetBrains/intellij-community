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
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;

public class SelectBeforeRunTaskDialog extends DialogWrapper {
  private final RunManagerEx myRunManager;
  private final MavenTasksManager myTasksManager;

  private final MavenProject myMavenProject;
  private final String myGoal;

  private DefaultMutableTreeNode myRoot;

  public SelectBeforeRunTaskDialog(Project project, MavenProject mavenProject, String goal) {
    super(project, true);
    myRunManager = RunManagerEx.getInstanceEx(project);
    myTasksManager = MavenTasksManager.getInstance(project);
    myMavenProject = mavenProject;
    myGoal = goal;

    setTitle(TasksBundle.message("maven.tasks.before.run.action"));
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    myRoot = buildNodes();
    final Tree tree = new Tree(myRoot);

    final MyTreeCellRenderer cellRenderer = new MyTreeCellRenderer();

    tree.setCellRenderer(cellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();
//    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
//    new TreeSpeedSearch(tree);

    tree.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          Rectangle rowBounds = tree.getRowBounds(row);
          cellRenderer.setBounds(rowBounds);
          Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();

          checkBounds.setLocation(rowBounds.getLocation());
          if (checkBounds.contains(e.getPoint())) {
            toggleNode(tree, (DefaultMutableTreeNode)tree.getPathForRow(row).getLastPathComponent());
            e.consume();
            tree.setSelectionRow(row);
          }
        }
      }
    });

    tree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath treePath = tree.getLeadSelectionPath();
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          toggleNode(tree, node);
          e.consume();
        }
      }
    });

    expandChecked(tree);

    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setPreferredSize(new Dimension(400, 400));
    panel.add(scrollPane, BorderLayout.CENTER);
    return panel;
  }

  private static void expandChecked(Tree tree) {
    TreeNode root = (TreeNode)tree.getModel().getRoot();
    Enumeration factories = root.children();
    ArrayList<TreeNode[]> toExpand = new ArrayList<TreeNode[]>();
    while (factories.hasMoreElements()) {
      DefaultMutableTreeNode factoryNode = (DefaultMutableTreeNode)factories.nextElement();
      Enumeration configurations = factoryNode.children();
      while (configurations.hasMoreElements()) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)configurations.nextElement();
        ConfigurationDescriptor config = (ConfigurationDescriptor)node.getUserObject();
        if (config.isChecked()) {
          toExpand.add(factoryNode.getPath());
          break;
        }
      }
    }
    for (TreeNode[] treeNodes : toExpand) {
      tree.expandPath(new TreePath(treeNodes));
    }
  }

  private static void toggleNode(JTree tree, DefaultMutableTreeNode node) {
    Descriptor descriptor = (Descriptor)node.getUserObject();
    descriptor.setChecked(!descriptor.isChecked());
    tree.repaint();
  }

  private DefaultMutableTreeNode buildNodes() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Descriptor());
    ConfigurationType[] configurationFactories = myRunManager.getConfigurationFactories();

    for (final ConfigurationType type : configurationFactories) {
      final Icon icon = type.getIcon();
      DefaultMutableTreeNode typeNode =
        new DefaultMutableTreeNode(new ConfigurationTypeDescriptor(type, icon, isAssigned(type, null)));
      root.add(typeNode);
      final Set<String> addedNames = StringSetSpinAllocator.alloc();
      try {
        RunConfiguration[] configurations = myRunManager.getConfigurations(type);
        for (final RunConfiguration configuration : configurations) {
          final String configurationName = configuration.getName();
          if (addedNames.contains(configurationName)) {
            // add only the first configuration if more than one has the same name
            continue;
          }
          addedNames.add(configurationName);
          typeNode.add(new DefaultMutableTreeNode(
            new ConfigurationDescriptor(type, configuration, configurationName, isAssigned(type, configuration))));
        }
      }
      finally {
        StringSetSpinAllocator.dispose(addedNames);
      }
    }

    return root;
  }

  private boolean isAssigned(ConfigurationType type, RunConfiguration configuration) {
    if (configuration == null) {
      for (ConfigurationFactory each : type.getConfigurationFactories()) {
        RunnerAndConfigurationSettingsImpl settings = ((RunManagerImpl)myRunManager).getConfigurationTemplate(each);
        if (doIsAssigned(settings.getConfiguration())) return true;
      }
      return false;
    }

    return doIsAssigned(configuration);
  }

  private boolean doIsAssigned(RunConfiguration configuration) {
    MavenBeforeRunTask task = myRunManager.getBeforeRunTask(configuration, MavenBeforeRunTasksProvider.TASK_ID);
    return task != null && task.isEnabled() && task.isFor(myMavenProject, myGoal);
  }

  private void assign(ConfigurationType type, RunConfiguration configuration) {
    if (configuration == null) {
      for (ConfigurationFactory each : type.getConfigurationFactories()) {
        RunnerAndConfigurationSettingsImpl settings = ((RunManagerImpl)myRunManager).getConfigurationTemplate(each);
        doAssign(settings.getConfiguration());
      }
    } else {
      doAssign(configuration);
    }
    myTasksManager.fireTasksChanged();
  }

  private void doAssign(RunConfiguration configuration) {
    MavenBeforeRunTask task = myRunManager.getBeforeRunTask(configuration, MavenBeforeRunTasksProvider.TASK_ID);
    if (task != null) {
      task.setProjectPath(myMavenProject.getPath());
      task.setGoal(myGoal);
      task.setEnabled(true);
    }
  }

  private void clearAll() {
    for (MavenBeforeRunTask each : myRunManager.getBeforeRunTasks(MavenBeforeRunTasksProvider.TASK_ID, false)) {
      each.setEnabled(false);
    }
    myTasksManager.fireTasksChanged();
  }

  protected void doOKAction() {
    clearAll();

    Enumeration nodes = myRoot.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodes.nextElement();
      Descriptor descriptor = (Descriptor)node.getUserObject();
      if (!descriptor.isChecked()) continue;
      if (descriptor instanceof DescriptorBase) {
        DescriptorBase descriptorBase = (DescriptorBase)descriptor;
        assign(descriptorBase.getConfigurationType(), descriptorBase.getConfiguration());
      }
    }

    close(OK_EXIT_CODE);
  }

  private static class Descriptor {
    private boolean myChecked;

    public final boolean isChecked() {
      return myChecked;
    }

    public final void setChecked(boolean checked) {
      myChecked = checked;
    }
  }

  private static abstract class DescriptorBase extends Descriptor {
    private final ConfigurationType myConfigurationType;
    private final RunConfiguration myConfiguration;

    public DescriptorBase(ConfigurationType type, RunConfiguration configuration, boolean isChecked) {
      myConfigurationType = type;
      myConfiguration = configuration;
      setChecked(isChecked);
    }

    public ConfigurationType getConfigurationType() {
      return myConfigurationType;
    }

    @Nullable
    public abstract String getName();

    public RunConfiguration getConfiguration() {
      return myConfiguration;
    }
  }

  private static final class ConfigurationTypeDescriptor extends DescriptorBase {
    private final Icon myIcon;

    public ConfigurationTypeDescriptor(ConfigurationType type, Icon icon, boolean isChecked) {
      super(type, null, isChecked);
      myIcon = icon;
    }

    public Icon getIcon() {
      return myIcon;
    }

    @Nullable
    public String getName() {
      return null;
    }
  }

  private static final class ConfigurationDescriptor extends DescriptorBase {
    private final String myName;

    public ConfigurationDescriptor(ConfigurationType type, RunConfiguration configuration, String name, boolean isChecked) {
      super(type, configuration, isChecked);
      myName = name;
    }

    public String getName() {
      return myName;
    }
  }

  private static final class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final JLabel myLabel;
    public final JCheckBox myCheckbox;

    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myLabel = new JLabel();
      add(myCheckbox, BorderLayout.WEST);
      add(myLabel, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Descriptor descriptor = (Descriptor)node.getUserObject();

      myCheckbox.setSelected(descriptor.isChecked());

      myCheckbox.setBackground(UIUtil.getTreeTextBackground());
      setBackground(selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground());
      Color foreground = selected ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeTextForeground();
      setForeground(foreground);
      myCheckbox.setForeground(foreground);
      myLabel.setForeground(foreground);
      myCheckbox.setEnabled(true);

      if (descriptor instanceof ConfigurationTypeDescriptor) {
        ConfigurationTypeDescriptor configurationTypeDescriptor = (ConfigurationTypeDescriptor)descriptor;
        myLabel.setFont(tree.getFont());
        myLabel.setText(configurationTypeDescriptor.getConfigurationType().getDisplayName());
        myLabel.setIcon(configurationTypeDescriptor.getIcon());
      }
      else if (descriptor instanceof ConfigurationDescriptor) {
        ConfigurationDescriptor configurationTypeDescriptor = (ConfigurationDescriptor)descriptor;
        myLabel.setFont(tree.getFont());
        myLabel.setText(configurationTypeDescriptor.getName());
        myLabel.setIcon(null);

        if (((ConfigurationTypeDescriptor)((DefaultMutableTreeNode)node.getParent()).getUserObject()).isChecked()) {
          Color foregrnd = tree.getForeground();
          Color backgrnd = tree.getBackground();
          if (foregrnd == null) foregrnd = Color.black;
          if (backgrnd == null) backgrnd = Color.white;

          int red = (foregrnd.getRed() + backgrnd.getRed()) / 2;
          int green = (foregrnd.getGreen() + backgrnd.getGreen()) / 2;
          int blue = (foregrnd.getBlue() + backgrnd.getBlue()) / 2;
          Color halftone = new Color(red, green, blue);
          setForeground(halftone);
          myCheckbox.setForeground(halftone);
          myLabel.setForeground(halftone);
          myCheckbox.setEnabled(false);
        }
      }

      return this;
    }
  }
}
