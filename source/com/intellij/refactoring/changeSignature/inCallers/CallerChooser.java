package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.ui.Tree;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.Enumeration;
import java.util.ArrayList;

/**
 * @author ven
 */
public abstract class CallerChooser extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.inCallers.CallerChooser");
  PsiMethod myMethod;
  private EditorTextField myEditorField;
  private Alarm myAlarm = new Alarm();
  private MethodNode myRoot;
  private Project myProject;

  public CallerChooser(final PsiMethod method, String title) {
    super(true);
    myMethod = method;
    myProject = myMethod.getProject();
    setTitle(title);
    init();
  }

  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(false, ((float)0.6));
    JPanel result = new JPanel(new BorderLayout());
    final Tree tree = createTree();
    JScrollPane scrollPane = new JScrollPane(tree);
    splitter.setFirstComponent(scrollPane);
    myEditorField = createCallSitesViewer();
    myEditorField.setText(getText(myMethod));
    myEditorField.setBorder(IdeBorderFactory.createBorder());
    splitter.setSecondComponent(myEditorField);
    result.add(splitter);

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
        super.checkNode(node, checked);
        updateChildren(node, !checked);
      }
    };
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.getSelectionModel().setSelectionPath(new TreePath(myRoot.getPath()));
    return tree;
  }

  private void updateChildren(final CheckedTreeNode node, boolean toEnable) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      MethodNode child = (MethodNode)children.nextElement();
      child.setEnabled(toEnable);
      updateChildren(child, toEnable);
    }
  }

  private PsiMethod[] getSelectedMethods () {
    MethodNode node = myRoot;
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();
    getSelectedMethodsInner(node, result);
    return result.toArray(new PsiMethod[result.size()]);
  }

  private void getSelectedMethodsInner(final MethodNode node, final ArrayList<PsiMethod> methods) {
    if (node.isChecked()) {
      methods.add(node.getMethod());
    } else {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        getSelectedMethodsInner((MethodNode)children.nextElement(), methods);
      }
    }
  }

  protected void doOKAction() {
    if (!verifyPaths(myRoot)) {
      Messages.showErrorDialog(myProject, "Not all paths to refactored method are covered", ChangeSignatureHandler.REFACTORING_NAME);
      return;
    }

    final PsiMethod[] selectedMethods = getSelectedMethods();
    LOG.assertTrue(selectedMethods.length > 0);
    callersChosen(selectedMethods);
    super.doOKAction();
  }

  private boolean verifyPaths(final MethodNode node) {
    if (node.isChecked()) return true;
    final Enumeration children = node.children();
    if (!children.hasMoreElements()) return false;

    while (children.hasMoreElements()) {
      if (!verifyPaths((MethodNode)children.nextElement())) return false;
    }
    return true;
  }

  abstract protected void callersChosen (PsiMethod[] callers);
}
