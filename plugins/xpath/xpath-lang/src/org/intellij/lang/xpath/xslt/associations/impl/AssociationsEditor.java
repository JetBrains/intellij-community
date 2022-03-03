// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.associations.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import icons.XpathIcons;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AssociationsEditor {
  private JPanel myComponent;
  private JBList<PsiFile> myList;
  private Tree myTree;

  private final AssociationsModel myListModel;
  private final TransactionalManager myManager;
  private final ProjectTreeBuilder myBuilder;

  AssociationsEditor(final Project project, final TreeState oldState) {
    myManager = ((FileAssociationsManagerImpl)FileAssociationsManager.getInstance(project)).getTempManager();

    initUI();

    final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree.setModel(treeModel);

    myBuilder = new ProjectTreeBuilder(project, myTree, treeModel, new MyGroupByTypeComparator(), new MyProjectStructure(project));

    myTree.setCellRenderer(new MyNodeRenderer(myManager));
    new TreeSpeedSearch(myTree);

    SwingUtilities.invokeLater(() -> ApplicationManager.getApplication().invokeLater(() -> {
      if (oldState == null) {
        expandTree(treeModel);
      }
      else {
        oldState.applyTo(myTree);
      }
    }));

    myListModel = new AssociationsModel(myTree, myManager);
    myListModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent listDataEvent) {
        myTree.invalidate();
        myTree.repaint();
      }

      @Override
      public void intervalRemoved(ListDataEvent listDataEvent) {
        myTree.invalidate();
        myTree.repaint();
      }

      @Override
      public void contentsChanged(ListDataEvent listDataEvent) {
      }
    });
    myList.setModel(myListModel);

  }

  private void initUI() {
    myComponent = new JPanel(new BorderLayout());
    JBSplitter splitter = new JBSplitter("AssociationsEditor.dividerProportion", 0.3f);
    myComponent.add(splitter, BorderLayout.CENTER);

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.setBorder(IdeBorderFactory.createTitledBorder(XPathBundle.message("border.title.project.xslt.files"), false,
                                                            JBInsets.emptyInsets()).setShowLine(false));
    myTree = new Tree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    leftPanel.add(new JBScrollPane(myTree), BorderLayout.CENTER);
    splitter.setFirstComponent(leftPanel);

    myList = new JBList<>();
    myList.setCellRenderer(new MyCellRenderer());
    myList.setMinimumSize(new Dimension(120, 200));
    myList.getEmptyText().setText(XPathBundle.message("status.text.no.associated.files"));
    JPanel rightPanel = ToolbarDecorator.createDecorator(myList)
      .addExtraAction(AnActionButton.fromAction(new AddAssociationActionWrapper()))
      .addExtraAction(AnActionButton.fromAction(new RemoveAssociationAction()))
      .disableUpDownActions().disableAddAction().disableRemoveAction().createPanel();
    final IdeaTitledBorder border =
      IdeBorderFactory.createTitledBorder(XPathBundle.message("border.title.associated.files"), false, JBInsets.emptyInsets());
    UIUtil.addBorder(rightPanel, border.setShowLine(false));
    splitter.setSecondComponent(rightPanel);
  }

  private void expandTree(DefaultTreeModel newModel) {
    final TreePath rootPath = new TreePath(newModel.getRoot());

    final Object element = myBuilder.getTreeStructure().getRootElement();
    myBuilder.batch(indicator -> {
      myBuilder.expand(element, null);
      myBuilder.expand(myBuilder.getTreeStructure().getChildElements(element), null);
    });

    myTree.setSelectionPath(rootPath);
    myTree.scrollRectToVisible(new Rectangle(new Point(0, 0)));
  }

  public TreeState getState() {
    return TreeState.createOn(myTree);
  }

  public JPanel getComponent() {
    return myComponent;
  }

  @Nullable
  static Object getTreeSelection(JTree tree) {
    final TreePath[] selectionPath = tree.getSelectionPaths();
    if (selectionPath == null || selectionPath.length != 1) return null;
    final Object component = selectionPath[0].getLastPathComponent();
    return getObject(component);
  }

  @Nullable
  private static Object getObject(Object component) {
    if (!(component instanceof DefaultMutableTreeNode)) return null;
    final DefaultMutableTreeNode node = ((DefaultMutableTreeNode)component);
    final Object userObject = node.getUserObject();
    if (!(userObject instanceof ProjectViewNode)) return null;
    return ((ProjectViewNode<?>)userObject).getValue();
  }

  public boolean isModified() {
    return myManager.isModified();
  }

  public void apply() {
    myManager.applyChanges();
  }

  public void reset() {
    myManager.reset();
    final Object selection = getTreeSelection(myTree);
    myListModel.update(selection instanceof PsiFile ? ((PsiFile)selection) : null);
  }

  public void dispose() {
    Disposer.dispose(myBuilder);
    myManager.dispose();
  }

  public void select(final PsiFile file) {
    myBuilder.getReady(this).doWhenDone(() -> myBuilder.selectAsync(file, file.getVirtualFile(), true));
  }

  class AddAssociationActionWrapper extends AddAssociationAction {
    AddAssociationActionWrapper() {
      super(myManager);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final PsiFile selection = (PsiFile)getTreeSelection(myTree);
      addAssociation(selection);
      myListModel.update(selection);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getTreeSelection(myTree) instanceof PsiFile);
    }
  }

  class RemoveAssociationAction extends AnAction {
    RemoveAssociationAction() {
      super(XPathBundle.message("action.remove.association.text"),
            XPathBundle.message("action.remove.association.description"),
            IconUtil.getRemoveIcon());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final PsiFile selection = (PsiFile)getTreeSelection(myTree);
      final PsiFile listSelection = (PsiFile)getListSelection();

      myManager.removeAssociation(selection, listSelection);
      myListModel.update(selection);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getListSelection() instanceof PsiFile);
    }

    private Object getListSelection() {
      return myList.getSelectedValue();
    }
  }

  private static final class MyGroupByTypeComparator extends GroupByTypeComparator {
    MyGroupByTypeComparator() {
      super(true);
    }

    @Override
    protected boolean isSortByType() {
      return false;
    }
  }

  @SuppressWarnings({"ALL"})
  private static final class MyProjectStructure extends AbstractProjectTreeStructure {
    public MyProjectStructure(@NotNull Project project) {
      super(project);
    }

    public List getProviders() {
      return Collections.EMPTY_LIST;
    }

    @NotNull
    @Override
    public Object[] getChildElements(@NotNull Object obj) {
      final Object[] childElements = super.getChildElements(obj);
      List l = new ArrayList(childElements.length);
      for (Object o : childElements) {
        if (o instanceof ProjectViewNode) {
          final ProjectViewNode node = (ProjectViewNode)o;
          final Object element = node.getValue();
          if (element instanceof PsiFile) {
            if (XsltSupport.isXsltFile((PsiFile)element)) {
              l.add(o);
            }
          }
          else {
            l.add(o);
          }
        }
      }
      return l.size() != childElements.length ? (Object[])l.toArray(new Object[l.size()]) : childElements;
    }

    @Override
    public boolean isFlattenPackages() {
      return true;
    }

    @Override
    public boolean isHideEmptyMiddlePackages() {
      return true;
    }
  }

  static class AssociationsModel extends AbstractListModel<PsiFile> implements TreeSelectionListener {
    private final Tree myTree;
    private final FileAssociationsManager myManager;
    private PsiFile[] myFiles;

    AssociationsModel(Tree tree, FileAssociationsManager manager) {
      myTree = tree;
      myManager = manager;
      myFiles = PsiFile.EMPTY_ARRAY;
      myTree.addTreeSelectionListener(this);
    }

    @Override
    public int getSize() {
      return myFiles.length;
    }

    @Override
    public PsiFile getElementAt(int index) {
      return myFiles[index];
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      final Object selection = getTreeSelection(myTree);
      if (selection instanceof PsiFile) {
        update((PsiFile)selection);
      }
      else {
        update(null);
      }
    }

    public void update(@Nullable PsiFile selection) {
      final int oldSize = myFiles.length;
      myFiles = PsiFile.EMPTY_ARRAY;
      if (myFiles.length != oldSize) fireIntervalRemoved(this, 0, oldSize - 1);
      if (selection != null) {
        myFiles = myManager.getAssociationsFor(selection);
        if (myFiles.length > 0) fireIntervalAdded(this, 0, myFiles.length - 1);
      }
    }
  }

  private static class MyNodeRenderer extends NodeRenderer {
    private final DefaultMutableTreeNode myTemp = new DefaultMutableTreeNode();
    private final FileAssociationsManager myManager;

    MyNodeRenderer(FileAssociationsManager manager) {
      myManager = manager;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      final Object object = getObject(value);
      if (object instanceof PsiFile) {
        final PsiFile file = (PsiFile)object;
        final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (myManager.getAssociationsFor(file).length > 0) {
          //noinspection unchecked
          myTemp.setUserObject(new MyNodeDescriptor((NodeDescriptor<PsiFileNode>)userObject));
          super.customizeCellRenderer(tree, myTemp, selected, expanded, leaf, row, hasFocus);
          return;
        }
      }
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
    }

    private static class MyNodeDescriptor extends NodeDescriptor<PsiFileNode> {
      private final PsiFileNode myNode;

      MyNodeDescriptor(NodeDescriptor<PsiFileNode> nodeDescriptor) {
        super(nodeDescriptor.getProject(), null);
        myName = nodeDescriptor.toString();
        setIcon(LayeredIcon.create(nodeDescriptor.getIcon(), XpathIcons.Association_small));
        myColor = nodeDescriptor.getColor();
        myNode = nodeDescriptor.getElement();
      }

      @Override
      public boolean update() {
        return false;
      }

      @Override
      public PsiFileNode getElement() {
        return myNode;
      }
    }
  }

  private static class MyCellRenderer extends PsiElementListCellRenderer<PsiFile> {
    @Override
    public String getElementText(PsiFile file) {
      return file.getName();
    }

    @Override
    protected String getContainerText(PsiFile psiElement, String string) {
      return "(" + psiElement.getVirtualFile().getParent().getPresentableUrl() + ")";
    }
  }
}
