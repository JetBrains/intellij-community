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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.core.GridConstraints;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
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

class AssociationsEditor {
    private static final String KEY = "#XsltSupport.AssociationsEditor";
    private static final Icon LINK_OVERLAY = IconLoader.getIcon("/icons/association_small.png");

    private JPanel myComponent;
    private JPanel myToolbar;
    private JBList myList;
    private Tree myTree;
    private JSplitPane mySplitPane;

    private final AssociationsModel myListModel;
    private final TransactionalManager myManager;
    private final ProjectTreeBuilder myBuilder;

    public AssociationsEditor(final Project project, final TreeState oldState) {
        myManager = ((FileAssociationsManagerImpl)FileAssociationsManager.getInstance(project)).getTempManager();

        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AddAssociationActionWrapper());
        group.add(new RemoveAssociationAction());

        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("AssociationsEditor", group, true);
        myToolbar.add(toolbar.getComponent(), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
        myTree.setModel(treeModel);

        myBuilder = new ProjectTreeBuilder(project, myTree, treeModel, new MyGroupByTypeComparator(), new MyProjectStructure(project));

        myTree.expandRow(0);
        myTree.setCellRenderer(new MyNodeRenderer(myManager));
        new TreeSpeedSearch(myTree);

        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (oldState == null) {
                  expandTree(treeModel);
                } else {
                  oldState.applyTo(myTree);
                }
              }
            });
          }
        });

        myListModel = new AssociationsModel(myTree, myManager);
        myListModel.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent listDataEvent) {
                myTree.invalidate();
                myTree.repaint();
            }

            public void intervalRemoved(ListDataEvent listDataEvent) {
                myTree.invalidate();
                myTree.repaint();
            }

            public void contentsChanged(ListDataEvent listDataEvent) {
            }
        });
        myList.setModel(myListModel);
        myList.setCellRenderer(new MyCellRenderer());
        myList.setMinimumSize(new Dimension(120, 200));

        final int location = DimensionService.getInstance().getExtendedState(KEY);
        if (location == -1) {
            myList.setPreferredSize(myList.getMinimumSize());
            mySplitPane.resetToPreferredSizes();
        } else {
            mySplitPane.setDividerLocation(location);
        }

        myList.getEmptyText().setText("No associated files");
    }

    private void expandTree(DefaultTreeModel newModel) {
      final TreePath rootPath = new TreePath(newModel.getRoot());

      final Object element = myBuilder.getTreeStructure().getRootElement();
      myBuilder.batch(new Progressive() {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myBuilder.expand(element, null);
          myBuilder.expand(myBuilder.getTreeStructure().getChildElements(element), null);
        }
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
        DimensionService.getInstance().setExtendedState(KEY, mySplitPane.getDividerLocation());
        Disposer.dispose(myBuilder);
        myManager.dispose();
    }

    public void select(final PsiFile file) {
      myBuilder.getReady(this).doWhenDone(new Runnable() {
        @Override
        public void run() {
          myBuilder.select(file, file.getVirtualFile(), true);
        }
      });
    }

    class AddAssociationActionWrapper extends AddAssociationAction {
        public AddAssociationActionWrapper() {
            super(myManager);
        }

        public void actionPerformed(AnActionEvent e) {
            final PsiFile selection = (PsiFile)getTreeSelection(myTree);
            addAssociation(e, selection);
            myListModel.update(selection);
        }

        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(getTreeSelection(myTree) instanceof PsiFile);
        }
    }

    class RemoveAssociationAction extends AnAction {
        public RemoveAssociationAction() {
            super(null, "Remove Association", IconLoader.getIcon("/general/remove.png"));
        }

        public void actionPerformed(AnActionEvent e) {
            final PsiFile selection = (PsiFile)getTreeSelection(myTree);
            final PsiFile listSelection = (PsiFile)getListSelection();

            myManager.removeAssociation(selection, listSelection);
            myListModel.update(selection);
        }

        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(getListSelection() instanceof PsiFile);
        }

        private Object getListSelection() {
            return myList.getSelectedValue();
        }
    }

    private static class MyGroupByTypeComparator extends GroupByTypeComparator {
        public MyGroupByTypeComparator() {
            super(true);
        }

        protected boolean isSortByType() {
            return false;
        }
    }

    @SuppressWarnings({ "ALL" })
    private static class MyProjectStructure extends AbstractProjectTreeStructure {

        public MyProjectStructure(Project project) {
            super(project);
        }

        public List getProviders() {
            return Collections.EMPTY_LIST;
        }

        public Object[] getChildElements(Object obj) {
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
                    } else {
                        l.add(o);
                    }
                }
            }
            return l.size() != childElements.length ? (Object[])l.toArray(new Object[l.size()]) : childElements;
        }

        public boolean isShowMembers() {
            return false;
        }

        public boolean isShowModules() {
            return true;
        }

        public boolean isFlattenPackages() {
            return true;
        }

        public boolean isAbbreviatePackageNames() {
            return false;
        }

        public boolean isHideEmptyMiddlePackages() {
            return true;
        }

        public boolean isShowLibraryContents() {
            return false;
        }
    }

    static class AssociationsModel extends AbstractListModel implements TreeSelectionListener {
        private final Tree myTree;
        private final FileAssociationsManager myManager;
        private PsiFile[] myFiles;

        public AssociationsModel(Tree tree, FileAssociationsManager manager) {
            myTree = tree;
            myManager = manager;
            myFiles = PsiFile.EMPTY_ARRAY;
            myTree.addTreeSelectionListener(this);
        }

        public int getSize() {
            return myFiles.length;
        }

        public Object getElementAt(int index) {
            return myFiles[index];
        }

        public void valueChanged(TreeSelectionEvent e) {
            final Object selection = getTreeSelection(myTree);
            if (selection instanceof PsiFile) {
                update((PsiFile)selection);
            } else {
                update(null);
            }
        }

        public void update(PsiFile selection) {
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

        public MyNodeRenderer(FileAssociationsManager manager) {
            myManager = manager;
        }

        public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
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

            public MyNodeDescriptor(NodeDescriptor<PsiFileNode> nodeDescriptor) {
                super(nodeDescriptor.getProject(), null);
                myName = nodeDescriptor.toString();
                myOpenIcon = nodeDescriptor.getOpenIcon();
                myClosedIcon = LayeredIcon.create(nodeDescriptor.getClosedIcon(), LINK_OVERLAY);
                myColor = nodeDescriptor.getColor();
                myNode = nodeDescriptor.getElement();
            }

            public boolean update() {
                return false;
            }

            public PsiFileNode getElement() {
                return myNode;
            }
        }
    }

    private static class MyCellRenderer extends PsiElementListCellRenderer<PsiFile> {
        public String getElementText(PsiFile file) {
            return file.getName();
        }

        protected String getContainerText(PsiFile psiElement, String string) {
            //noinspection ConstantConditions
            return "(" + psiElement.getVirtualFile().getParent().getPresentableUrl() + ")";
        }

        protected int getIconFlags() {
            return 0;
        }
    }
}
