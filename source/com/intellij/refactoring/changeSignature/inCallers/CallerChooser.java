package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.Tree;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Enumeration;
import java.util.Set;

/**
 * @author ven
 */
public abstract class CallerChooser extends DialogWrapper {
  PsiMethod myMethod;
  private EditorTextField myEditorField;
  private Alarm myAlarm = new Alarm();
  private MethodNode myRoot;
  private Project myProject;
  private Tree myTree;

  public Tree getTree() {
    return myTree;
  }

  public CallerChooser(final PsiMethod method, String title, Tree previousTree) {
    super(true);
    myMethod = method;
    myProject = myMethod.getProject();
    myTree = previousTree;
    setTitle(title);
    init();
  }

  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(false, ((float)0.6));
    JPanel result = new JPanel(new BorderLayout());
    if (myTree == null) {
      myTree = createTree();
    } else {
      final CheckedTreeNode root = (CheckedTreeNode)myTree.getModel().getRoot();
      myRoot = (MethodNode)root.getFirstChild();
    }
    JScrollPane scrollPane = new JScrollPane(myTree);
    splitter.setFirstComponent(scrollPane);
    myEditorField = createCallSitesViewer();
    myEditorField.setText(getText(myMethod));
    myEditorField.setBorder(IdeBorderFactory.createBorder());
    splitter.setSecondComponent(myEditorField);
    result.add(splitter);
    return result;
  }

  private String getText(final PsiMethod method) {
    final PsiFile file = method.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    final int start = document.getLineStartOffset(document.getLineNumber(method.getTextRange().getStartOffset()));
    final int end = document.getLineEndOffset(document.getLineNumber(method.getTextRange().getEndOffset()));
    return document.getText().substring(start, end);
  }

  private EditorTextField createCallSitesViewer() {
    return new EditorTextField("", myProject, StdFileTypes.JAVA) {
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.setOneLineMode(false);
        return editor;
      }
    };
  }

  private Tree createTree() {
    final CheckedTreeNode root = new CheckedTreeNode(null);
    myRoot = new MethodNode(myMethod);
    root.add(myRoot);
    final CheckboxTree.CheckboxTreeCellRenderer cellRenderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof MethodNode) {
          ((MethodNode)value).customizeRenderer(getTextRenderer());
        }
      }
    };
    Tree tree = new CheckboxTree(cellRenderer, root) {
      protected void checkNode(CheckedTreeNode node, boolean checked) {
        node.setChecked(checked);
        if (checked) {
          CheckedTreeNode parent = (CheckedTreeNode)node.getParent();
          while(parent != null) {
            parent.setChecked(true);
            parent = (CheckedTreeNode)parent.getParent();
          }
        }
        else {
          uncheckChildren(node);
        }
        repaint();
      }
    };
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.getSelectionModel().setSelectionPath(new TreePath(myRoot.getPath()));
    tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = e.getPath();
        if (path != null) {
          MethodNode node = (MethodNode)path.getLastPathComponent();
          final PsiMethod method = node.getMethod();
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            public void run() {
              myEditorField.setText(getText(method));
            }
          }, 300);
        }
      }
    });

    return tree;
  }

  private void uncheckChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      MethodNode child = (MethodNode)children.nextElement();
      child.setChecked(false);
      uncheckChildren(child);
    }
  }

  private void getSelectedMethods(Set<PsiMethod> methods) {
    MethodNode node = myRoot;
    getSelectedMethodsInner(node, methods);
    methods.remove(node.getMethod());
  }

  private void getSelectedMethodsInner(final MethodNode node, final Set<PsiMethod> allMethods) {
    if (node.isChecked()) {
      allMethods.add(node.getMethod());
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        getSelectedMethodsInner((MethodNode)children.nextElement(), allMethods);
      }
    }
  }

  protected void doOKAction() {
    final Set<PsiMethod> selectedMethods = new HashSet<PsiMethod>();
    getSelectedMethods(selectedMethods);
    callersChosen(selectedMethods);
    super.doOKAction();
  }

  abstract protected void callersChosen(Set<PsiMethod> allCallers);
}
