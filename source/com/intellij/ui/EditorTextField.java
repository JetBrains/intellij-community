package com.intellij.ui;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author max
 */
public class EditorTextField extends JPanel implements DocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.EditorTextField");

  private Document myDocument;
  private Project myProject;
  private FileType myFileType;
  private EditorEx myEditor = null;
  private Component myNextFocusable = null;
  private boolean myWholeTextSelected = false;
  private ArrayList<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private boolean myIsListenerInstalled = false;
  private boolean myIsViewer;

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
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
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



  public String getText() {
    return myDocument.getText();
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
    add(myEditor.getComponent());
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

  private void releaseEditor(final Editor editor) {
    remove(editor.getComponent());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          EditorFactory.getInstance().releaseEditor(editor);
        }
      });
  }

  public void addNotify() {
    LOG.assertTrue(myEditor == null);

    boolean isFocused = isFocusOwner();

    myEditor = createEditor();
    add(myEditor.getComponent());

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
      myEditor.getColorsScheme().setEditorFontName(getFont().getFontName());
      myEditor.getColorsScheme().setEditorFontSize(getFont().getSize());
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

    editor.getColorsScheme().setEditorFontName(getFont().getFontName());
    editor.getColorsScheme().setEditorFontSize(getFont().getSize());

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
    return editor;
  }

  protected boolean shouldHaveBorder() {
    return true;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myIsViewer = !enabled;
    if (myEditor == null) return;
    Editor editor = myEditor;
    myEditor = createEditor();
    add(myEditor.getComponent());
    revalidate();
    releaseEditor(editor);
  }

  private Color getBackgroundColor(boolean enabled){
    return enabled ? UIManager.getColor("textActiveText")
    : UIManager.getColor("textInactiveText");
  }

  public Dimension getPreferredSize() {
    if (myEditor != null) return myEditor.getComponent().getPreferredSize();
    return new Dimension(100, 20);
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
    if (!myEditor.processKeyTyped(e)) {
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

  public Editor getEditor() {
    return myEditor;
  }
}
