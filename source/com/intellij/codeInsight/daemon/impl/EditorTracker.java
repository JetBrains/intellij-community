package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditorTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.EditorTracker");

  private final Project myProject;

  /**
   * @fabrique *
   */
  protected Map<Window, List<Editor>> myWindowToEditorsMap = new HashMap<Window, List<Editor>>();
  private Map<Editor, Window> myEditorToWindowMap = new HashMap<Editor, Window>();
  private static final Editor[] EMPTY_EDITOR_ARRAY = new Editor[0];
  private Editor[] myActiveEditors = EMPTY_EDITOR_ARRAY;

  private EditorFactoryListener myEditorFactoryListener;
  private EventDispatcher<EditorTrackerListener> myDispatcher = EventDispatcher.create(EditorTrackerListener.class);

  private final IdeFrame myIdeFrame;
  private Window myActiveWindow = null;
  private final WindowFocusListener myIdeFrameFocusListener = new WindowFocusListener() {
    public void windowGainedFocus(WindowEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("windowGainedFocus for IdeFrame");
      }
      setActiveWindow(myIdeFrame);
    }

    public void windowLostFocus(WindowEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("windowLostFocus for IdeFrame");
      }
      setActiveWindow(null);
    }
  };

  public EditorTracker(Project project) {
    myProject = project;

    myIdeFrame = ((WindowManagerEx)WindowManager.getInstance()).getFrame(myProject);
    FileEditorManager.getInstance(project).addFileEditorManagerListener(new FileEditorManagerAdapter() {
      public void selectionChanged(FileEditorManagerEvent event) {
        if (myIdeFrame.getFocusOwner() == null) return;
        setActiveWindow(myIdeFrame);
      }
    });
    myIdeFrame.addWindowFocusListener(myIdeFrameFocusListener);

    myEditorFactoryListener = new EditorFactoryListener() {
      public void editorCreated(EditorFactoryEvent event) {
        final Editor editor = event.getEditor();
        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
        if (psiFile == null) return;

        editor.getComponent().addHierarchyListener(new HierarchyListener() {
          public void hierarchyChanged(HierarchyEvent e) {
            registerEditor(editor);
          }
        });

        editor.getContentComponent().addFocusListener(new FocusListener() {
          public void focusGained(FocusEvent e) {
            editorFocused(editor);
          }

          public void focusLost(FocusEvent e) {
          }
        });
      }

      public void editorReleased(EditorFactoryEvent event) {
        unregisterEditor(event.getEditor());
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
  }

  /**
   * @fabrique *
   */
  protected void editorFocused(Editor editor) {
    Window window = myEditorToWindowMap.get(editor);
    if (window == null) return;

    List<Editor> list = myWindowToEditorsMap.get(window);
    int index = list.indexOf(editor);
    LOG.assertTrue(index >= 0);
    if (list.size() == 0) return;

    for (int i = index - 1; i >= 0; i--) {
      list.set(i + 1, list.get(i));
    }
    list.set(0, editor);

    setActiveWindow(window);
  }

  protected boolean isEditorInIdeFrameActive(Editor editor) {
    if (isEditorInTabbedPane(editor)){
      return isActiveEditorInTabbedPane(editor);
    }
    else{
      return true;
    }
  }

  private boolean isEditorInTabbedPane(Editor editor){
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] allEditors = editorManager.getAllEditors();
    for (int i = 0; i < allEditors.length; i++) {
      FileEditor fileEditor = allEditors[i];
      if (fileEditor instanceof TextEditor) {
        if (editor == ((TextEditor)fileEditor).getEditor()) return true;
      }
    }
    return false;
  }

  private boolean isActiveEditorInTabbedPane(Editor editor){
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    VirtualFile[] files = editorManager.getSelectedFiles();
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      FileEditor selectedEditor = editorManager.getSelectedEditor(file);
      if (selectedEditor instanceof TextEditor) {
        if (editor == ((TextEditor)selectedEditor).getEditor()) return true;
      }
    }
    return false;
  }

  /**
   * @fabrique *
   */
  protected void registerEditor(Editor editor) {
    unregisterEditor(editor);

    final Window window = windowByEditor(editor);
    if (window == null) return;

    myEditorToWindowMap.put(editor, window);
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) {
      list = new ArrayList<Editor>();
      myWindowToEditorsMap.put(window, list);

      if (!(window instanceof IdeFrame)) {
        window.addWindowFocusListener(new WindowFocusListener() {
          public void windowGainedFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowGainedFocus:" + window);
            }

            setActiveWindow(window);
          }

          public void windowLostFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowLostFocus:" + window);
            }

            setActiveWindow(null);
          }
        });
      }
    }
    list.add(editor);

    if (myActiveWindow == window) {
      setActiveWindow(window); // to fire event
    }
  }

  /**
   * @fabrique *
   */
  protected void unregisterEditor(Editor editor) {
    Window oldWindow = myEditorToWindowMap.get(editor);
    if (oldWindow != null) {
      myEditorToWindowMap.remove(editor);
      List<Editor> editorsList = myWindowToEditorsMap.get(oldWindow);
      boolean removed = editorsList.remove(editor);
      LOG.assertTrue(removed);
      if (editorsList.isEmpty()) {
        myWindowToEditorsMap.remove(oldWindow);
      }
    }
  }

  /**
   * @fabrique *
   */
  protected Window windowByEditor(Editor editor) {
    Window window = SwingUtilities.windowForComponent(editor.getComponent());
    if (window instanceof IdeFrame) {
      if (window != myIdeFrame) return null;
    }
    return window;
  }

  public void dispose() {
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    myIdeFrame.removeWindowFocusListener(myIdeFrameFocusListener);
  }

  public Editor[] getActiveEditors() {
    return myActiveEditors;
  }

  private void setActiveWindow(Window window) {
    myActiveWindow = window;

    if (myActiveWindow == null || myActiveWindow == myIdeFrame) {
      setActiveEditors(EMPTY_EDITOR_ARRAY);
    }
    else {
      Editor[] editors = editorsByWindow(myActiveWindow);
      setActiveEditors(editors);
    }
  }

  /**
   * @fabrique *
   */
  protected Editor[] editorsByWindow(Window window) {
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) return EMPTY_EDITOR_ARRAY;
    if (window instanceof IdeFrame){
      List<Editor> filteredList = new ArrayList<Editor>();
      for (int i = 0; i < list.size(); i++) {
        Editor editor = list.get(i);
        if (isEditorInIdeFrameActive(editor)) {
          filteredList.add(editor);
        }
      }
      list = filteredList;
    }
    return list.toArray(new Editor[list.size()]);
  }

  /**
   * @fabrique *
   */
  protected void setActiveEditors(Editor[] editors) {
    myActiveEditors = editors;

    if (LOG.isDebugEnabled()) {
      LOG.debug("active editors changed:");
      if (editors.length > 0) {
        for (int i = 0; i < editors.length; i++) {
          Editor editor = editors[i];
          PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
          LOG.debug("    " + psiFile);
        }
      }
      else {
        LOG.debug("    <none>");
      }
    }

    myDispatcher.getMulticaster().activeEditorsChanged();
  }

  public void addEditorTrackerListener(EditorTrackerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeEditorTrackerListener(EditorTrackerListener listener) {
    myDispatcher.removeListener(listener);
  }
}
