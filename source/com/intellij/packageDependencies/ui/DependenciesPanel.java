package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesHandler;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.util.Icons;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class DependenciesPanel extends JPanel {
  private Map<PsiFile, Set<PsiFile>> myDependencies;
  private Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> myIllegalDependencies;
  private MyTree myLeftTree = new MyTree();
  private JEditorPane myBrowser = new JEditorPane("text/html", "<HTML><BODY></BODY></HTML>");
  private MyTree myRightTree = new MyTree();
  private UsagesPanel myUsagesPanel;

  private static final HashSet<PsiFile> EMPTY_FILE_SET = new HashSet<PsiFile>(0);
  private TreeExpantionMonitor myRightTreeExpantionMonitor;
  private TreeExpantionMonitor myLeftTreeExpantionMonitor;

  private TreeModelBuilder.Marker myRightTreeMarker;
  private TreeModelBuilder.Marker myLeftTreeMarker;
  private Set<PsiFile> myIllegalsInRightTree = new HashSet<PsiFile>();

  private Project myProject;
  private DependenciesBuilder myBuilder;
  private Content myContent;

  private JComponent myLeftTreePanel;

  public DependenciesPanel(Project project, final DependenciesBuilder builder) {
    super(new BorderLayout());
    myDependencies = builder.getDependencies();
    myBuilder = builder;
    myIllegalDependencies = myBuilder.getIllegalDependencies();
    myProject = project;
    myUsagesPanel = new UsagesPanel(myProject);

    hideHintsWhenNothingToShow();


    Splitter treeSplitter = new Splitter();
    treeSplitter.setFirstComponent(myLeftTreePanel);
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

    Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(treeSplitter);
    splitter.setSecondComponent(myUsagesPanel);
    add(splitter, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);

    myRightTreeExpantionMonitor = TreeExpantionMonitor.install(myRightTree);
    myLeftTreeExpantionMonitor = TreeExpantionMonitor.install(myLeftTree);

    myRightTreeMarker = new TreeModelBuilder.Marker() {
      public boolean isMarked(PsiFile file) {
        return myIllegalsInRightTree.contains(file);
      }
    };

    myLeftTreeMarker = new TreeModelBuilder.Marker() {
      public boolean isMarked(PsiFile file) {
        return myIllegalDependencies.containsKey(file);
      }
    };

    updateLeftTreeModel();
    updateRightTreeModel();

    myLeftTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateRightTreeModel();
        final StringBuffer denyRules = new StringBuffer();
        final StringBuffer allowRules = new StringBuffer();
        PackageDependenciesNode selectedNode = (PackageDependenciesNode)myLeftTree.getSelectionPath().getLastPathComponent();
        traverseToLeaves(selectedNode, denyRules, allowRules);
        try {
          if (denyRules.length() + allowRules.length() > 0) {
            myBrowser.read(new StringReader("<html><body>The following rule" +
                                            ((denyRules.length() == 0 || allowRules.length() == 0) ? " is " : "s are ") +
                                            "violated: " +
                                            (denyRules.length() > 0 ? denyRules.toString() : " ") + "<br>" +
                                            (allowRules.length() > 0 ? allowRules.toString() : " ") +
                                            "</body></html>"), null);

          }
          else {
            myBrowser.read(new StringReader("<html><body>No rules are violated.</body></html>"), null);
          }
        }
        catch (IOException e1) {
          //can't be
        }
      }
    });

    myRightTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            Set<PsiFile> searchIn = getSelectedScope(myLeftTree);
            Set<PsiFile> searchFor = getSelectedScope(myRightTree);
            if (searchIn.isEmpty() || searchFor.isEmpty()) {
              myUsagesPanel.setToInitialPosition();
            }
            else {
              myUsagesPanel.findUsages(builder, searchIn, searchFor);
            }
          }
        });
      }
    });

    initTree(myLeftTree, false);
    initTree(myRightTree, true);

    AnalysisScope scope = builder.getScope();
    if (scope.getScopeType() == AnalysisScope.FILE) {
      Set<PsiFile> oneFileSet = myDependencies.keySet();
      if (oneFileSet.size() == 1) {
        selectElementInLeftTree(oneFileSet.iterator().next());
      }
    }
  }

  private void traverseToLeaves(final PackageDependenciesNode treeNode, final StringBuffer denyRules, final StringBuffer allowRules) {
    for (int i = 0; i < treeNode.getChildCount(); i++) {
      traverseToLeaves((PackageDependenciesNode)treeNode.getChildAt(i), denyRules, allowRules);
    }
    if (myIllegalDependencies.containsKey(treeNode.getPsiElement())) {
      final Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(treeNode.getPsiElement());
      for (Iterator<DependencyRule> iterator = illegalDeps.keySet().iterator(); iterator.hasNext();) {
        final DependencyRule rule = iterator.next();
        if (rule.isDenyRule()) {
          if (denyRules.indexOf(rule.getDisplayText()) == -1) {
            denyRules.append(rule.getDisplayText());
            denyRules.append("\n");
          }
        }
        else {
          if (allowRules.indexOf(rule.getDisplayText()) == -1) {
            allowRules.append(rule.getDisplayText());
            allowRules.append("\n");
          }
        }
      }
    }
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(new FlattenPackagesAction());
    group.add(new ShowFilesAction());
    group.add(new ShowModulesAction());
    group.add(new GroupByScopeTypeAction());
    group.add(new FilterLegalsAction());
    group.add(new EditDependencyRulesAction());
    group.add(new HelpAction());

    if (((ApplicationEx)ApplicationManager.getApplication()).isInternal()) {
      group.add(new ExportZkmAction());
    }

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  private void rebuild() {
    myIllegalDependencies = myBuilder.getIllegalDependencies();
    hideHintsWhenNothingToShow();
    updateLeftTreeModel();
    updateRightTreeModel();
  }

  private void hideHintsWhenNothingToShow() {
    if (myIllegalDependencies.isEmpty()) {
      myLeftTreePanel = ScrollPaneFactory.createScrollPane(myLeftTree);
    }
    else {
      Splitter leftTreeSplitter = new Splitter();
      leftTreeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
      leftTreeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myBrowser));
      myLeftTreePanel = leftTreeSplitter;
    }
  }

  private void initTree(final MyTree tree, boolean isRightTree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.putClientProperty("JTree.lineStyle", "Angled");

    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    new TreeSpeedSearch(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(isRightTree), ActionManager.getInstance());

    tree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.isPopupTrigger() && e.getClickCount() == 2) {
          Navigatable navigatable = (Navigatable)tree.getData(DataConstants.NAVIGATABLE);
          if (navigatable != null) {
            navigatable.navigate(true);
          }
        }
      }
    });
  }

  private void updateRightTreeModel() {
    Set<PsiFile> deps = new HashSet<PsiFile>();
    Set<PsiFile> scope = getSelectedScope(myLeftTree);
    myIllegalsInRightTree = new HashSet<PsiFile>();
    for (Iterator<PsiFile> iterator = scope.iterator(); iterator.hasNext();) {
      PsiFile psiFile = iterator.next();
      Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(psiFile);
      if (illegalDeps != null) {
        for (Iterator<DependencyRule> iterator1 = illegalDeps.keySet().iterator(); iterator1.hasNext();) {
          final DependencyRule rule = iterator1.next();
          myIllegalsInRightTree.addAll(illegalDeps.get(rule));
        }
      }
      deps.addAll(myDependencies.get(psiFile));
    }
    deps.removeAll(scope);
    myRightTreeExpantionMonitor.freeze();
    myRightTree.setModel(buildTreeModel(deps, myRightTreeMarker));
    myRightTreeExpantionMonitor.restore();
    expandFirstLevel(myRightTree);
  }

  private ActionGroup createTreePopupActions(boolean isRightTree) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));

    if (isRightTree) {
      group.add(new SelectInLeftTreeAction());
    }

    return group;
  }

  private TreeModelBuilder.TreeModel buildTreeModel(Set<PsiFile> deps, TreeModelBuilder.Marker marker) {
    return TreeModelBuilder.createTreeModel(myProject, false, deps, marker);
  }

  private void updateLeftTreeModel() {
    Set<PsiFile> psiFiles = myDependencies.keySet();
    myLeftTreeExpantionMonitor.freeze();
    myLeftTree.setModel(buildTreeModel(psiFiles, myLeftTreeMarker));
    myLeftTreeExpantionMonitor.restore();
    expandFirstLevel(myLeftTree);
  }

  private static void expandFirstLevel(Tree tree) {
    PackageDependenciesNode root = (PackageDependenciesNode)tree.getModel().getRoot();
    int count = root.getChildCount();
    if (count < 10) {
      for (int i = 0; i < count; i++) {
        PackageDependenciesNode child = (PackageDependenciesNode)root.getChildAt(i);
        expandNodeIfNotTooWide(tree, child);
      }
    }
  }

  private static void expandNodeIfNotTooWide(Tree tree, PackageDependenciesNode node) {
    int count = node.getChildCount();
    if (count > 5) return;
    tree.expandPath(new TreePath(node.getPath()));
  }

  private Set<PsiFile> getSelectedScope(final Tree tree) {
    int[] rows = tree.getSelectionRows();
    if (rows == null || rows.length != 1) return EMPTY_FILE_SET;
    PackageDependenciesNode node = (PackageDependenciesNode)tree.getPathForRow(rows[0]).getLastPathComponent();
    if (node.isRoot()) return EMPTY_FILE_SET;
    Set<PsiFile> result = new HashSet<PsiFile>();
    node.fillFiles(result, !DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES);
    return result;
  }

  public void setContent(Content content) {
    myContent = content;
  }

  public JTree getLeftTree() {
    return myLeftTree;
  }

  public JTree getRightTree() {
    return myRightTree;
  }

  private static class MyTreeCellRenderer extends DefaultTreeCellRenderer {
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

      if (node.hasMarked() && !sel) {
        setForeground(Color.red);
      }

      return this;
    }
  }

  private final class CloseAction extends AnAction {
    public CloseAction() {
      super("Close", "Close Dependency Viewer", IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      DependencyValidationManager.getInstance(myProject).closeContent(myContent);
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
      rebuild();
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
      rebuild();
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
      rebuild();
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
      rebuild();
    }
  }


  private final class FilterLegalsAction extends ToggleAction {
    FilterLegalsAction() {
      super("Show Illegals Only", "Show only files that have illegal dependencies", IconLoader.getIcon("/ant/filter.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_FILTER_LEGALS;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
      rebuild();
    }
  }

  private final class EditDependencyRulesAction extends AnAction {
    public EditDependencyRulesAction() {
      super("Edit Rules", "Edit Dependency Rules", IconLoader.getIcon("/general/ideOptions.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      boolean applied = ShowSettingsUtil.getInstance().editConfigurable(DependenciesPanel.this, new DependencyConfigurable(myProject));
      if (applied) {
        rebuild();
      }
    }
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super("Rerun", "Rerun Dependency Analysis", IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myBuilder.getScope().isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      DependencyValidationManager.getInstance(myProject).closeContent(myContent);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          new AnalyzeDependenciesHandler(myProject, myBuilder.getScope()).analyze();
        }
      });
    }
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super("Help", null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp("editing.analyzeDependencies");
    }
  }

  private class ExportZkmAction extends AnAction {
    private ExportZkmAction() {
      super("Export ZKM", null, Icons.CUSTOM_FILE_ICON);
    }

    public void actionPerformed(AnActionEvent event) {
      System.out.println("// -----------------------------------------------------------------------------");

      Set<PsiFile> files = getSelectedScope(myRightTree);
      for (Iterator<PsiFile> iterator = files.iterator(); iterator.hasNext();) {
        PsiFile psiFile = iterator.next();
        if (psiFile instanceof PsiJavaFile) {
          PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
          for (int i = 0; i < classes.length; i++) {
            String qName = classes[i].getQualifiedName();
            String instr = qName.substring(0, qName.lastIndexOf(".") + 1) + "^" + classes[i].getName() + "^";

            System.out.println(instr + " !private *(*) and");
            System.out.println(instr + " !private * and");
          }
        }
      }

      System.out.println("// -----------------------------------------------------------------------------");
    }
  }

  private static class MyTree extends Tree implements DataProvider {
    public Object getData(String dataId) {
      PackageDependenciesNode node = getSelectedNode();
      if (node != null && DataConstants.PSI_ELEMENT.equals(dataId)) {
        return node.getPsiElement();
      }
      return null;
    }

    public PackageDependenciesNode getSelectedNode() {
      TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length != 1) return null;
      return (PackageDependenciesNode)paths[0].getLastPathComponent();
    }
  }

  private class SelectInLeftTreeAction extends AnAction {
    public SelectInLeftTreeAction() {
      super("Select in Left Tree", "Select in left tree (to browse dependencies from)", null);
    }

    public void update(AnActionEvent e) {
      boolean enabled = false;
      PackageDependenciesNode node = myRightTree.getSelectedNode();
      if (node != null) {
        PsiElement elt = node.getPsiElement();
        if (elt != null) {
          if (elt instanceof PsiFile) {
            enabled = myDependencies.containsKey(elt);
          }
          else if (elt instanceof PsiPackage) {
            Set<PsiFile> files = myDependencies.keySet();
            String packageName = ((PsiPackage)elt).getQualifiedName();
            for (Iterator<PsiFile> iterator = files.iterator(); iterator.hasNext();) {
              PsiFile file = iterator.next();
              if (file instanceof PsiJavaFile && Comparing.equal(packageName, ((PsiJavaFile)file).getPackageName())) {
                enabled = true;
                break;
              }
            }
          }
        }
      }
      e.getPresentation().setEnabled(enabled);
    }

    public void actionPerformed(AnActionEvent e) {
      PackageDependenciesNode node = myRightTree.getSelectedNode();
      if (node != null) {
        PsiElement elt = node.getPsiElement();
        if (elt != null) {
          DependencyUISettings.getInstance().UI_FILTER_LEGALS = false;
          selectElementInLeftTree(elt);

        }
      }
    }
  }

  private void selectElementInLeftTree(PsiElement elt) {
    PsiManager manager = PsiManager.getInstance(myProject);

    PackageDependenciesNode root = (PackageDependenciesNode)myLeftTree.getModel().getRoot();
    Enumeration enumeration = root.breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      PackageDependenciesNode child = (PackageDependenciesNode)enumeration.nextElement();
      if (manager.areElementsEquivalent(child.getPsiElement(), elt)) {
        myLeftTree.setSelectionPath(new TreePath(((DefaultTreeModel)myLeftTree.getModel()).getPathToRoot(child)));
        break;
      }
    }
  }
}