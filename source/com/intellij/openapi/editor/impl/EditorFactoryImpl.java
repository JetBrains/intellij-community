package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocatorEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.util.EventDispatcher;
import com.intellij.util.text.CharArrayCharSequence;

import javax.swing.*;
import java.util.ArrayList;

public class EditorFactoryImpl extends EditorFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFactoryImpl");

  private final EditorEventMulticasterImpl myEditorEventMulticaster = new EditorEventMulticasterImpl();

  private EventDispatcher<EditorFactoryListener> myEditorFactoryEventDispatcher = EventDispatcher.create(EditorFactoryListener.class);

  private ArrayList<Editor> myEditors = new ArrayList<Editor>();

  public EditorFactoryImpl(ProjectManager projectManager) {
    LaterInvocatorEx.addModalityStateListener(
      new ModalityStateListener() {
        public void beforeModalityStateChanged() {
          for (int i = 0; i < myEditors.size(); i++) {
            Editor editor = myEditors.get(i);
            ((EditorImpl)editor).beforeModalityStateChanged();
          }
        }
      }
    );

    projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectClosed(final Project project) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            validateEditorsAreReleased(project);
          }
        });
      }
    });
  }

  public String getComponentName() {
    return "EditorFactory";
  }

  public void initComponent() { }

  public void validateEditorsAreReleased(Project project) {
    for (int i = 0; i < myEditors.size(); i++) {
      Editor editor = myEditors.get(i);
      if (editor.getProject() == project) {
        LOG.error("Editor for the document with the following text hasn't been released:\n" + editor.getDocument().getText());
      }
    }
  }

  public void disposeComponent() {
  }

  public Document createDocument(char[] text) {
    return createDocument(new CharArrayCharSequence(text));
  }

  public Document createDocument(CharSequence text) {
    DocumentImpl document = new DocumentImpl(text);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  public void refreshAllEditors() {
    Editor[] editors = getAllEditors();
    for (int i = 0; i < editors.length; i++) {
      EditorEx editor = (EditorEx)editors[i];
      editor.reinitSettings();
    }
  }

  public Editor createEditor(Document document) {
    return createEditor(document, false, null);
  }

  public Editor createViewer(Document document) {
    return createEditor(document, true, null);
  }

  public Editor createEditor(Document document, Project project) {
    return createEditor(document, false, project);
  }

  public Editor createViewer(Document document, Project project) {
    return createEditor(document, true, project);
  }

  private Editor createEditor(Document document, boolean isViewer, Project project) {
    EditorImpl editor = new EditorImpl(document, isViewer, project);
    myEditors.add(editor);
    myEditorEventMulticaster.registerEditor(editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorCreated(new EditorFactoryEvent(this, editor));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of Editor's:" + myEditors.size());
      //Thread.dumpStack();
    }

    return editor;
  }

  public void releaseEditor(Editor editor) {
    ((EditorImpl)editor).release();
    myEditors.remove(editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorReleased(new EditorFactoryEvent(this, editor));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of Editor's:" + myEditors.size());
      //Thread.dumpStack();
    }
  }

  public Editor[] getEditors(Document document, Project project) {
    ArrayList<Editor> list = new ArrayList<Editor>();
    for (int i = 0; i < myEditors.size(); i++) {
      Editor editor = myEditors.get(i);
      Project project1 = editor.getProject();
      if (editor.getDocument().equals(document) && (project == null || project1 == null || project1.equals(project))) {
        list.add(editor);
      }
    }
    return list.toArray(new Editor[list.size()]);
  }

  public Editor[] getEditors(Document document) {
    return getEditors(document, null);
  }

  public Editor[] getAllEditors() {
    return myEditors.toArray(new Editor[myEditors.size()]);
  }

  public void addEditorFactoryListener(EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.addListener(listener);
  }

  public void removeEditorFactoryListener(EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.removeListener(listener);
  }

  public EditorEventMulticaster getEventMulticaster() {
    return myEditorEventMulticaster;
  }
}
