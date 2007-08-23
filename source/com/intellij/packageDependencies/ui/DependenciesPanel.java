package com.intellij.packageDependencies.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.util.scopeChooser.ScopeEditorPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packageDependencies.*;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesHandler;
import com.intellij.packageDependencies.actions.BackwardDependenciesHandler;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Icons;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;

public class DependenciesPanel extends JPanel implements Disposable, DataProvider {
  private Map<PsiFile, Set<PsiFile>> myDependencies;
  private Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> myIllegalDependencies;
  private MyTree myLeftTree = new MyTree();
  private MyTree myRightTree = new MyTree();
  private UsagesPanel myUsagesPanel;

  private static final HashSet<PsiFile> EMPTY_FILE_SET = new HashSet<PsiFile>(0);
  private TreeExpansionMonitor myRightTreeExpansionMonitor;
  private TreeExpansionMonitor myLeftTreeExpansionMonitor;

  private TreeModelBuilder.Marker myRightTreeMarker;
  private TreeModelBuilder.Marker myLeftTreeMarker;
  private Set<PsiFile> myIllegalsInRightTree = new HashSet<PsiFile>();

  private Project myProject;
  private DependenciesBuilder myBuilder;
  private Content myContent;
  private DependencyPanelSettings mySettings = new DependencyPanelSettings();
  private static final Logger LOG = Logger.getInstance("#" + DependenciesPanel.class.getName());


  public DependenciesPanel(Project project, final DependenciesBuilder builder) {
    super(new BorderLayout());
    myDependencies = builder.getDependencies();
    myBuilder = builder;
    myIllegalDependencies = myBuilder.getIllegalDependencies();
    myProject = project;
    myUsagesPanel = new UsagesPanel(myProject, myBuilder);
    Disposer.register(this, myUsagesPanel);

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, new Disposable() {
      public void dispose() {
        treeSplitter.dispose();
      }
    });
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
    treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

    final Splitter splitter = new Splitter(true);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        splitter.dispose();
      }
    });
    splitter.setFirstComponent(treeSplitter);
    splitter.setSecondComponent(myUsagesPanel);
    add(splitter, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);

    myRightTreeExpansionMonitor = TreeExpansionMonitor.install(myRightTree, myProject);
    myLeftTreeExpansionMonitor = TreeExpansionMonitor.install(myLeftTree, myProject);

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
        final TreePath selectionPath = myLeftTree.getSelectionPath();
        if (selectionPath == null) {
          return;
        }
        PackageDependenciesNode selectedNode = (PackageDependenciesNode)selectionPath.getLastPathComponent();
        traverseToLeaves(selectedNode, denyRules, allowRules);
        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
        if (denyRules.length() + allowRules.length() > 0) {
          statusBar.setInfo(AnalysisScopeBundle.message("status.bar.rule.violation.message",
                                                        ((denyRules.length() == 0 || allowRules.length() == 0) ? 1 : 2),
                                                        (denyRules.length() > 0 ? denyRules.toString() + (allowRules.length() > 0 ? "; " : "") : " ") +
                                                        (allowRules.length() > 0 ? allowRules.toString() : " ")));

        }
        else {
          statusBar.setInfo(AnalysisScopeBundle.message("status.bar.no.rule.violation.message"));
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
              myUsagesPanel.findUsages(searchIn, searchFor);
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
    final Enumeration enumeration = treeNode.breadthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      PsiElement childPsiElement = ((PackageDependenciesNode)enumeration.nextElement()).getPsiElement();
      if (myIllegalDependencies.containsKey(childPsiElement)) {
        final Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(childPsiElement);
        for (final DependencyRule rule : illegalDeps.keySet()) {
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
    group.add(new MarkAsIllegalAction());
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
    updateLeftTreeModel();
    updateRightTreeModel();
  }

  private void initTree(final MyTree tree, boolean isRightTree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);

    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(isRightTree), ActionManager.getInstance());


  }

  private void updateRightTreeModel() {
    Set<PsiFile> deps = new HashSet<PsiFile>();
    Set<PsiFile> scope = getSelectedScope(myLeftTree);
    myIllegalsInRightTree = new HashSet<PsiFile>();
    for (PsiFile psiFile : scope) {
      Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(psiFile);
      if (illegalDeps != null) {
        for (final DependencyRule rule : illegalDeps.keySet()) {
          myIllegalsInRightTree.addAll(illegalDeps.get(rule));
        }
      }
      final Set<PsiFile> psiFiles = myDependencies.get(psiFile);
      if (psiFiles != null) {
        for (PsiFile file : psiFiles) {
          if (file != null && file.isValid()) {
            deps.add(file);
          }
        }
      }
    }
    deps.removeAll(scope);
    myRightTreeExpansionMonitor.freeze();
    myRightTree.setModel(buildTreeModel(deps, myRightTreeMarker));
    myRightTreeExpansionMonitor.restore();
    expandFirstLevel(myRightTree);
  }

  private ActionGroup createTreePopupActions(boolean isRightTree) {
    DefaultActionGroup group = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(actionManager.getAction(IdeActions.GROUP_VERSION_CONTROLS));

    if (isRightTree) {
      group.add(actionManager.getAction(IdeActions.GROUP_ANALYZE));
      group.add(new SelectInLeftTreeAction());
    }

    return group;
  }

  private TreeModelBuilder.TreeModel buildTreeModel(Set<PsiFile> deps, TreeModelBuilder.Marker marker) {
    return TreeModelBuilder.createTreeModel(myProject, false, deps, marker, mySettings);
  }

  private void updateLeftTreeModel() {
    Set<PsiFile> psiFiles = myDependencies.keySet();
    myLeftTreeExpansionMonitor.freeze();
    myLeftTree.setModel(buildTreeModel(psiFiles, myLeftTreeMarker));
    myLeftTreeExpansionMonitor.restore();
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
    //another level of nesting
    if (count == 1 && node.getChildAt(0).getChildCount() > 5){
      return;
    }
    tree.expandPath(new TreePath(node.getPath()));
  }

  private static Set<PsiFile> getSelectedScope(final Tree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null || paths.length != 1) return EMPTY_FILE_SET;
    PackageDependenciesNode node = (PackageDependenciesNode)paths[0].getLastPathComponent();
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

  public void dispose() {
    TreeModelBuilder.clearCaches(myProject);
  }

  @Nullable
  @NonNls
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(DataConstants.PSI_ELEMENT)) {
      final PackageDependenciesNode selectedNode = myRightTree.getSelectedNode();
      if (selectedNode != null) {
        return selectedNode.getPsiElement();
      }
    }
    if (dataId.equals(DataConstants.HELP_ID)) {
      return "dependency.viewer.tool.window";
    }
    return null;
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
  ){
      PackageDependenciesNode node = (PackageDependenciesNode)value;
      if (node.isValid()) {
        if (expanded) {
          setIcon(node.getOpenIcon());
        }
        else {
          setIcon(node.getClosedIcon());
        }
      } else {
        append(UsageViewBundle.message("node.invalid") + " ", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      append(node.toString(), node.hasMarked() && !selected ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(node.getPresentableFilesCount(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  private final class CloseAction extends AnAction {
    public CloseAction() {
      super(CommonBundle.message("action.close"), AnalysisScopeBundle.message("action.close.dependency.description"), IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      myUsagesPanel.dispose();
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
    }
  }

  private final class FlattenPackagesAction extends ToggleAction {
    FlattenPackagesAction() {
      super(AnalysisScopeBundle.message("action.flatten.packages"), AnalysisScopeBundle.message("action.flatten.packages"), Icons.FLATTEN_PACKAGES_ICON);
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_FLATTEN_PACKAGES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
      mySettings.UI_FLATTEN_PACKAGES = flag;
      rebuild();
    }
  }

  private final class ShowFilesAction extends ToggleAction {
    ShowFilesAction() {
      super(AnalysisScopeBundle.message("action.show.files"), AnalysisScopeBundle.message("action.show.files.description"), IconLoader.getIcon("/fileTypes/java.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_FILES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
      mySettings.UI_SHOW_FILES = flag;
      if (!flag && myLeftTree.getSelectionPath() != null && myLeftTree.getSelectionPath().getLastPathComponent() instanceof FileNode){
        TreeUtil.selectPath(myLeftTree, myLeftTree.getSelectionPath().getParentPath());
      }
      rebuild();
    }
  }

  private final class ShowModulesAction extends ToggleAction {
    ShowModulesAction() {
      super(AnalysisScopeBundle.message("action.show.modules"), AnalysisScopeBundle.message("action.show.modules.description"), IconLoader.getIcon("/objectBrowser/showModules.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_SHOW_MODULES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_SHOW_MODULES = flag;
      mySettings.UI_SHOW_MODULES = flag;
      rebuild();
    }
  }

  private final class GroupByScopeTypeAction extends ToggleAction {
    GroupByScopeTypeAction() {
      super(AnalysisScopeBundle.message("action.group.by.scope.type"), AnalysisScopeBundle.message("action.group.by.scope.type.description"), IconLoader.getIcon("/nodes/testSourceFolder.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_GROUP_BY_SCOPE_TYPE;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
      mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
      rebuild();
    }
  }


  private final class FilterLegalsAction extends ToggleAction {
    FilterLegalsAction() {
      super(AnalysisScopeBundle.message("action.show.illegals.only"), AnalysisScopeBundle.message("action.show.illegals.only.description"), IconLoader.getIcon("/ant/filter.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return mySettings.UI_FILTER_LEGALS;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
      mySettings.UI_FILTER_LEGALS = flag;
      rebuild();
    }
  }

  private final class EditDependencyRulesAction extends AnAction {
    public EditDependencyRulesAction() {
      super(AnalysisScopeBundle.message("action.edit.rules"), AnalysisScopeBundle.message("action.edit.rules.description"), IconLoader.getIcon("/general/ideOptions.png"));
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
      super(CommonBundle.message("action.rerun"), AnalysisScopeBundle.message("action.rerun.dependency"), IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myBuilder.getScope().isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).closeContent(myContent);
      mySettings.copyToApplicationDependencySettings();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          final AnalysisScope scope = myBuilder.getScope();
          scope.invalidate();
          if (myBuilder.isBackward()) {
            new BackwardDependenciesHandler(myProject, scope, myBuilder.getScopeOfInterest()).analyze();
          }
          else {
            new AnalyzeDependenciesHandler(myProject, scope).analyze();
          }
        }
      });
    }
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super(CommonBundle.message("action.help"), null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp("dependency.viewer.tool.window");
    }
  }

  private class ExportZkmAction extends AnAction {
    private ExportZkmAction() {
      super(AnalysisScopeBundle.message("action.export.zkm"), null, Icons.CUSTOM_FILE_ICON);
    }

    public void actionPerformed(AnActionEvent event) {
      System.out.println("// -----------------------------------------------------------------------------");

      Set<PsiFile> files = getSelectedScope(myRightTree);
      Set<String> excludeStrings = new TreeSet<String>();

      for (Iterator<PsiFile> iterator = files.iterator(); iterator.hasNext();) {
        PsiFile psiFile = iterator.next();
        if (psiFile instanceof PsiJavaFile) {
          PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
          for (int i = 0; i < classes.length; i++) {
            final PsiClass aClass = classes[i];
            excludeClass(aClass, false, excludeStrings);
          }
        }
      }

      for (Iterator<String> iterator = excludeStrings.iterator(); iterator.hasNext();) {
        String s = iterator.next();
        System.out.println(s);
      }

      System.out.println("// -----------------------------------------------------------------------------");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private void excludeClass(final PsiClass aClass, boolean base, Set<String> excludeStrings) {
      String qName = aClass.getQualifiedName();
      if (!qName.startsWith("com.intellij")) return;

      String instr = qName.substring(0, qName.lastIndexOf(".") + 1) + "^" + aClass.getName() + "^";

      excludeStrings.add(instr + " !private *(*) and" + (base ? " //base" : ""));
      excludeStrings.add(instr + " !private * and" + (base ? " //base" : ""));

      final PsiClass[] supers = aClass.getSupers();
      for (int i = 0; i < supers.length; i++) {
        PsiClass aSuper = supers[i];
        excludeClass(aSuper, true, excludeStrings);
      }

      final PsiClass[] interfaces = aClass.getInterfaces();
      for (int i = 0; i < interfaces.length; i++) {
        PsiClass anInterface = interfaces[i];
        excludeClass(anInterface, true, excludeStrings);
      }
    }
  }

  private static class MyTree extends Tree implements DataProvider {
    public Object getData(String dataId) {
      PackageDependenciesNode node = getSelectedNode();
      if (DataConstants.NAVIGATABLE.equals(dataId)) {
        return node;
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
      super(AnalysisScopeBundle.message("action.select.in.left.tree"), AnalysisScopeBundle.message("action.select.in.left.tree.description"), null);
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
            for (PsiFile file : files) {
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
          mySettings.UI_FILTER_LEGALS = false;
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

  private class MarkAsIllegalAction extends AnAction {
    public MarkAsIllegalAction() {
      super(AnalysisScopeBundle.message("mark.dependency.illegal.text"), AnalysisScopeBundle.message("mark.dependency.illegal.text"), IconLoader.getIcon(
        "/actions/lightning.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final PackageDependenciesNode leftNode = myLeftTree.getSelectedNode();
      final PackageDependenciesNode rightNode = myRightTree.getSelectedNode();
      if (leftNode != null && rightNode != null) {
        PackageSet leftPackageSet = ScopeEditorPanel.getNodePackageSet(leftNode, true);
        if (leftPackageSet == null) {
          leftPackageSet = ScopeEditorPanel.getNodePackageSet(leftNode, false);
        }
        LOG.assertTrue(leftPackageSet != null);
        PackageSet rightPackageSet = ScopeEditorPanel.getNodePackageSet(rightNode, true);
        if (rightPackageSet == null) {
          rightPackageSet = ScopeEditorPanel.getNodePackageSet(rightNode, false);
        }
        LOG.assertTrue(rightPackageSet != null);
        DependencyValidationManager.getInstance(myProject)
          .addRule(new DependencyRule(new NamedScope.UnnamedScope(leftPackageSet),
                                      new NamedScope.UnnamedScope(rightPackageSet), true));
        rebuild();
      }
    }

    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final PackageDependenciesNode leftNode = myLeftTree.getSelectedNode();
      final PackageDependenciesNode rightNode = myRightTree.getSelectedNode();
      if (leftNode != null && rightNode != null) {
        presentation.setEnabled((ScopeEditorPanel.getNodePackageSet(leftNode, true) != null || ScopeEditorPanel.getNodePackageSet(leftNode, false) != null) &&
                                (ScopeEditorPanel.getNodePackageSet(rightNode, true) != null || ScopeEditorPanel.getNodePackageSet(rightNode, false) != null));
      }
    }
  }

  public static class DependencyPanelSettings {
    public boolean UI_FLATTEN_PACKAGES = true;
    public boolean UI_SHOW_FILES = false;
    public boolean UI_SHOW_MODULES = true;
    public boolean UI_FILTER_LEGALS = false;
    public boolean UI_GROUP_BY_SCOPE_TYPE = true;
    public boolean UI_GROUP_BY_FILES = false;
    public boolean UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;

    public DependencyPanelSettings() {
      final DependencyUISettings settings = DependencyUISettings.getInstance();
      UI_FLATTEN_PACKAGES = settings.UI_FLATTEN_PACKAGES;
      UI_SHOW_FILES = settings.UI_SHOW_FILES;
      UI_SHOW_MODULES = settings.UI_SHOW_MODULES;
      UI_FILTER_LEGALS = settings.UI_FILTER_LEGALS;
      UI_GROUP_BY_SCOPE_TYPE = settings.UI_GROUP_BY_SCOPE_TYPE;
      UI_GROUP_BY_FILES = settings.UI_GROUP_BY_FILES;
      UI_COMPACT_EMPTY_MIDDLE_PACKAGES = settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    }

    public void copyToApplicationDependencySettings(){
      final DependencyUISettings settings = DependencyUISettings.getInstance();
      settings.UI_FLATTEN_PACKAGES = UI_FLATTEN_PACKAGES;
      settings.UI_SHOW_FILES = UI_SHOW_FILES;
      settings.UI_SHOW_MODULES = UI_SHOW_MODULES;
      settings.UI_FILTER_LEGALS = UI_FILTER_LEGALS;
      settings.UI_GROUP_BY_SCOPE_TYPE = UI_GROUP_BY_SCOPE_TYPE;
      settings.UI_GROUP_BY_FILES = UI_GROUP_BY_FILES;
      settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    }
  }
}
