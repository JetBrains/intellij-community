/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.ui.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.Tree;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class ScopeEditorPanel {
  private JPanel myButtonsPanel;
  private JTextField myNameField;
  private JTextField myPatternField;
  private JPanel myTreeToolbar;
  private Tree myPackageTree;
  private JPanel myPanel;
  private JPanel myTreePanel;
  private JLabel myMatchingCountLabel;

  private final Project myProject;
  private TreeExpantionMonitor myTreeExpantionMonitor;
  private TreeModelBuilder.Marker myTreeMarker;
  private PackageSet myCurrentScope = null;
  private boolean myIsInUpdate = false;
  private String myErrorMessage;
  private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private ScopeChooserPanel.ScopeDescriptor myDescriptor;
  private boolean myIsFirstUpdate = true;
  private JLabel myCaretPositionLabel;
  private int myCaretPosition = 0;

  public ScopeEditorPanel(Project project, final NamedScopesHolder holder) {
    myProject = project;
    myButtonsPanel.add(createActionsPanel());

    myPackageTree = new Tree();
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myPackageTree), BorderLayout.CENTER);

    myTreeToolbar.setLayout(new BorderLayout());
    myTreeToolbar.add(createTreeToolbar(), BorderLayout.WEST);

    myTreeExpantionMonitor = TreeExpantionMonitor.install(myPackageTree);

    myTreeMarker = new TreeModelBuilder.Marker() {
      public boolean isMarked(PsiFile file) {
        return myCurrentScope == null ? false : myCurrentScope.contains(file, holder);
      }
    };

    myPatternField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        onTextChange();
      }
    });

    myPatternField.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent e) {
        myCaretPosition = e.getDot();
        updateCaretPositionText();
      }
    });

    myPatternField.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        myCaretPositionLabel.setVisible(true);
      }

      public void focusLost(FocusEvent e) {
        myCaretPositionLabel.setVisible(false);
      }
    });

    updateTreeModel();

    initTree(myPackageTree);
  }

  private void updateCaretPositionText() {
    if (myErrorMessage != null) {
      myCaretPositionLabel.setText("pos:" + (myCaretPosition + 1));
    }
    else {
      myCaretPositionLabel.setText("");
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void onTextChange() {
    if (!myIsInUpdate) {
      myCurrentScope = null;
      try {
        myCurrentScope = PackageSetFactory.getInstance().compile(myPatternField.getText());
        myErrorMessage = null;
        rebuild(false);
      }
      catch (ParsingException e) {
        myErrorMessage = e.getMessage();
        showErrorMessage();
      }
    }
    else {
      myErrorMessage = null;
    }
  }

  private void showErrorMessage() {
    myMatchingCountLabel.setText(StringUtil.capitalize(myErrorMessage));
    myMatchingCountLabel.setForeground(Color.red);
    myMatchingCountLabel.setToolTipText(myErrorMessage);
  }

  private JComponent createActionsPanel() {
    JButton include = new JButton("Include");
    JButton includeRec = new JButton("Include Recursively");
    JButton exclude = new JButton("Exclude");
    JButton excludeRec = new JButton("Exclude Recursively");

    JPanel buttonsPanel = new JPanel(new VerticalFlowLayout());
    buttonsPanel.add(include);
    buttonsPanel.add(includeRec);
    buttonsPanel.add(exclude);
    buttonsPanel.add(excludeRec);

    include.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelected(false);
      }
    });
    includeRec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelected(true);
      }
    });
    exclude.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelected(false);
      }
    });
    excludeRec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelected(true);
      }
    });

    return buttonsPanel;
  }

  private void excludeSelected(boolean recurse) {
    PackageSet selected = getSelectedSet(recurse);
    if (selected == null) return;
    selected = new ComplementPackageSet(selected);
    if (myCurrentScope == null) {
      myCurrentScope = selected;
    }
    else {
      myCurrentScope = new IntersectionPackageSet(myCurrentScope, selected);
    }
    rebuild(true);
  }

  private void includeSelected(boolean recurse) {
    PackageSet selected = getSelectedSet(recurse);
    if (selected == null) return;

    if (myCurrentScope == null) {
      myCurrentScope = selected;
    }
    else {
      myCurrentScope = new UnionPackageSet(myCurrentScope, selected);
    }
    rebuild(true);
  }

  private PackageSet getSelectedSet(boolean recursively) {
    int[] rows = myPackageTree.getSelectionRows();
    if (rows == null || rows.length != 1) return null;
    PackageDependenciesNode node = (PackageDependenciesNode)myPackageTree.getPathForRow(rows[0]).getLastPathComponent();
    if (node instanceof ModuleNode) {
      if (!recursively) return null;
      return new PatternPackageSet("*..*", getSelectedScopeType(node), ((ModuleNode)node).getModuleName());
    }
    else if (node instanceof PackageNode) {
      String pattern = ((PackageNode)node).getPackageQName();
      if (pattern != null) {
        pattern += recursively ? "..*" : ".*";
      }
      else {
        pattern = recursively ? "*..*" : "*";
      }

      return getPatternSet(node, pattern);
    }
    else if (node instanceof FileNode) {
      if (recursively) return null;
      FileNode fNode = (FileNode)node;
      String fqName = fNode.getFQName();
      if (fqName != null) return getPatternSet(node, fqName);
    }
    else if (node instanceof GeneralGroupNode) {
      return new PatternPackageSet("*..*", getSelectedScopeType(node), null);
    }

    return null;
  }

  private PackageSet getPatternSet(PackageDependenciesNode node, String pattern) {
    String scope = getSelectedScopeType(node);
    String modulePattern = getSelectedModulePattern(node);

    return new PatternPackageSet(pattern, scope, modulePattern);
  }

  private String getSelectedModulePattern(PackageDependenciesNode node) {
    ModuleNode moduleParent = getModuleParent(node);
    String modulePattern = null;
    if (moduleParent != null) {
      modulePattern = moduleParent.getModuleName();
    }
    return modulePattern;
  }

  private String getSelectedScopeType(PackageDependenciesNode node) {
    GeneralGroupNode groupParent = getGroupParent(node);
    String scope = PatternPackageSet.SCOPE_ANY;
    if (groupParent != null) {
      String name = groupParent.toString();
      if (TreeModelBuilder.PRODUCTION_NAME.equals(name)) {
        scope = PatternPackageSet.SCOPE_SOURCE;
      }
      else if (TreeModelBuilder.TEST_NAME.equals(name)) {
        scope = PatternPackageSet.SCOPE_TEST;
      }
      else if (TreeModelBuilder.LIBRARY_NAME.equals(name)) {
        scope = PatternPackageSet.SCOPE_LIBRARY;
      }
    }
    return scope;
  }

  private GeneralGroupNode getGroupParent(PackageDependenciesNode node) {
    if (node instanceof GeneralGroupNode) return (GeneralGroupNode)node;
    if (node == null || node instanceof RootNode) return null;
    return getGroupParent((PackageDependenciesNode)node.getParent());
  }

  private ModuleNode getModuleParent(PackageDependenciesNode node) {
    if (node instanceof ModuleNode) return (ModuleNode)node;
    if (node == null || node instanceof RootNode) return null;
    return getModuleParent((PackageDependenciesNode)node.getParent());
  }

  private JComponent createTreeToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FlattenPackagesAction());
    group.add(new ShowFilesAction());
    group.add(new ShowModulesAction());
    group.add(new GroupByScopeTypeAction());
    group.add(new FilterLegalsAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  public void rebuild(final boolean updateText) {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (updateText && myCurrentScope != null) {
              myIsInUpdate = true;
              myPatternField.setText(myCurrentScope.getText());
              myIsInUpdate = false;
            }
            updateTreeModel();
          }
        });
      }
    }, 300);
  }

  private void initTree(Tree tree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.putClientProperty("JTree.lineStyle", "Angled");

    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    new TreeSpeedSearch(tree);
  }

  private void updateTreeModel() {
    myTreeExpantionMonitor.freeze();
    TreeModelBuilder.TreeModel model = TreeModelBuilder.createTreeModel(myProject, myIsFirstUpdate, false, myTreeMarker);
    myIsFirstUpdate = false;

    if (myErrorMessage == null) {
      myMatchingCountLabel.setText("Scope contains " + model.getMarkedFileCount() + " of total " + model.getTotalFileCount() + " java files");
      myMatchingCountLabel.setForeground(new JLabel().getForeground());
    }
    else {
      showErrorMessage();
    }

    myPackageTree.setModel(model);
    myTreeExpantionMonitor.restore();
  }

  public boolean checkCurrentScopeValid(boolean showMessage) {
    if (myCurrentScope == null) {
      if (showMessage) {
        Messages.showErrorDialog(myPanel, "Correct pattern syntax errors first", "Syntax Error");
      }
      return false;
    }
    return true;
  }

  public boolean commit() {
    if (!checkCurrentScopeValid(true)) return false;

    rebuild(false);
    myDescriptor.setName(myNameField.getText());
    myDescriptor.setSet(myCurrentScope);
    return true;
  }

  public void reset(ScopeChooserPanel.ScopeDescriptor descriptor) {
    myDescriptor = descriptor;
    myNameField.setText(descriptor.getName());
    myCurrentScope = descriptor.getSet();
    myPatternField.setText(myCurrentScope == null ? "" : myCurrentScope.getText());
    rebuild(false);
  }

  private static class MyTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final Color WHOLE_INCLUDED = new Color(10, 119, 0);
    private static final Color PARTIAL_INCLUDED = new Color(0, 50, 160);

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      PackageDependenciesNode node = (PackageDependenciesNode)value;
      if (expanded) {
        setIcon(node.getOpenIcon());
      }
      else {
        setIcon(node.getClosedIcon());
      }

      if (!sel && node.hasMarked() && !DependencyUISettings.getInstance().UI_FILTER_LEGALS) {
        setForeground(node.hasUnmarked() ? PARTIAL_INCLUDED : WHOLE_INCLUDED);
      }

      return this;
    }
  }

  private final class FlattenPackagesAction extends ToggleAction {
    FlattenPackagesAction() {
      super("Flatten Packages", "Flatten Packages", IconLoader.getIcon("/objectBrowser/flattenPackages.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
      rebuild(true);
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super("Show Files", "Show/Hide Files", IconLoader.getIcon("/fileTypes/java.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_SHOW_FILES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      rebuild(true);
    }
  }

  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super("Group by Scope Type", "Group by Scope Type (production, test, libraries)", IconLoader.getIcon("/nodes/testSourceFolder.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild(true);
    }
  }

  private final class ShowModulesAction extends ToggleAction {
    ShowModulesAction() {
      super("Show Modules", "Show/Hide Modules", IconLoader.getIcon("/objectBrowser/showModules.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_SHOW_MODULES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_MODULES = flag;
      rebuild(true);
    }
  }

  private final class FilterLegalsAction extends ToggleAction {
    FilterLegalsAction() {
      super("Show Included Only", "Show only files included to the current scope selected", IconLoader.getIcon("/ant/filter.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_FILTER_LEGALS;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
      rebuild(true);
    }
  }
}