/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 15, 2007
 * Time: 4:04:39 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TableUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableCellRenderer;
import com.intellij.util.ui.treetable.TreeTableModel;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

public class FileTreeTable extends TreeTable {
  private final MyModel myModel;
  private final Project myProject;

  public FileTreeTable(final Project project) {
    super(new MyModel(project));
    myProject = project;

    myModel = (MyModel)getTableModel();

    final TableColumn valueColumn = getColumnModel().getColumn(1);
    valueColumn.setCellRenderer(new DefaultTableCellRenderer(){
      public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                     final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        Charset t = (Charset)value;
        if (t != null) {
          setText(t.name());
        }
        else {
          Object userObject = table.getModel().getValueAt(row, 0);
          if (userObject instanceof VirtualFile) {
            VirtualFile file = (VirtualFile)userObject;
            Charset charset = ChooseFileEncodingAction.encodingFromContent(myProject, file);
            if (charset != null) {
              setText("Encoding: " + charset);
              return this;
            }
            setEnabled(ChooseFileEncodingAction.isEnabled(myProject, file));
          }
        }
        setEnabled(true);
        return this;
      }
    });

    JComboBox valuesCombo = new JComboBox();
    valuesCombo.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        String text =((Charset)value).name();
        setText(text);
        return component;
      }
    });

    valueColumn.setCellEditor(new DefaultCellEditor(new JComboBox()){
      private VirtualFile myVirtualFile;

      {
        delegate = new EditorDelegate() {
            public void setValue(Object value) {
              myModel.setValueAt(value, new DefaultMutableTreeNode(myVirtualFile), -1);
              //comboComponent.revalidate();
            }

	    public Object getCellEditorValue() {
		return myModel.getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
	    }
        };
      }
      public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, int row, int column) {
        Object o = table.getModel().getValueAt(row, 0);
        myVirtualFile = o instanceof Project ? null : (VirtualFile)o;

        final ChooseFileEncodingAction changeAction = new ChooseFileEncodingAction(myVirtualFile, myProject){
          protected void chosen(VirtualFile virtualFile, Charset charset) {
            valueColumn.getCellEditor().stopCellEditing();
            int ret = askWhetherClearSubdirectories(virtualFile);
            if (ret != 2) {
              myModel.setValueAt(charset, new DefaultMutableTreeNode(virtualFile), 1);
            }
          }
        };
        final JComponent comboComponent = changeAction.createCustomComponent(changeAction.getTemplatePresentation());

        DataContext dataContext = SimpleDataContext.getSimpleContext(DataConstants.VIRTUAL_FILE, myVirtualFile, SimpleDataContext.getProjectContext(myProject));
        AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, changeAction.getTemplatePresentation(), ActionManager.getInstance(), 0);
        changeAction.update(event);
        editorComponent = comboComponent;

        Charset charset = (Charset)myModel.getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
        if (charset != null) {
          changeAction.getTemplatePresentation().setText(charset.name());
        }
        comboComponent.revalidate();

        return editorComponent;
      }
    });

    getTree().setShowsRootHandles(true);
    getTree().setLineStyleAngled();
    getTree().setRootVisible(true);
    getTree().setCellRenderer(new DefaultTreeCellRenderer(){
      public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded,
                                                    final boolean leaf, final int row, final boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof ProjectRootNode) {
          setText("Project");
          return this;
        }
        FileNode fileNode = (FileNode)value;
        VirtualFile file = fileNode.getObject();
        if (fileNode.getParent() instanceof FileNode) {
          setText(file.getName());
        }
        else {
          setText(file.getPresentableUrl());
        }

        Icon icon;
        if (file.isDirectory()) {
          icon = expanded ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON;
        }
        else {
          icon = IconUtil.getIcon(file, 0, null);
        }
        setIcon(icon);
        return this;
      }
    });
    getTableHeader().setReorderingAllowed(false);


    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setPreferredScrollableViewportSize(new Dimension(300, getRowHeight() * 10));

    getColumnModel().getColumn(0).setPreferredWidth(280);
    valueColumn.setPreferredWidth(60);
  }

  private int askWhetherClearSubdirectories(final VirtualFile parent) {
    Map<VirtualFile, Charset> mappings = myModel.myCurrentMapping;
    Map<VirtualFile, Charset> subdirectoryMappings = new THashMap<VirtualFile, Charset>();
    for (VirtualFile file : mappings.keySet()) {
      if (file != null && (parent == null || VfsUtil.isAncestor(parent, file, true))) {
        subdirectoryMappings.put(file, mappings.get(file));
      }
    }
    if (subdirectoryMappings.isEmpty()) {
      return 0;
    }
    else {
      int ret = Messages.showDialog(myProject, "There are encodings specified for the subdirectories. Override them?",
                                            "Override Subdirectory Encoding", new String[]{"Override","Do Not Override","Cancel"}, 0, Messages.getWarningIcon());
      if (ret == 0) {
        for (VirtualFile file : subdirectoryMappings.keySet()) {
          myModel.setValueAt(null, new DefaultMutableTreeNode(file), 1);
        }
      }
      return ret;
    }
  }

  public Map<VirtualFile, Charset> getValues() {
    return myModel.getValues();
  }

  public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
    TreeTableCellRenderer tableRenderer = super.createTableRenderer(treeTableModel);
    UIUtil.setLineStyleAngled(tableRenderer);
    tableRenderer.setRootVisible(false);
    tableRenderer.setShowsRootHandles(true);

    return tableRenderer;
  }

  public void reset(final Map<VirtualFile, Charset> mappings) {
    myModel.reset(mappings);
    final TreeNode root = (TreeNode)myModel.getRoot();
    myModel.nodeChanged(root);
    getTree().setModel(null);
    getTree().setModel(myModel);
  }

  public void select(final VirtualFile toSelect) {
    if (toSelect != null) {
      select(toSelect, (TreeNode)myModel.getRoot());
    }
  }
  private void select(@NotNull VirtualFile toSelect, final TreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      TreeNode child = root.getChildAt(i);
      VirtualFile file = ((FileNode)child).getObject();
      if (VfsUtil.isAncestor(file, toSelect, false)) {
        if (file == toSelect) {
          TreeUtil.selectNode(getTree(), child);
          getSelectionModel().clearSelection();
          addSelectedPath(TreeUtil.getPathFromRoot(child));
          TableUtil.scrollSelectionToVisible(this);
        }
        else {
          select(toSelect, child);
        }
        return;
      }
    }
  }


  private static class MyModel extends DefaultTreeModel implements TreeTableModel {
    private final Map<VirtualFile, Charset> myCurrentMapping = new HashMap<VirtualFile, Charset>();
    private final Project myProject;

    private MyModel(Project project) {
      super(new ProjectRootNode(project));
      myProject = project;
      myCurrentMapping.putAll(EncodingProjectManager.getInstance(project).getAllMappings());
    }

    private Map<VirtualFile, Charset> getValues() {
      return new HashMap<VirtualFile, Charset>(myCurrentMapping);
    }

    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(final int column) {
      switch(column) {
        case 0: return "File/Directory";
        case 1: return "Default Encoding";
        default: throw new RuntimeException("invalid column " + column);
      }
    }

    public Class getColumnClass(final int column) {
      switch(column) {
        case 0: return TreeTableModel.class;
        case 1: return Charset.class;
        default: throw new RuntimeException("invalid column " + column);
      }
    }

    public Object getValueAt(final Object node, final int column) {
      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof Project) {
        switch(column) {
          case 0: return userObject;
          case 1: return myCurrentMapping.get(null);
        }
      }
      VirtualFile file = (VirtualFile)userObject;
      switch(column) {
        case 0: return file;
        case 1: return myCurrentMapping.get(file);
        default: throw new RuntimeException("invalid column " + column);
      }
    }

    public boolean isCellEditable(final Object node, final int column) {
      switch(column) {
        case 0: return false;
        case 1:
          Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
          if (userObject instanceof VirtualFile) {
            VirtualFile file = (VirtualFile)userObject;
            return ChooseFileEncodingAction.isEnabled(myProject, file);
          }
          return true;
        default: throw new RuntimeException("invalid column " + column);
      }
    }

    public void setValueAt(final Object aValue, final Object node, final int column) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof Project) return;
      VirtualFile file = (VirtualFile)userObject;
      Charset charset = (Charset)aValue;
      if (charset == ChooseFileEncodingAction.NO_ENCODING) {
        myCurrentMapping.remove(file);
      }
      else {
        myCurrentMapping.put(file, charset);
      }
      fireTreeNodesChanged(this, new Object[]{getRoot()}, null, null);
    }

    public void reset(final Map<VirtualFile, Charset> mappings) {
      myCurrentMapping.clear();
      myCurrentMapping.putAll(mappings);
      ((ProjectRootNode)getRoot()).clearCachedChildren();
    }
  }

  private static class ProjectRootNode extends ConvenientNode<Project> {
    public ProjectRootNode(Project project) {
      super(project);
    }

    protected void appendChildrenTo(final Collection<ConvenientNode> children) {
      Project project = getObject();
      VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
      
      NextRoot:
      for (VirtualFile root : roots) {
        for (VirtualFile candidate : roots) {
          if (VfsUtil.isAncestor(candidate, root, true)) continue NextRoot;
        }
        children.add(new FileNode(root, project));
      }
    }
  }

  private abstract static class ConvenientNode<T> extends DefaultMutableTreeNode {
    private final T myObject;

    private ConvenientNode(T object) {
      myObject = object;
    }

    public T getObject() {
      return myObject;
    }

    protected abstract void appendChildrenTo(final Collection<ConvenientNode> children);

    public int getChildCount() {
      init();
      return super.getChildCount();
    }

    public TreeNode getChildAt(final int childIndex) {
      init();
      return super.getChildAt(childIndex);
    }

    public Enumeration children() {
      init();
      return super.children();
    }

    private void init() {
      if (getUserObject() == null) {
        setUserObject(myObject);
        List<ConvenientNode> children = new ArrayList<ConvenientNode>();
        appendChildrenTo(children);
        Collections.sort(children, new Comparator<ConvenientNode>() {
          public int compare(final ConvenientNode node1, final ConvenientNode node2) {
            Object o1 = node1.getObject();
            Object o2 = node2.getObject();
            if (o1 == o2) return 0;
            if (o1 instanceof Project) return -1;
            if (o2 instanceof Project) return 1;
            VirtualFile file1 = (VirtualFile)o1;
            VirtualFile file2 = (VirtualFile)o2;
            if (file1.isDirectory() != file2.isDirectory()) {
              return file1.isDirectory() ? -1 : 1;
            }
            return file1.getName().compareTo(file2.getName());
          }
        });
        int i=0;
        for (ConvenientNode child : children) {
          insert(child, i++);
        }
      }
    }

    public void clearCachedChildren() {
      if (children != null) {
        for (Object child : children) {
          ConvenientNode<T> node = (ConvenientNode<T>)child;
          node.clearCachedChildren();
        }
      }
      removeAllChildren();
      setUserObject(null);
    }
  }

  private static class FileNode extends ConvenientNode<VirtualFile> {
    private final Project myProject;

    private FileNode(@NotNull VirtualFile file, final Project project) {
      super(file);
      myProject = project;
    }

    protected void appendChildrenTo(final Collection<ConvenientNode> children) {
      VirtualFile[] childrenf = getObject().getChildren();
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      for (VirtualFile child : childrenf) {
        if (fileIndex.isInContent(child)) {
          children.add(new FileNode(child, myProject));
        }
      }
    }
  }
}
