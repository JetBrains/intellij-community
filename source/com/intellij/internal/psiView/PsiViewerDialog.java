/**
 * class PsiViewerDialog
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

public class PsiViewerDialog extends DialogWrapper {
  private Project myProject;

  private Tree myTree;
  private ViewerTreeBuilder myTreeBuilder;

  private Editor myEditor;
  private String myLastParsedText = null;

  private JRadioButton myRbMethod;
  private JRadioButton myRbCodeBlock;
  private JRadioButton myRbExpression;
  private JRadioButton[] myFileTypeButtons;
  private FileType[] myFileTypes;

  private JCheckBox myShowWhiteSpacesBox;

  private JPanel myStructureTreePanel;
  private JPanel myTextPanel;
  private JPanel myPanel;
  private JPanel myChoicesPanel;

  public PsiViewerDialog(Project project,boolean modal) {
    super(project, true);
    setTitle("PSI Viewer");
    myProject = project;
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.updateUI();
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);
    myTreeBuilder = new ViewerTreeBuilder(project, myTree);

    myTree.addTreeSelectionListener(new MyTreeSelectionListener());

    JScrollPane scrollPane = new JScrollPane(myTree);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(scrollPane, BorderLayout.CENTER);
    myStructureTreePanel.setLayout(new BorderLayout());
    myStructureTreePanel.add(panel, BorderLayout.CENTER);

    setModal(modal);
    setOKButtonText("&Build PSI Tree");
    init();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.internal.psiView.PsiViewerDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  protected void init() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    myEditor = editorFactory.createEditor(document, myProject);
    myEditor.getSettings().setFoldingOutlineShown(false);

    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes,new Comparator<FileType>() {
      public int compare(final FileType o1, final FileType o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    List<FileType> customFileTypes = new ArrayList<FileType>();

    for (FileType fileType : fileTypes) {
      if (fileType != StdFileTypes.GUI_DESIGNER_FORM && fileType != StdFileTypes.IDEA_MODULE && fileType != StdFileTypes.IDEA_PROJECT &&
          fileType != StdFileTypes.IDEA_WORKSPACE && fileType != StdFileTypes.ARCHIVE && fileType != StdFileTypes.UNKNOWN &&
          fileType != StdFileTypes.PLAIN_TEXT && !(fileType instanceof CustomFileType) && !fileType.isBinary() && !fileType.isReadOnly()) {
        customFileTypes.add(fileType);
      }
    }

    myFileTypes = customFileTypes.toArray(new FileType[customFileTypes.size()]);
    myFileTypeButtons = new JRadioButton[myFileTypes.length];

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbMethod);
    bg.add(myRbCodeBlock);
    bg.add(myRbExpression);

    Box choicesBox = Box.createHorizontalBox();

    for (int i = 0; i < myFileTypes.length; i++) {
      FileType fileType = myFileTypes[i];
      JRadioButton button = new JRadioButton(fileType.getName()+" file");
      bg.add(button);
      choicesBox.add(button);
      myFileTypeButtons[i] = button;
    }

    myRbExpression.setSelected(true);

    myChoicesPanel.setLayout(new BorderLayout());
    myChoicesPanel.add(choicesBox, BorderLayout.CENTER);

    final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    myShowWhiteSpacesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        treeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
        myTreeBuilder.updateFromRoot();
      }
    });
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    super.init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected void doOKAction() {
    final String text = myEditor.getDocument().getText();
    if ("".equals(text.trim())) {
      return;
    }
    myLastParsedText = text;
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    PsiElement rootElement = null;
    try {
      PsiElementFactory factory = psiManager.getElementFactory();
      if (myRbMethod.isSelected()) {
        rootElement = factory.createMethodFromText(text, null);
      }
      else if (myRbCodeBlock.isSelected()) {
        rootElement = factory.createCodeBlockFromText(text, null);
      }
      else if (myRbExpression.isSelected()) {
        rootElement = factory.createExpressionFromText(text, null);
      }
      else {
        for (int i = 0; i < myFileTypeButtons.length; i++) {
          JRadioButton fileTypeButton = myFileTypeButtons[i];

          if (fileTypeButton.isSelected()) {
            rootElement = factory.createFileFromText("Dummy."+myFileTypes[i].getDefaultExtension(), text);
          }
        }
      }
    }
    catch (IncorrectOperationException e1) {
      rootElement = null;
      Messages.showMessageDialog(
        myProject,
        e1.getMessage(),
        "Error",
        Messages.getErrorIcon()
      );
    }
    ViewerTreeStructure structure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
    structure.setRootPsiElement(rootElement);

    myTreeBuilder.updateFromRoot();
    myTree.setRootVisible(true);
    myTree.expandRow(0);
    myTree.setRootVisible(false);
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    private TextAttributes myAttributes;
    private RangeHighlighter myHighlighter;

    public MyTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setBackgroundColor(new Color(0, 0, 128));
      myAttributes.setForegroundColor(Color.white);
    }

    public void valueChanged(TreeSelectionEvent e) {
      if (!myEditor.getDocument().getText().equals(myLastParsedText)) return;
      TreePath path = myTree.getSelectionPath();
      if (path == null){
        clearSelection();
      }
      else{
        clearSelection();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return;
        ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor)node.getUserObject();
        Object elementObject = descriptor.getElement();
        if (elementObject instanceof PsiElement) {
          PsiElement element = (PsiElement)elementObject;
          TextRange range = element.getTextRange();
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          final ViewerTreeStructure treeStructure = (ViewerTreeStructure)myTreeBuilder.getTreeStructure();
          PsiElement rootPsiElement = treeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }

          final int textLength = myEditor.getDocument().getTextLength();
          if (end < textLength) {
            myHighlighter = myEditor.getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.FIRST + 1, myAttributes, HighlighterTargetArea.EXACT_RANGE);
          }
        }
      }
    }

    private void clearSelection() {
      if (myHighlighter != null) {
        myEditor.getMarkupModel().removeHighlighter(myHighlighter);
        myHighlighter = null;
      }
    }
  }

  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    EditorFactory.getInstance().releaseEditor(myEditor);

    super.dispose();
  }
}
