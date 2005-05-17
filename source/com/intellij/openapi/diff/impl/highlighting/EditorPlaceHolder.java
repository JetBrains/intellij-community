package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.DiffVersionComponent;
import com.intellij.openapi.diff.impl.util.ContentDocumentListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;

class EditorPlaceHolder extends DiffMarkup implements DiffVersionComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.highlighting.EditorWrapper");
  private EditorEx myEditor;
  private DiffContent myContent;
  private final FragmentSide mySide;
  private ContentChangeListener myListener = null;

  public EditorPlaceHolder(FragmentSide side, Project project) {
    super(project);
    mySide = side;
    resetHighlighters();
  }

  public void addListener(ContentChangeListener listener) {
    LOG.assertTrue(myListener == null);
    myListener = listener;
  }

  protected void doDispose() {
    LOG.assertTrue(!isDisposed());
    super.doDispose();
    fireContentChanged();
  }

  private void fireContentChanged() {
    myListener.onContentChangedIn(this);
  }

  public void setContent(final DiffContent content) {
    disposeEditor();
    myContent = content;
    if (myContent != null) {
      Document document = myContent.getDocument();
      final EditorFactory editorFactory = EditorFactory.getInstance();
      myEditor = DiffUtil.createEditor(document, getProject(), false);
      addDisposable(new Disposable() {
        public void dispose() {
          editorFactory.releaseEditor(myEditor);
          myEditor = null;
        }
      });
      ContentDocumentListener.install(myContent, this);
    }
    fireContentChanged();
  }

  public EditorEx getEditor() { return myEditor; }

  public FragmentSide getSide() { return mySide; }

  public DiffContent getContent() {
    return myContent;
  }

  public void removeContent() {
    setContent(null);
  }
}
