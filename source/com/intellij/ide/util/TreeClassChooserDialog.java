package com.intellij.ide.util;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePanel;
import com.intellij.ide.util.gotoByName.GotoClassModel;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;

public class TreeClassChooserDialog extends DialogWrapper {
  private Tree myTree;
  private DefaultTreeModel myModel;
  private PsiClass mySelectedClass = null;
  private Project myProject;
  private BaseProjectTreeBuilder myBuilder;
  private TabbedPaneWrapper myTabbedPane;
  private ChooseByNamePanel myGotoByNamePanel;
  private GlobalSearchScope myScope;
  private ClassFilter myClassFilter;
  private final PsiClass myInitialClass;
  private final PsiClassChildrenSource myClassChildrens;

  public static interface ClassFilter {
    boolean isAccepted(PsiClass aClass);
  }

  public static interface ClassFilterWithScope extends ClassFilter {
    GlobalSearchScope getScope();
  }

  public static class InheritanceClassFilter implements ClassFilter {
    private final PsiClass myBase;
    private final boolean myAcceptsSelf;
    private final boolean myAcceptsInner;
    private final Condition<PsiClass> myAddtionalCondition;

    public InheritanceClassFilter(PsiClass base, boolean acceptsSelf, boolean acceptInner) {
      this(base, acceptsSelf, acceptInner, FilteringIterator.alwaysTrueCondition(PsiClass.class));
    }

    public InheritanceClassFilter(PsiClass base, boolean acceptsSelf, boolean acceptInner,
                                  Condition<PsiClass> addtionalCondition
                                  ) {
      myAcceptsSelf = acceptsSelf;
      myAcceptsInner = acceptInner;
      myAddtionalCondition = addtionalCondition;
      myBase = base;
    }

    public boolean isAccepted(PsiClass aClass) {
      if (!myAcceptsInner && !(aClass.getParent() instanceof PsiJavaFile)) return false;
      if (!myAddtionalCondition.value(aClass)) return false;
      if (myBase == null) return true;
      return myAcceptsSelf ?
             InheritanceUtil.isInheritorOrSelf(aClass, myBase, true) :
             aClass.isInheritor(myBase, true);
    }
  }

  public TreeClassChooserDialog(String title, Project project) {
    this(
      title,
      project,
      null
    );
  }

  public TreeClassChooserDialog(String title, Project project, PsiClass initialClass) {
    this(
      title,
      project,
      GlobalSearchScope.projectScope(project),
      null,
      initialClass
    );
  }

  public TreeClassChooserDialog(
    String title,
    Project project,
    GlobalSearchScope scope,
    ClassFilter classFilter,
    PsiClass initialClass
  ){
    this(title, project, scope, classFilter, initialClass, PsiClassChildrenSource.NONE);
  }

  private TreeClassChooserDialog (String title,
                                  Project project,
                                  GlobalSearchScope scope,
                                  ClassFilter classFilter, PsiClass initialClass, PsiClassChildrenSource classChildrens) {
    super(project, true);
    myScope = scope;
    myClassFilter = classFilter;
    myInitialClass = initialClass;
    myClassChildrens = classChildrens;
    setTitle(title);
    myProject = project;
    init();
    if (initialClass != null) {
      selectClass(initialClass);
    }

    handleSelectionChanged();
  }

  public static TreeClassChooserDialog withInnerClasses(String title, Project project, GlobalSearchScope scope,
                                                        final ClassFilter classFilter, PsiClass initialClass) {
    return new TreeClassChooserDialog(title, project, scope, classFilter, initialClass, new PsiClassChildrenSource() {
      public void addChildren(PsiClass psiClass, java.util.List<PsiElement> children) {
        ArrayList<PsiElement> innerClasses = new ArrayList<PsiElement>();
        PsiClassChildrenSource.CLASSES.addChildren(psiClass, innerClasses);
        for (Iterator<PsiElement> iterator = innerClasses.iterator(); iterator.hasNext();) {
          PsiElement innerClass = iterator.next();
          if (classFilter.isAccepted((PsiClass)innerClass)) children.add(innerClass);
        }
      }
    });
  }

  protected JComponent createCenterPanel() {
    myModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(myModel);

    ProjectAbstractTreeStructureBase treeStructure = new AbstractProjectTreeStructure(
      myProject) {
      public boolean isFlattenPackages() {
        return false;
      }

      public boolean isShowMembers() {
        return myClassChildrens != PsiClassChildrenSource.NONE;
      }

      public boolean isHideEmptyMiddlePackages() {
        return true;
      }


      protected boolean isAcceptedNotClass(PsiElement child) {
        return !(child instanceof PsiFile);
      }

      public boolean isAbbreviatePackageNames() {
        return false;
      }

      public boolean isShowLibraryContents() {
        return false;
      }

      public boolean isShowModules() {
        return false;
      }
    };
    myBuilder = new ProjectTreeBuilder(myProject, myTree, myModel, AlphaComparator.INSTANCE, treeStructure);

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandRow(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new NodeRenderer());
    myTree.putClientProperty("JTree.lineStyle", "Angled");

    JScrollPane scrollPane = new JScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(500, 300));

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path != null && myTree.isPathSelected(path)) {
            doOKAction();
          }
        }
      }
    });

    myTree.addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          handleSelectionChanged();
        }
      }
    );

    new TreeSpeedSearch(myTree);

    myTabbedPane = new TabbedPaneWrapper();

    final JPanel dummyPanel = new JPanel(new BorderLayout());
    String name = null;
    if (myInitialClass != null) {
      name = myInitialClass.getName();
    }
    myGotoByNamePanel = new ChooseByNamePanel(myProject, new MyGotoClassModel(), name) {
      protected void close(boolean isOk) {
        super.close(isOk);

        if (isOk) {
          doOKAction();
        }
        else {
          doCancelAction();
        }
      }

      protected void initUI(Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        dummyPanel.add(myGotoByNamePanel.getPanel(), BorderLayout.CENTER);
        IdeFocusTraversalPolicy.getPreferredFocusedComponent(myGotoByNamePanel.getPanel()).requestFocus();
      }

      protected void choosenElementMightChange() {
        handleSelectionChanged();
      }
    };

    myTabbedPane.addTab("Search by Name", dummyPanel);
    myTabbedPane.addTab("Project", scrollPane);

    myGotoByNamePanel.invoke(new MyCallback(), getModalityState(), false);

    myTabbedPane.installKeyboardNavigation();

    myTabbedPane.addChangeListener(
      new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          handleSelectionChanged();
        }
      }
    );

    return myTabbedPane.getComponent();
  }

  private void handleSelectionChanged(){
    PsiClass selection = calcSelectedClass();
    setOKActionEnabled(selection != null);
  }

  protected void doOKAction() {
    mySelectedClass = calcSelectedClass();
    if (mySelectedClass == null) return;
    super.doOKAction();
  }

  public PsiClass getSelectedClass() {
    return mySelectedClass;
  }

  public void selectClass(final PsiClass aClass) {
    selectElementInTree(aClass);
  }

  public void selectDirectory(final PsiDirectory directory) {
    selectElementInTree(directory);
  }

  private void selectElementInTree(final PsiElement element) {
    if (element == null)
      throw new IllegalArgumentException("aClass cannot be null");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myBuilder == null) return;
        myBuilder.buildNodeForElement(element);
        DefaultMutableTreeNode node = myBuilder.getNodeForElement(element);
        if (node != null) {
          final TreePath treePath = new TreePath(node.getPath());
          myTree.expandPath(treePath);
          TreeUtil.selectPath(myTree, treePath);
        }
      }
    }, getModalityState());
  }

  private ModalityState getModalityState() {
    return ModalityState.stateForComponent(getRootPane());
  }

  private PsiClass calcSelectedClass() {
    if (myTabbedPane.getSelectedIndex() == 0) {
      return (PsiClass)myGotoByNamePanel.getChosenElement();
    }
    else {
      TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ClassTreeNode)) return null;
      ClassTreeNode descriptor = (ClassTreeNode)userObject;
      return descriptor.getPsiClass();
    }
  }


  protected void dispose() {
    if (myBuilder != null) {
      myBuilder.dispose();
      myBuilder = null;
    }
    super.dispose();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.TreeClassChooserDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myGotoByNamePanel.getPreferredFocusedComponent();
  }

  private class MyGotoClassModel extends GotoClassModel {
    public MyGotoClassModel() {
      super(myProject);
    }

    public Object[] getElementsByName(final String name, final boolean checkBoxState) {
      final PsiManager manager = PsiManager.getInstance(myProject);
      PsiClass[] classes = manager.getShortNamesCache().getClassesByName(name, myScope);

      ArrayList<PsiClass> list = new ArrayList<PsiClass>();
      for (int i = 0; i < classes.length; i++) {
        PsiClass aClass = classes[i];
        if (myClassFilter != null && !myClassFilter.isAccepted(aClass)) continue;
        list.add(aClass);
      }
      return list.toArray(new PsiClass[list.size()]);
    }

    public String getPromptText() {
      return null;
    }
  }

  private class MyCallback extends ChooseByNameBase.Callback {
    public void elementChosen(Object element) {
      mySelectedClass = (PsiClass)element;
      close(OK_EXIT_CODE);
    }
  }
}
