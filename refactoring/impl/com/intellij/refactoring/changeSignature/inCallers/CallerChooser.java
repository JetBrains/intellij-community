package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.inCallers.CallerChooser");
  PsiMethod myMethod;
  private Alarm myAlarm = new Alarm();
  private MethodNode myRoot;
  private Project myProject;
  private Tree myTree;
  private TreeSelectionListener myTreeSelectionListener;
  private Editor myCallerEditor;
  private Editor myCalleeEditor;

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
    }
    else {
      final CheckedTreeNode root = (CheckedTreeNode)myTree.getModel().getRoot();
      myRoot = (MethodNode)root.getFirstChild();
    }
    myTreeSelectionListener = new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = e.getPath();
        if (path != null) {
          final MethodNode node = (MethodNode)path.getLastPathComponent();
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            public void run() {
              updateEditorTexts(node);
            }
          }, 300);
        }
      }
    };
    myTree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    JScrollPane scrollPane = new JScrollPane(myTree);
    splitter.setFirstComponent(scrollPane);
    final JComponent callSitesViewer = createCallSitesViewer();
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) {
      selectionPath = new TreePath(myRoot.getPath());
      myTree.getSelectionModel().addSelectionPath(selectionPath);
    }

    final MethodNode node = (MethodNode)selectionPath.getLastPathComponent();
    updateEditorTexts(node);

    splitter.setSecondComponent(callSitesViewer);
    result.add(splitter);
    return result;
  }

  private void updateEditorTexts(final MethodNode node) {
    final MethodNode parentNode = (MethodNode)node.getParent();
    final String callerText = node != myRoot ? getText(node.getMethod()) : "";
    final Document callerDocument = myCallerEditor.getDocument();
    final String calleeText = node != myRoot ? getText(parentNode.getMethod()) : "";
    final Document calleeDocument = myCalleeEditor.getDocument();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        callerDocument.replaceString(0, callerDocument.getTextLength(), callerText);
        calleeDocument.replaceString(0, calleeDocument.getTextLength(), calleeText);
      }
    });

    final PsiMethod caller = node.getMethod();
    final PsiMethod callee = parentNode != null ? parentNode.getMethod() : null;
    if (caller != null && callee != null) {
      HighlightManager highlighter = HighlightManager.getInstance(myProject);
      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
      int start = getStartOffset(caller);
      for (PsiReference ref : ReferencesSearch.search(callee, new LocalSearchScope(caller), false).findAll()) {
        final PsiElement element = ref.getElement();
        if (element != null) {
          highlighter.addRangeHighlight(myCallerEditor, element.getTextRange().getStartOffset() - start,
                                        element.getTextRange().getEndOffset() - start, attributes, false, null);
        }
      }
    }
  }

  public void dispose() {
    myTree.removeTreeSelectionListener(myTreeSelectionListener);
    EditorFactory.getInstance().releaseEditor(myCallerEditor);
    EditorFactory.getInstance().releaseEditor(myCalleeEditor);
    super.dispose();
  }

  private String getText(final PsiMethod method) {
    if (method == null) return "";
    final PsiFile file = method.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    final int start = document.getLineStartOffset(document.getLineNumber(method.getTextRange().getStartOffset()));
    final int end = document.getLineEndOffset(document.getLineNumber(method.getTextRange().getEndOffset()));
    return document.getText().substring(start, end);
  }

  private int getStartOffset (final PsiMethod method) {
    LOG.assertTrue(method != null);
    final PsiFile file = method.getContainingFile();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    return document.getLineStartOffset(document.getLineNumber(method.getTextRange().getStartOffset()));
  }

  private JComponent createCallSitesViewer() {
    Splitter splitter = new Splitter(true);
    myCallerEditor = createEditor();
    myCalleeEditor = createEditor();
    final JComponent callerComponent = myCallerEditor.getComponent();
    callerComponent.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("caller.chooser.caller.method")));
    splitter.setFirstComponent(callerComponent);
    final JComponent calleeComponent = myCalleeEditor.getComponent();
    calleeComponent.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("caller.chooser.callee.method")));
    splitter.setSecondComponent(calleeComponent);
    splitter.setBorder(IdeBorderFactory.createBorder());
    return splitter;
  }

  private Editor createEditor() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    final Document document = editorFactory.createDocument("");
    final Editor editor = editorFactory.createViewer(document, myProject);
    ((EditorEx)editor).setHighlighter(HighlighterFactory.createHighlighter(myProject, StdFileTypes.JAVA));
    return editor;
  }

  private Tree createTree() {
    final CheckedTreeNode root = new MethodNode(null);
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
          while (parent != null) {
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

    return tree;
  }

  private static void uncheckChildren(final CheckedTreeNode node) {
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

  private static void getSelectedMethodsInner(final MethodNode node, final Set<PsiMethod> allMethods) {
    if (node.isChecked()) {
      PsiMethod method = node.getMethod();
      final PsiMethod[] superMethods = method.findDeepestSuperMethods();
      if (superMethods.length == 0) {
        allMethods.add(method);
      } else {
        for (PsiMethod superMethod : superMethods) {
          allMethods.add(superMethod);
        }
      }

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

  abstract protected void callersChosen(Set<PsiMethod> callers);
}
