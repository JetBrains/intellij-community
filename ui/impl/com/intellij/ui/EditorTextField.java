package com.intellij.ui;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author max
 */
public class EditorTextField extends JPanel implements DocumentListener, TextComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.EditorTextField");
  public static final Key<Boolean> SUPPLEMENTARY_KEY = Key.create("Supplementary");

  private Document myDocument;
  private Project myProject;
  private FileType myFileType;
  private EditorEx myEditor = null;
  private Component myNextFocusable = null;
  private boolean myWholeTextSelected = false;
  private ArrayList<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private boolean myIsListenerInstalled = false;
  private boolean myIsViewer;
  private boolean myIsSupplementary;
  private boolean myInheritSwingFont = true;
  private Color myEnforcedBgColor = null;
  private boolean myUseTextFieldPreferredSize = true;

  public EditorTextField(String text) {
    this(EditorFactory.getInstance().createDocument(text), null, StdFileTypes.PLAIN_TEXT);
  }

  public EditorTextField(String text, Project project, FileType fileType) {
    this(EditorFactory.getInstance().createDocument(text), project, fileType, false);
  }

  public EditorTextField(Document document, Project project, FileType fileType) {
    this(document, project, fileType, false);
  }

  public EditorTextField(Document document, Project project, FileType fileType, boolean isViewer) {
    myIsViewer = isViewer;
    setDocument(document);
    myProject = project;
    myFileType = fileType;
    setLayout(new BorderLayout());
    enableEvents(AWTEvent.KEY_EVENT_MASK);
    // todo[dsl,max]
    setFocusable(true);
    // dsl: this is a weird way of doing things....
    addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        requestFocus();
      }

      public void focusLost(FocusEvent e) {
      }
    });
  }

  public void setSupplementary(boolean supplementary) {
    myIsSupplementary = supplementary;
    if (myEditor != null) {
      myEditor.putUserData(SUPPLEMENTARY_KEY, supplementary);
    }
  }

  public void setFontInheritedFromLAF(boolean b) {
    myInheritSwingFont = b;
    setDocument(myDocument); // reinit editor.
  }

  public String getText() {
    return myDocument.getText();
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    myEnforcedBgColor = bg;
    if (myEditor != null) {
      myEditor.setBackgroundColor(bg);
    }
  }

  public JComponent getComponent() {
    return this;
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    installDocumentListener();
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    uninstallDocumentListener(false);
  }

  public void beforeDocumentChange(DocumentEvent event) {
    for (int i = 0; i < myDocumentListeners.size(); i++) {
      DocumentListener documentListener = myDocumentListeners.get(i);
      documentListener.beforeDocumentChange(event);
    }
  }

  public void documentChanged(DocumentEvent event) {
    for (int i = 0; i < myDocumentListeners.size(); i++) {
      DocumentListener documentListener = myDocumentListeners.get(i);
      documentListener.documentChanged(event);
    }
  }

  public Project getProject() {
    return myProject;
  }

  public Document getDocument() {
    return myDocument;
  }

  public void setDocument(Document document) {
    if (myDocument != null) {
      /*
      final UndoManager undoManager = myProject != null
      ? UndoManager.getInstance(myProject)
      : UndoManager.getGlobalInstance();
      undoManager.clearUndoRedoQueue(myDocument);
      */

      uninstallDocumentListener(true);
    }

    myDocument = document;
    installDocumentListener();
    if (myEditor == null) return;

    //MainWatchPanel watches the oldEditor's focus in order to remove debugger combobox when focus is lost
    //we should first transfer focus to new oldEditor and only then remove current oldEditor
    //MainWatchPanel check that oldEditor.getParent == newEditor.getParent and does not remove oldEditor in such cases

    boolean isFocused = isFocusOwner();
    Editor editor = myEditor;
    myEditor = createEditor();
    add(myEditor.getComponent(), BorderLayout.CENTER);
    releaseEditor(editor);

    validate();
    if (isFocused) {
      myEditor.getContentComponent().requestFocus();
    }
  }

  private void installDocumentListener() {
    if (myDocument != null && myDocumentListeners.size() > 0 && !myIsListenerInstalled) {
      myIsListenerInstalled = true;
      myDocument.addDocumentListener(this);
    }
  }

  private void uninstallDocumentListener(boolean force) {
    if (myDocument != null && myIsListenerInstalled && (force || myDocumentListeners.size() == 0)) {
      myIsListenerInstalled = false;
      myDocument.removeDocumentListener(this);
    }
  }

  public void setText(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          public void run() {
            myDocument.replaceString(0, myDocument.getTextLength(), text);
            if (myEditor != null) {
              myEditor.getCaretModel().moveToOffset(myDocument.getTextLength());
            }
          }
        }, null, null);
      }
    });
  }

  public void selectAll() {
    if (myEditor != null) {
      myEditor.getSelectionModel().setSelection(0, myDocument.getTextLength());
    }
    else {
      myWholeTextSelected = true;
    }
  }

  public void removeSelection() {
    if (myEditor != null) {
      myEditor.getSelectionModel().removeSelection();
    }
    else {
      myWholeTextSelected = false;
    }
  }

  public CaretModel getCaretModel() {
    return myEditor.getCaretModel();
  }

  public boolean isFocusOwner() {
    if (myEditor != null) {
      return IJSwingUtilities.hasFocus(myEditor.getContentComponent());
    }
    return super.isFocusOwner();
  }

  void releaseEditor(final Editor editor) {
    remove(editor.getComponent());
    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = new Runnable() {
      public void run() {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    };

    if (application.isUnitTestMode()) {
      runnable.run();
    } else {
      application.invokeLater(runnable);
    }
  }

  public void addNotify() {
    LOG.assertTrue(myEditor == null);

    boolean isFocused = isFocusOwner();

    myEditor = createEditor();
    add(myEditor.getComponent(), BorderLayout.CENTER);

    super.addNotify();

    if (myNextFocusable != null) {
      myEditor.getContentComponent().setNextFocusableComponent(myNextFocusable);
      myNextFocusable = null;
    }
    revalidate();
    if (isFocused) {
      requestFocus();
    }
  }

  public void removeNotify() {
    super.removeNotify();
    LOG.assertTrue(myEditor != null);
    releaseEditor(myEditor);
    myEditor = null;
  }

  public void setFont(Font font) {
    super.setFont(font);
    if (myEditor != null) {
      setupEditorFont(myEditor);
    }
  }

  protected EditorEx createEditor() {
    LOG.assertTrue(myDocument != null);

    final EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor;
    if (!myIsViewer) {
      editor = myProject != null
                            ? (EditorEx)factory.createEditor(myDocument, myProject)
                            : (EditorEx)factory.createEditor(myDocument);
    }
    else {
      editor = myProject != null
                            ? (EditorEx)factory.createViewer(myDocument, myProject)
                            : (EditorEx)factory.createViewer(myDocument);
    }

    final EditorSettings settings = editor.getSettings();
    settings.setAdditionalLinesCount(0);
    settings.setAdditionalColumnsCount(1);
    settings.setRightMarginShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setVirtualSpace(false);
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(false);
    settings.setLineCursorWidth(1);

    setupEditorFont(editor);

    if (myProject != null) {
      editor.setHighlighter(HighlighterFactory.createHighlighter(myProject, myFileType));
    }
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);
    editor.setOneLineMode(true);
    editor.getCaretModel().moveToOffset(myDocument.getTextLength());
    if (!shouldHaveBorder()) {
      editor.getScrollPane().setBorder(null);
    }

    if (myIsViewer) {
      editor.getSelectionModel().removeSelection();
    }
    else if (myWholeTextSelected) {
      editor.getSelectionModel().setSelection(0, myDocument.getTextLength());
    }

    editor.setBackgroundColor(getBackgroundColor(!myIsViewer));

    if (myUseTextFieldPreferredSize) {
      editor.getComponent().setPreferredSize(new JTextField().getPreferredSize());
    }

    editor.putUserData(SUPPLEMENTARY_KEY, myIsSupplementary);

    return editor;
  }

  private void setupEditorFont(final EditorEx editor) {
    if (myInheritSwingFont) {
      editor.getColorsScheme().setEditorFontName(getFont().getFontName());
      editor.getColorsScheme().setEditorFontSize(getFont().getSize());
    }
  }

  protected boolean shouldHaveBorder() {
    return true;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myIsViewer = !enabled;
    if (myEditor == null) {
      return;
    }
    Editor editor = myEditor;
    myEditor = createEditor();
    add(myEditor.getComponent(), BorderLayout.CENTER);
    revalidate();
    releaseEditor(editor);
  }

  private Color getBackgroundColor(boolean enabled){
    if (myEnforcedBgColor != null) return myEnforcedBgColor;
    return enabled ? UIUtil.getActiveTextColor()
    : UIUtil.getInactiveTextColor();
  }

  public Dimension getPreferredSize() {
    if (myEditor != null) {
      final Dimension preferredSize = new Dimension(myEditor.getComponent().getPreferredSize());
      final Insets insets = getInsets();
      if (insets != null) {
        preferredSize.width += insets.left;
        preferredSize.width += insets.right;
        preferredSize.height += insets.top;
        preferredSize.height += insets.bottom;
      }
      return preferredSize;
    }
    return new Dimension(100, 20);
  }

  public void setUseTextFieldPreferredSize(final boolean b) {
    myUseTextFieldPreferredSize = b;
  }

  public Component getNextFocusableComponent() {
    if (myEditor == null && myNextFocusable == null) return super.getNextFocusableComponent();
    if (myEditor == null) return myNextFocusable;
    return myEditor.getContentComponent().getNextFocusableComponent();
  }

  public void setNextFocusableComponent(Component aComponent) {
    if (myEditor != null) {
      myEditor.getContentComponent().setNextFocusableComponent(aComponent);
      return;
    }
    myNextFocusable = aComponent;
  }


  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.isConsumed() || !myEditor.processKeyTyped(e)) {
      return super.processKeyBinding(ks, e, condition, pressed);
    }
    return true;
  }


  public void requestFocus() {
    if (myEditor != null) {
      myEditor.getContentComponent().requestFocus();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    else {
      super.requestFocus();
    }
  }

  public boolean requestFocusInWindow() {
    if (myEditor != null) {
      final boolean b = myEditor.getContentComponent().requestFocusInWindow();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return b;
    }
    else {
      return super.requestFocusInWindow();
    }
  }

  public Editor getEditor() {
    return myEditor;
  }
}
