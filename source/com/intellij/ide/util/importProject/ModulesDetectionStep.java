package com.intellij.ide.util.importProject;

import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 6, 2004
 */
public class ModulesDetectionStep extends AbstractStepWithProgress<ProjectLayout> {
  private final JavaModuleBuilder myBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private Tree myModuleTree;
  private Tree myJarsTree;

  public ModulesDetectionStep(JavaModuleBuilder builder, Icon icon, @NonNls String helpId) {
    super("Stop project layout analysis?");
    myBuilder = builder;
    myIcon = icon;
    myHelpId = helpId;
  }

  public void updateDataModel() {
  }

  protected JComponent createResultsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final Splitter splitter = new Splitter(false);
    panel.add(splitter, BorderLayout.CENTER);
    myModuleTree = new Tree();
    myModuleTree.setRootVisible(false);
    splitter.setFirstComponent(new JScrollPane(myModuleTree));
    /*
    panel.add(
      new JScrollPane(myModuleTree), 
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.5, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(5, 5, 5, 0), 0, 0)
    );
    */

    myJarsTree = new Tree();
    myJarsTree.setRootVisible(false);
    splitter.setSecondComponent(new JScrollPane(myJarsTree));
    /*
    panel.add(
      new JScrollPane(myJarsTree), 
      new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.5, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 0, 0)
    );
    */
    return panel;
  }

  protected String getProgressText() {
    return "Analysing module structure. Please wait.";
  }

  protected boolean shouldRunProgress() {
    return true;
  }

  protected ProjectLayout calculate() {
    // build sources array
    final List<Pair<String,String>> sourcePaths = myBuilder.getSourcePaths();
    final List<Pair<File,String>> _sourcePaths = new ArrayList<Pair<File, String>>();
    for (Pair<String, String> path : sourcePaths) {
      _sourcePaths.add(new Pair<File, String>(new File(path.first), path.second != null? path.second : ""));
    }
    // build ignored names set
    final HashSet<String> ignored = new HashSet<String>();
    final StringTokenizer tokenizer = new StringTokenizer(FileTypeManager.getInstance().getIgnoredFilesList(), ";", false);
    while (tokenizer.hasMoreTokens()) {
      ignored.add(tokenizer.nextToken());
    }
    // start scan
    final ModuleInsight insight = new ModuleInsight(new DelegatingProgressIndicator(), Arrays.asList(new File(myBuilder.getContentEntryPath())), _sourcePaths, ignored);
    insight.scan();
    
    return insight.getSuggestedLayout();
  }

  protected void onFinished(final ProjectLayout moduleDescriptors, final boolean canceled) {
    final Set<File> jars = new HashSet<File>();
    final List<ModuleDescriptor> modules = moduleDescriptors.getModules();
    for (ModuleDescriptor moduleDescriptor : modules) {
      jars.addAll(moduleDescriptor.getLibraryFiles());
    }
    rebuildModuleTree(modules);
    rebuildJarsTree(jars);
  }

  private void rebuildModuleTree(List<ModuleDescriptor> descriptors) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final DefaultTreeModel model = new DefaultTreeModel(root);
    myModuleTree.setModel(model);
    int index = 0;
    for (ModuleDescriptor descriptor : descriptors) {
      final DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(descriptor);
      model.insertNodeInto(moduleNode, root, index++);
      final Set<File> libraries = descriptor.getLibraryFiles();
      int j = 0;
      for (File library : libraries) {
        model.insertNodeInto(new DefaultMutableTreeNode(library), moduleNode, j++);
      }
    }
    myModuleTree.expandPath(new TreePath(model.getPathToRoot(root)));
  }
  
  private void rebuildJarsTree(Collection<File> jars) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final DefaultTreeModel model = new DefaultTreeModel(root);
    myJarsTree.setModel(model);
    int index = 0;
    for (File file : jars) {
      final DefaultMutableTreeNode jarNode = new DefaultMutableTreeNode(file);
      model.insertNodeInto(jarNode, root, index++);
    }
    myJarsTree.expandPath(new TreePath(model.getPathToRoot(root)));
  }
  
  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}