package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.EditorPopupHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class TextEditorComponent extends JPanel implements DataProvider{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.TextEditorComponent");

  private final Project myProject;
  private final VirtualFile myFile;
  private final TextEditorImpl myTextEditor;
  /**
   * Document to be edited
   */
  private final Document myDocument;

  private final MyEditorMouseListener myEditorMouseListener;
  private final MyEditorCaretListener myEditorCaretListener;
  private final MyDocumentListener myDocumentListener;
  private final MyEditorPropertyChangeListener myEditorPropertyChangeListener;
  private final MyFileTypeListener myFileTypeListener;
  private final MyVirtualFileListener myVirtualFileListener;
  private final Editor myEditor;

  /**
   * Whether the editor's document is modified or not
   */
  private boolean myModified;
  /**
   * Whether the editor is valid or not
   */
  private boolean myValid;

  TextEditorComponent(final Project project, final VirtualFile file, final TextEditorImpl textEditor) {
    super(new BorderLayout (), true);

    if(project==null){
      throw new IllegalArgumentException("project cannot be null");
    }
    if(file==null){
      throw new IllegalArgumentException("file cannot be null");
    }
    if (textEditor == null) {
      throw new IllegalArgumentException("textEditor cannot be null");
    }
    assertThread();

    myProject = project;
    myFile = file;
    myTextEditor = textEditor;

    myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    LOG.assertTrue(myDocument!=null);
    myDocumentListener = new MyDocumentListener();
    myDocument.addDocumentListener(myDocumentListener);

    myEditorMouseListener = new MyEditorMouseListener();
    myEditorCaretListener = new MyEditorCaretListener();
    myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();

    myFileTypeListener = new MyFileTypeListener();
    FileTypeManager.getInstance().addFileTypeListener(myFileTypeListener);

    myVirtualFileListener = new MyVirtualFileListener();
    myFile.getFileSystem().addVirtualFileListener(myVirtualFileListener);
    myEditor=createEditor();
    add (myEditor.getComponent (), BorderLayout.CENTER);
    myModified = isModifiedImpl();
    myValid = isEditorValidImpl();
    LOG.assertTrue(myValid);
  }

  /**
   * Disposes all resources allocated be the TextEditorComponent. It disposes all created
   * editors, unregisters listeners. The behaviour of the splitter after disposing is
   * unpredictable.
   */
  protected void dispose(){
    myDocument.removeDocumentListener(myDocumentListener);

    disposeEditor(myEditor);

    FileTypeManager.getInstance().removeFileTypeListener(myFileTypeListener);
    myFile.getFileSystem().removeVirtualFileListener(myVirtualFileListener);
    //myFocusWatcher.deinstall(this);
    //removePropertyChangeListener(mySplitterPropertyChangeListener);

    //super.dispose();
  }

  /**
   * Should be invoked when the corresponding <code>TextEditorImpl</code>
   * is selected. Updates the status bar.
   */
  void selectNotify(){
    updateStatusBar();
  }

  /**
   * Should be invoked when the corresponding <code>TextEditorImpl</code>
   * is deselected. Clears the status bar.
   */
  void deselectNotify(){
    StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
    LOG.assertTrue(statusBar != null);
    statusBar.clear();
  }

  private static void assertThread(){
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  /**
   * @return most recently used editor. This method never returns <code>null</code>.
   */
  Editor getEditor(){
    return myEditor;
  }

  /**
   * @return created editor. This editor should be released by {@link #disposeEditor(Editor) }
   * method.
   */
  private Editor createEditor(){
    Editor editor=EditorFactory.getInstance().createEditor(myDocument, myProject);
    ((EditorMarkupModel) editor.getMarkupModel()).setErrorStripeVisible(true);
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(myFile, EditorColorsManager.getInstance().getGlobalScheme(), myProject);
    ((EditorEx) editor).setHighlighter(highlighter);
    ((EditorEx) editor).setFile(myFile);

    editor.addEditorMouseListener(myEditorMouseListener);
    editor.getCaretModel().addCaretListener(myEditorCaretListener);
    ((EditorEx)editor).addPropertyChangeListener(myEditorPropertyChangeListener);

    TextEditorProvider.putTextEditor(editor, myTextEditor);
    return editor;
  }

  /**
   * Disposes resources allocated by the specified editor view and registeres all
   * it's listeners
   */
  private void disposeEditor(final Editor editor){
    EditorFactory.getInstance().releaseEditor(editor);
    editor.removeEditorMouseListener(myEditorMouseListener);
    editor.getCaretModel().removeCaretListener(myEditorCaretListener);
    ((EditorEx)editor).removePropertyChangeListener(myEditorPropertyChangeListener);
  }

  /**
   * @return whether the editor's document is modified or not
   */
  boolean isModified(){
    assertThread();
    return myModified;
  }

  /**
   * Just calculates "modified" property
   */
  private boolean isModifiedImpl(){
    return FileDocumentManager.getInstance().isFileModified(myFile);
  }

  /**
   * Updates "modified" property and fires event if necessary
   */
  private void updateModifiedProperty(){
    Boolean oldModified=Boolean.valueOf(myModified);
    myModified = isModifiedImpl();
    myTextEditor.firePropertyChange(FileEditor.PROP_MODIFIED, oldModified, Boolean.valueOf(myModified));
  }

  /**
   * Name <code>isValid</code> is in use in <code>java.awt.Component</code>
   * so we change the name of method to <code>isEditorValid</code>
   *
   * @return whether the editor is valid or not
   */
  boolean isEditorValid(){
    return myValid;
  }

  /**
   * Just calculates
   */
  private boolean isEditorValidImpl(){
    return FileDocumentManager.getInstance().getDocument(myFile) != null;
  }

  private void updateValidProperty(){
    Boolean oldValid = Boolean.valueOf(myValid);
    myValid = isEditorValidImpl();
    myTextEditor.firePropertyChange(FileEditor.PROP_VALID, oldValid, Boolean.valueOf(myValid));
  }

  /**
   * Updates editors' highlighters. This should be done when the opened file
   * changes its file type.
   */
  private void updateHighlighters(){
    final EditorHighlighter highlighter = HighlighterFactory.createHighlighter(myProject, myFile.getName());
    ((EditorEx)myEditor).setHighlighter(highlighter);
  }

  /**
   * Updates frame's status bar: insert/overwrite mode, caret position
   */
  private void updateStatusBar(){
    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
    final Editor editor = getEditor();
    if (editor.isColumnMode()) {
      statusBar.setStatus(UIBundle.message("status.bar.column.status.text"));
    } else {
      statusBar.setStatus(editor.isInsertMode() ? UIBundle.message("status.bar.insert.status.text")
                          : UIBundle.message("status.bar.overwrite.status.text"));
    }
    boolean isWritable = editor.getDocument().isWritable();
    statusBar.setStatusEnabled(isWritable);
    statusBar.setWriteStatus(!isWritable);
    statusBar.setPosition(editor.getCaretModel().getLogicalPosition().line + 1 +
      ":" + (editor.getCaretModel().getLogicalPosition().column + 1)
    );
    statusBar.updateEditorHighlightingStatus(false);
  }

  public Object getData(final String dataId) {
    final Object o = ((FileEditorManagerImpl)FileEditorManager.getInstance(myProject)).getData(dataId, myEditor);
    if (o != null) return o;

    if (dataId.equals(DataConstants.EDITOR)) {
      return myEditor;
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.EDITOR))) {
      return InjectedLanguageUtil.getEditorForInjectedLanguage(myEditor, getPsiFile());
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.PSI_ELEMENT))) {
      return getPsiElementIn((Editor)getData(AnActionEvent.injectedId(DataConstants.EDITOR)));
    }
    if (DataConstants.PSI_ELEMENT.equals(dataId)){
      return getPsiElementIn(myEditor);
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.LANGUAGE))) {
      PsiFile psiFile = (PsiFile)getData(AnActionEvent.injectedId(DataConstants.PSI_FILE));
      Editor editor = (Editor)getData(AnActionEvent.injectedId(DataConstants.EDITOR));
      if (psiFile == null || editor == null) return null;
      return PsiUtil.getLanguageAtOffset(psiFile, editor.getCaretModel().getOffset());
    }
    if (DataConstants.LANGUAGE.equals(dataId)) {
      final Editor editor = myEditor;
      final PsiFile psiFile = getPsiFile();
      if (psiFile == null) return null;
      return PsiUtil.getLanguageAtOffset(psiFile, editor.getCaretModel().getOffset());
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.VIRTUAL_FILE))) {
      PsiFile psiFile = (PsiFile)getData(AnActionEvent.injectedId(DataConstants.PSI_FILE));
      if (psiFile == null) return null;
      return psiFile.getVirtualFile();
    }
    if (dataId.equals(DataConstants.VIRTUAL_FILE)) {
      return myFile.isValid()? myFile : null;  // fix for SCR 40329
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.PSI_FILE))) {
      Editor editor = (Editor)getData(AnActionEvent.injectedId(DataConstants.EDITOR));
      if (editor == null) return null;
      return PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    }
    if (dataId.equals(DataConstants.PSI_FILE)) {
      return getPsiFile();
    }
    if (DataConstantsEx.TARGET_PSI_ELEMENT.equals(dataId)) {
      /*
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);
      LOG.assertTrue(psiFile != null);
      if (!(psiFile instanceof PsiJavaFile)) {
        return null;
      }

      PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
      PsiDirectory directory = psiJavaFile.getContainingDirectory();
      if (directory == null) {
        return null;
      }
      PsiPackage aPackage = directory.getPackage();
      if (aPackage == null) {
        return null;
      }
      return directory;
      */
      // [dsl] in Editor we do not have any specific target psi element
      // we only guess
      return null;
    }
    return null;
  }

  private Object getPsiElementIn(final Editor editor) {
    final PsiFile psiFile = getPsiFile();
    if (psiFile == null) return null;
    return TargetElementUtil.findTargetElement(editor,
                                          TargetElementUtil.THROW_STATEMENT_ACCEPTED |
                                          TargetElementUtil.THROWS_ACCEPTED |
                                          TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                          TargetElementUtil.ELEMENT_NAME_ACCEPTED |
                                          TargetElementUtil.NEW_AS_CONSTRUCTOR |
                                          TargetElementUtil.LOOKUP_ITEM_ACCEPTED);
  }

  private PsiFile getPsiFile() {
    return myFile.isValid()? PsiManager.getInstance(myProject).findFile(myFile) : null; // fix for SCR 40329
  }


  /**
   * Shows popup menu
   */
  private static final class MyEditorMouseListener extends EditorPopupHandler {
    public void invokePopup(final EditorMouseEvent event) {
      if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_POPUP);
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
        MouseEvent e = event.getMouseEvent();
        popupMenu.getComponent().show(e.getComponent(), e.getX(), e.getY());
        e.consume();
      }
    }
  }

  /**
   * Getts events about caret movements and modifies status bar
   */
  private final class MyEditorCaretListener implements CaretListener {
    public void caretPositionChanged(final CaretEvent e) {
      assertThread();
      if (e.getEditor() == getEditor()) {
        updateStatusBar();
      }
    }
  }

  /**
   * Updates "modified" property
   */
  private final class MyDocumentListener extends DocumentAdapter {
    /**
     * We can reuse this runnable to decrease number of allocated object.
     */
    private final Runnable myUpdateRunnable;

    public MyDocumentListener() {
      myUpdateRunnable = new Runnable() {
        public void run() {
          updateModifiedProperty();
        }
      };
    }

    public void documentChanged(DocumentEvent e) {
      // document's timestamp is changed later on undo or PSI changes
      ApplicationManager.getApplication().invokeLater(myUpdateRunnable);
    }
  }

  /**
   * Gets event obout insert/overwrite modes
   */
  private final class MyEditorPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(final PropertyChangeEvent e) {
      assertThread();
      final String propertyName = e.getPropertyName();
      if(EditorEx.PROP_INSERT_MODE.equals(propertyName) || EditorEx.PROP_COLUMN_MODE.equals(propertyName)){
        updateStatusBar();
      }
    }
  }

  /**
   * Listen changes of file types. When type of the file changes we need
   * to also change highlighter.
   */
  private final class MyFileTypeListener implements FileTypeListener {
    public void beforeFileTypesChanged(FileTypeEvent event) {
    }

    public void fileTypesChanged(final FileTypeEvent event) {
      assertThread();
      // File can be invalid after file type changing. The editor should be removed
      // by the FileEditorManager if it's invalid.
      updateValidProperty();
      if(isValid()){
        updateHighlighters();
      }
    }
  }

  /**
   * Updates "valid" property and highlighters (if necessary)
   */
  private final class MyVirtualFileListener extends VirtualFileAdapter{
    public void propertyChanged(final VirtualFilePropertyEvent e) {
      if(VirtualFile.PROP_NAME.equals(e.getPropertyName())){
        // File can be invalidated after file changes name (extension also
        // can changes). The editor should be removed if it's invalid.
        updateValidProperty();
        if(isValid()){
          updateHighlighters();
        }
      }
    }

    public void contentsChanged(VirtualFileEvent event){
      if (event.isFromSave()){ // commit
        assertThread();
        VirtualFile file = event.getFile();
        LOG.assertTrue(file.isValid());
        if(myFile.equals(file)){
          updateModifiedProperty();
        }
      }
    }
  }
}
