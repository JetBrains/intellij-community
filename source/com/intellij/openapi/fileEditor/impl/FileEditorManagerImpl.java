package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class FileEditorManagerImpl extends FileEditorManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl");

  private static final FileEditor[] EMPTY_EDITOR_ARRAY = new FileEditor[]{};
  private static final FileEditorProvider[] EMPTY_PROVIDER_ARRAY = new FileEditorProvider[]{};

  static final Icon LOCKED_ICON = IconLoader.getIcon("/nodes/locked.png");
  static final Icon MODIFIED_ICON = IconLoader.getIcon("/general/modified.png");

  static final Icon GAP_ICON = EmptyIcon.create(MODIFIED_ICON.getIconWidth(),
                                                        MODIFIED_ICON.getIconHeight());
  private final JPanel myPanels;
  public Project myProject;

  private final EventListenerList myListenerList;

  /**
   * Updates tabs colors
   */
  private final MyFileStatusListener myFileStatusListener;
  /**
   * Updates tabs icons
   */
  private final MyFileTypeListener myFileTypeListener;
  /**
   * Removes invalid myEditor and updates "modified" status.
   */
  protected final MyEditorPropertyChangeListener myEditorPropertyChangeListener;
  /**
   * Updates tabs names
   */
  private final MyVirtualFileListener myVirtualFileListener;
  /**
   * Extends/cuts number of opened tabs. Also updates location of tabs.
   */
  private final MyUISettingsListener myUISettingsListener;

  /**
   * Push forward events from composite
   */
  protected final MyEditorManagerListener myEditorManagerListener;
  /**
   * Updates icons for open files when project roots change
   */
  private final MyPsiTreeChangeListener myPsiTreeChangeListener;
  final private EditorsSplitters mySplitters;


  FileEditorManagerImpl(final Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myListenerList = new EventListenerList();
    myProject = project;
    myPanels = new JPanel(new BorderLayout());
    mySplitters = new EditorsSplitters(this);
    myPanels.add(mySplitters, BorderLayout.CENTER);

    myFileStatusListener = new MyFileStatusListener();
    myFileTypeListener = new MyFileTypeListener();
    myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();
    myVirtualFileListener = new MyVirtualFileListener();
    myUISettingsListener = new MyUISettingsListener();
    myEditorManagerListener = new MyEditorManagerListener();
    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
  }

  //-------------------------------------------------------------------------------

  public JComponent getComponent() {
    return myPanels;
  }

  public JComponent getPreferredFocusedComponent() {
    assertThread();
    final EditorWindow window = getSplitters().getCurrentWindow();
    if (window != null) {
      final EditorWithProviderComposite editor = window.getSelectedEditor();
      if (editor != null) {
        return editor.getPreferredFocusedComponent();
      }
    }
    return null;
  }

  //-------------------------------------------------------

  /**
   * @return color of the <code>file</code> which corresponds to the
   *         file's status
   */
  protected Color getFileColor(final VirtualFile file) {
    LOG.assertTrue(file != null);
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      return fileStatusManager.getStatus(file).getColor();
    }
    return Color.BLACK;
  }


  /**
   * Updates tab color for the specified <code>file</code>. The <code>file</code>
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  protected void updateFileColor(final VirtualFile file) {
    getSplitters ().updateFileColor(file);
  }


  /**
   * Updates tab icon for the specified <code>file</code>. The <code>file</code>
   * should be opened in the myEditor, otherwise the method throws an assertion.
   */
  private void updateFileIcon(final VirtualFile file, boolean useAlarm) {
    getSplitters ().updateFileIcon(file, useAlarm);
  }

  /**
   * Updates tab title and tab tool tip for the specified <code>file</code>
   */
  protected void updateFileName(final VirtualFile file) {
    final WindowManagerEx windowManagerEx = WindowManagerEx.getInstanceEx();
    final IdeFrame frame = windowManagerEx.getFrame(myProject);
    LOG.assertTrue(frame != null);
    mySplitters.updateFileName (file);
    frame.setFileTitle(file);
  }

  //-------------------------------------------------------


  public VirtualFile getFile(final FileEditor editor) {
    final EditorComposite editorComposite = getEditorComposite(editor);
    if (editorComposite != null) {
      return editorComposite.getFile();
    }
    return null;
  }

  public boolean hasSplitters() {
    return true; //false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void unsplitWindow() {
    final EditorWindow currentWindow = getSplitters ().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplit();
    }
  }

  public void unsplitAllWindow() {
    final EditorWindow currentWindow = getSplitters ().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.unsplitAll();
    }
  }

  public EditorWindow [] getWindows() {
    return mySplitters.getWindows();
  }

  public EditorWindow getNextWindow(final EditorWindow window) {
    final EditorWindow[] windows = getSplitters().getOrderedWindows();
    for (int i = 0; i != windows.length; ++ i)
      if (windows[i].equals (window))
        return windows [(i+1)%windows.length];
    LOG.error("Not window found");
    return null;
  }

  public EditorWindow getPrevWindow(final EditorWindow window) {
    final EditorWindow[] windows = getSplitters().getOrderedWindows();
    for (int i = 0; i != windows.length; ++ i)
      if (windows[i].equals (window))
        return windows [(i+windows.length-1)%windows.length];
    LOG.error("Not window found");
    return null;
  }

  public void moveFocusToNextEditor() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void createSplitter(final int orientation) {
    final EditorWindow currentWindow = getSplitters ().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.split(orientation);
    }
  }

  public void changeSplitterOrientation() {
    final EditorWindow currentWindow = getSplitters ().getCurrentWindow();
    if (currentWindow != null) {
      currentWindow.changeOrientation();
    }
  }


  public void revalidate() {
    myPanels.repaint();
  }

  public void flipTabs() {
    /*
    if (myTabs == null) {
      myTabs = new EditorTabs (this, UISettings.getInstance().EDITOR_TAB_PLACEMENT);
      remove (mySplitters);
      add (myTabs, BorderLayout.CENTER);
      initTabs ();
    } else {
      remove (myTabs);
      add (mySplitters, BorderLayout.CENTER);
      myTabs.dispose ();
      myTabs = null;
    }
    */
    myPanels.revalidate();
  }

  public boolean tabsMode() {
    return false;
  }

  public void setTabsMode(final boolean mode) {
    if (tabsMode() != mode) {
      flipTabs();
    }
    //LOG.assertTrue (tabsMode () == mode);
  }


  public boolean isInSplitter() {
    final EditorWindow currentWindow = getSplitters ().getCurrentWindow();
    if (currentWindow != null) {
      return currentWindow.inSplitter();
    }
    return false;
  }

  public boolean hasOpenedFile() {
    final EditorWindow currentWindow = getSplitters ().getCurrentWindow();
    return currentWindow != null && currentWindow.getSelectedEditor() != null;
  }

  public VirtualFile getCurrentFile() {
    return getSplitters().getCurrentFile();
  }

  public EditorWindow getCurrentWindow() {
    return getSplitters().getCurrentWindow();
  }

  public void setCurrentWindow(final EditorWindow window) {
    getSplitters().setCurrentWindow(window, true);
  }

  //============================= EditorManager methods ================================
  public void closeFile(final VirtualFile file) {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    assertThread();

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        closeFileImpl(file);
      }
    },
                                                  "",
                                                  null);
  }


  private VirtualFile findNextFile(final VirtualFile file) {
    final EditorWindow [] windows = getWindows(); // TODO: use current file as base
    for (int i = 0; i != windows.length; ++ i) {
      final VirtualFile[] files = windows [i].getFiles();
      for (int j = 0; j < files.length; j++) {
        final VirtualFile fileAt = files[j];
        if (fileAt != file) {
          return fileAt;
        }
      }
    }
    return null;
  }

  public void closeFileImpl(final VirtualFile file) {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    FileEditorManagerImpl.assertThread();
    ++getSplitters().myInsideChange;
    try {
      final EditorWindow[] windows = getSplitters().findWindows(file);
      if (windows != null) {
        final VirtualFile nextFile = findNextFile(file);
        //if (nextFile == null) {
        //  getSplitters().clear();
        //}
        for (int i = 0; i < windows.length; i++) {
          final EditorWindow window = windows[i];
          LOG.assertTrue(window.getSelectedEditor() != null);
          final EditorWithProviderComposite oldComposite = window.findFileComposite(file);
          if (oldComposite != null) {
            window.removeEditor(oldComposite);
          }
          if (nextFile != null) {
            if (window.getTabCount() == 0) {
              EditorWithProviderComposite newComposite = window.findFileComposite(nextFile);
              if (newComposite == null) {
                newComposite = newEditorComposite(nextFile, true, null);
              }
              window.setEditor(newComposite); // newComposite can be null
            }
          }
        }
      }
      fireFileClosed(file);
    }
    finally {
      --getSplitters().myInsideChange;
    }
  }

  private EditorsSplitters getSplitters() {
    return mySplitters;
  }

//-------------------------------------- Open File ----------------------------------------

  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(final VirtualFile file, final boolean focusEditor) {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    if (!file.isValid()) {
      throw new IllegalArgumentException("file is not valid: " + file);
    }
    assertThread();

    return openFileImpl(file, focusEditor, null);
  }

  public Pair<FileEditor[], FileEditorProvider[]> openFileImpl(final VirtualFile file,
                                                               final boolean focusEditor,
                                                               final HistoryEntry entry) {
    return openFileImpl2(getSplitters().getOrCreateCurrentWindow(), file, focusEditor, entry);
  }

  public Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(final EditorWindow window,
                                                                final VirtualFile file,
                                                                final boolean focusEditor,
                                                                final HistoryEntry entry) {
    final Ref<Pair<FileEditor[], FileEditorProvider[]>> resHolder = new Ref<Pair<FileEditor[], FileEditorProvider[]>>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        resHolder.set(openFileImpl3(window, file, focusEditor, entry));
      }
    },
                                                  "",
                                                  null);
    return resHolder.get();
  }

  /**
   * @param file  to be opened. Unlike openFile method, file can be
   *              invalid. For example, all file were invalidate and they are being
   *              removed one by one. If we have removed one invalid file, then another
   *              invalid file become selected. That's why we are not require that
   *              passed file is valid.
   * @param entry map between FileEditorProvider and FileEditorState. If this parameter
   *              is not <code>null</code> then it's used to restore state for the newly created
   */
  static protected Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(final EditorWindow window,
                                                                          final VirtualFile file,
                                                                          final boolean focusEditor,
                                                                          final HistoryEntry entry) {
    LOG.assertTrue(file != null);

    // Open file
    window.myInsideTabChange++;
    FileEditor[] editors;
    FileEditorProvider[] providers;
    try {
      final EditorWithProviderComposite newSelectedComposite;
      boolean newEditorCreated = false;

      final boolean open = window.isFileOpen(file);
      if (open) {
        LOG.assertTrue(window.findFileComposite(file) != null);
        // File is already opened. In this case we have to just select existing EditorComposite
        newSelectedComposite = window.findFileComposite(file);
        LOG.assertTrue(newSelectedComposite != null);

        editors = newSelectedComposite.getEditors();
        providers = newSelectedComposite.getProviders();
        window.setEditor(newSelectedComposite);
      }
      else {
        // File is not opened yet. In this case we have to create editors
        // and select the created EditorComposite.
        final FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
        providers = editorProviderManager.getProviders(window.getManager().myProject, file);

        if (providers.length == 0) {
          return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
        }
        newEditorCreated = true;

        editors = new FileEditor[providers.length];
        for (int i = 0; i < providers.length; i++) {
          final FileEditorProvider provider = providers[i];
          LOG.assertTrue(provider != null);
          LOG.assertTrue(provider.accept(window.getManager().myProject, file));
          final FileEditor editor = provider.createEditor(window.getManager().myProject, file);
          editors[i] = editor;
          LOG.assertTrue(editor != null);
          LOG.assertTrue(editor.isValid());

          // Register PropertyChangeListener into editor
          editor.addPropertyChangeListener(window.getManager().myEditorPropertyChangeListener);
        }

        // Now we have to create EditorComposite and insert it into the TabbedEditorComponent.
        // After that we have to select opened editor.
        newSelectedComposite = new EditorWithProviderComposite(file, editors, providers, window.getManager());
        newSelectedComposite.addEditorManagerListener(window.getManager().myEditorManagerListener);
        window.setEditor(newSelectedComposite);
      }

      final EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(window.getManager().myProject);
      for (int i = 0; i < editors.length; i++) {
        final FileEditor editor = editors[i];
        if (editor instanceof TextEditor) {
          // hack!!!
          // This code prevents "jumping" on next repaint.
          ((EditorEx)((TextEditor)editor).getEditor()).stopOptimizedScrolling();
        }

        final FileEditorProvider provider = providers[i];//getProvider(editor);

        // Restore editor state
        FileEditorState state = null;
        if (entry != null) {
          state = entry.getState(provider);
        }
        if (state == null && !open) {
          // We have to try to get state from the history only in case
          // if editor is not opened. Otherwise history enty might have a state
          // no in sych with the current editor state.
          state = editorHistoryManager.getState(file, provider);
        }
        if (state != null) {
          editor.setState(state);
        }
      }

      // Restore selected editor
      final FileEditorProvider selectedProvider = editorHistoryManager.getSelectedProvider(file);
      if (selectedProvider != null) {
        final FileEditor[] _editors = newSelectedComposite.getEditors();
        final FileEditorProvider[] _providers = newSelectedComposite.getProviders();
        for (int i = _editors.length - 1; i >= 0; i--) {
          final FileEditorProvider provider = _providers[i];//getProvider(_editors[i]);
          if (provider.equals(selectedProvider)) {
            newSelectedComposite.setSelectedEditor(i);
            break;
          }
        }
      }

      // Notify editors about selection changes

      final EditorComposite oldSelectedComposite = window.getManager ().getLastSelected ();
      if (oldSelectedComposite != null) {
        oldSelectedComposite.getSelectedEditor().deselectNotify();
      }
      window.getManager ().mySplitters.setCurrentWindow (window, false);
      newSelectedComposite.getSelectedEditor().selectNotify();

      if (newEditorCreated) {
        window.getManager().fireFileOpened(file);
      }

      // Notify listeners about selection changes
      window.getManager().fireSelectionChanged(oldSelectedComposite, newSelectedComposite);

      // Transfer focus into editor
      if (!ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
        if (focusEditor || ToolWindowManager.getInstance(window.getManager().myProject).isEditorComponentActive()) {
          //myFirstIsActive = myTabbedContainer1.equals(tabbedContainer);
          window.setAsCurrentWindow(false);
          ToolWindowManager.getInstance(window.getManager().myProject).activateEditorComponent();
        }
      }

      // Update frame and tab title
      window.getManager().updateFileName(file);

      // Make back/forward work
      IdeDocumentHistory.getInstance(window.getManager().myProject).includeCurrentCommandAsNavigation();
    }
    finally {
      window.myInsideTabChange--;
    }

    return Pair.create(editors, providers);
  }

  private EditorWithProviderComposite newEditorComposite(final VirtualFile file,
                                                         final boolean focusEditor,
                                                         final HistoryEntry entry) {
    if (file == null) return null;

    final FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    final FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
    final FileEditor[] editors = new FileEditor[providers.length];
    for (int i = 0; i < providers.length; i++) {
      final FileEditorProvider provider = providers[i];
      LOG.assertTrue(provider != null);
      LOG.assertTrue(provider.accept(myProject, file));
      final FileEditor editor = provider.createEditor(myProject, file);
      editors[i] = editor;
      LOG.assertTrue(editor != null);
      LOG.assertTrue(editor.isValid());
      editor.addPropertyChangeListener(myEditorPropertyChangeListener);
    }

    final EditorWithProviderComposite newComposite = new EditorWithProviderComposite(file, editors, providers, this);
    final EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(myProject);
    for (int i = 0; i < editors.length; i++) {
      final FileEditor editor = editors[i];
      if (editor instanceof TextEditor) {
        // hack!!!
        // This code prevents "jumping" on next repaint.
        //((EditorEx)((TextEditor)editor).getEditor()).stopOptimizedScrolling();
      }

      final FileEditorProvider provider = providers[i];

      // Restore myEditor state
      FileEditorState state = null;
      if (entry != null) {
        state = entry.getState(provider);
      }
      if (state == null/* && !open*/) {
        // We have to try to get state from the history only in case
        // if myEditor is not opened. Otherwise history enty might have a state
        // no in sych with the current myEditor state.
        state = editorHistoryManager.getState(file, provider);
      }
      if (state != null) {
        editor.setState(state);
      }
    }
    return newComposite;
  }

  public Editor openTextEditor(final OpenFileDescriptor descriptor, final boolean focusEditor) {
    if (descriptor == null) {
      throw new IllegalArgumentException("descriptor cannot be null");
    }
    assertThread();

    final Editor[] result = new Editor[1];
    CommandProcessor.getInstance().executeCommand(myProject,
                                                  new Runnable() {
                                                    public void run() {
                                                      final FileEditor[] editors = openFile(descriptor.getFile(), focusEditor);
                                                      for (int i = 0; i < editors.length; i++) {
                                                        final FileEditor editor = editors[i];
                                                        if (!(editor instanceof TextEditor)) {
                                                          continue;
                                                        }

                                                        final Editor _editor = ((TextEditor)editor).getEditor();
                                                        result[0] = _editor;

                                                        // Move myEditor caret to the specified location.
                                                        if (descriptor.getOffset() >= 0) {
                                                          _editor.getCaretModel().moveToOffset(
                                                            Math.min(descriptor.getOffset(), _editor.getDocument().getTextLength()));
                                                          _editor.getSelectionModel().removeSelection();
                                                          _editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                                                        }
                                                        else if (descriptor.getLine() != -1 && descriptor.getColumn() != -1) {
                                                          final LogicalPosition pos = new LogicalPosition(descriptor.getLine(), descriptor.getColumn());
                                                          _editor.getCaretModel().moveToLogicalPosition(pos);
                                                          _editor.getSelectionModel().removeSelection();
                                                          _editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                                                        }

                                                        break;
                                                      }
                                                    }
                                                  },
                                                  "",
                                                  null);

    return result[0];
  }

  public Editor getSelectedTextEditor() {
    assertThread();
    final VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles.length == 0) {
      return null;
    }

    final FileEditor editor = getSelectedEditor(selectedFiles[0]);
    if (editor instanceof TextEditor) {
      return ((TextEditor)editor).getEditor();
    }
    else {
      return null;
    }
  }


  public boolean isFileOpen(final VirtualFile file) {
    return getEditors(file).length != 0;
  }

  public VirtualFile[] getOpenFiles() {
    return getSplitters ().getOpenFiles();
  }

  public VirtualFile[] getSelectedFiles() {
    return getSplitters().getSelectedFiles();
  }

  public FileEditor[] getSelectedEditors() {
    return getSplitters().getSelectedEditors();
  }

  public FileEditor getSelectedEditor(final VirtualFile file) {
    return getSelectedEditorWithProvider(file).getFirst();
  }


  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(final VirtualFile file) {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }

    final EditorWithProviderComposite[] composites = getEditorComposites(file);
    if (composites != null) {
      return composites[0].getSelectedEditorWithProvider();
    }
    else {
      return null;
    }
  }

  public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(final VirtualFile file) {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    assertThread();

    final EditorWithProviderComposite[] composites = getEditorComposites(file);
    if (composites != null) {
      return Pair.create(composites[0].getEditors(), composites[0].getProviders());
    }
    else {
      return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
    }
  }

  public FileEditor[] getEditors(final VirtualFile file) {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    assertThread();

    final EditorComposite[] composites = getEditorComposites(file);
    if (composites != null) {
      return composites[0].getEditors();
    }
    else {
      return EMPTY_EDITOR_ARRAY;
    }
  }

  public EditorWithProviderComposite[] getEditorComposites(final VirtualFile file) {
    return getSplitters().findEditorComposites(file);
  }

  public FileEditor[] getAllEditors() {
    assertThread();
    final ArrayList<FileEditor> result = new ArrayList<FileEditor>();
    final EditorWithProviderComposite[] editorsComposites = getSplitters().getEditorsComposites();
    for (int i = 0; i < editorsComposites.length; i++) {
      final FileEditor[] editors = editorsComposites[i].getEditors();
      for (int j = 0; j < editors.length; j++) {
        result.add(editors[j]);
      }
    }
    return result.toArray(new FileEditor[result.size()]);
  }

  public void addFileEditorManagerListener(final FileEditorManagerListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
    assertThread();
    myListenerList.add(FileEditorManagerListener.class, listener);
  }

  public void removeFileEditorManagerListener(final FileEditorManagerListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
    assertThread();
    myListenerList.remove(FileEditorManagerListener.class, listener);
  }

  // ProjectComponent methods
  public void projectOpened() {
    //myFocusWatcher.install(myWindows.getComponent ());

    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      fileStatusManager.addFileStatusListener(myFileStatusListener);
    }
    FileTypeManager.getInstance().addFileTypeListener(myFileTypeListener);
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
    UISettings.getInstance().addUISettingsListener(myUISettingsListener);
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(myProject,
                                                          new Runnable() {
                                                            public void run() {
                                                              if (myProject.isDisposed()) return;
                                                              setTabsMode(UISettings.getInstance().EDITOR_TAB_PLACEMENT != UISettings.TABS_NONE);
                                                              getSplitters().openFiles();
                                                              // group 1
                                                            }
                                                          },
                                                          "",
                                                          null);
          }
        });
      }
    });

  }

  public void projectClosed() {
    //myFocusWatcher.deinstall(myWindows.getComponent ());
    // Remove application level listeners
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      fileStatusManager.removeFileStatusListener(myFileStatusListener);
    }
    FileTypeManager.getInstance().removeFileTypeListener(myFileTypeListener);
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);

    // Dispose created editors. We do not use use closeEditor method because
    // it fires event and changes history.
    closeAllFiles();
  }


  // BaseCompomemnt methods
  public String getComponentName() {
    return "FileEditorManager";
  }

  public void initComponent() { /* really do nothing */ }

  public void disposeComponent() { /* really do nothing */  }

  //JDOMExternalizable methods
  public void writeExternal(final Element element) {
    getSplitters().writeExternal(element);
  }

  public void readExternal(final Element element) {
    getSplitters().readExternal(element);
  }

  private EditorComposite getEditorComposite(final FileEditor editor) {
    LOG.assertTrue(editor != null);
    final EditorWithProviderComposite[] editorsComposites = getSplitters().getEditorsComposites();
    for (int i = editorsComposites.length - 1; i >= 0; i--) {
      final EditorComposite composite = editorsComposites[i];
      final FileEditor[] editors = composite.getEditors();
      for (int j = editors.length - 1; j >= 0; j--) {
        final FileEditor _editor = editors[j];
        LOG.assertTrue(_editor != null);
        if (editor.equals(_editor)) {
          return composite;
        }
      }
    }
    return null;
  }


  //======================= Misc =====================
  public static void assertThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  //================== Firing events =====================
  private void fireFileOpened(final VirtualFile file) {
    final FileEditorManagerListener[] listeners = (FileEditorManagerListener[])myListenerList.getListeners(FileEditorManagerListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].fileOpened(this, file);
    }
  }

  public void fireFileClosed(final VirtualFile file) {
    final FileEditorManagerListener[] listeners = (FileEditorManagerListener[])myListenerList.getListeners(FileEditorManagerListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].fileClosed(this, file);
    }
  }

  public void fireSelectionChanged(final EditorComposite oldSelectedComposite,
                                   final EditorComposite newSelectedComposite) {
    final VirtualFile oldSelectedFile = oldSelectedComposite != null ? oldSelectedComposite.getFile() : null;
    final FileEditor oldSelectedEditor = oldSelectedComposite != null ? oldSelectedComposite.getSelectedEditor() : null;

    final VirtualFile newSelectedFile = newSelectedComposite != null ? newSelectedComposite.getFile() : null;
    final FileEditor newSelectedEditor = newSelectedComposite != null ? newSelectedComposite.getSelectedEditor() : null;

    final FileEditorManagerEvent event = new FileEditorManagerEvent(this, oldSelectedFile, oldSelectedEditor, newSelectedFile, newSelectedEditor);
    final FileEditorManagerListener[] listeners = (FileEditorManagerListener[])myListenerList.getListeners(FileEditorManagerListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].selectionChanged(event);
    }
  }

  private void fireSelectionChanged(final VirtualFile selectedFile,
                                    final FileEditor oldSelectedEditor,
                                    final FileEditor newSelectedEditor) {
    LOG.assertTrue(selectedFile != null);
    if (!Comparing.equal(oldSelectedEditor, newSelectedEditor)) {
      final FileEditorManagerEvent event = new FileEditorManagerEvent(this, selectedFile, oldSelectedEditor, selectedFile, newSelectedEditor);
      final FileEditorManagerListener[] listeners = (FileEditorManagerListener[])myListenerList.getListeners(FileEditorManagerListener.class);
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].selectionChanged(event);
      }
    }
  }

  // true if only one editor per file allowed
  public boolean isSingleFileMode() {
    return false;
  }

  public boolean isChanged (final EditorComposite editor) {
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager != null) {
      if (!fileStatusManager.getStatus(editor.getFile()).equals (FileStatus.NOT_CHANGED)) {
        return true;
      }
    }
    return false;
  }

  public void disposeComposite(EditorWithProviderComposite editor) {
    if(editor.equals(getLastSelected ())){
      editor.getSelectedEditor().deselectNotify();
      mySplitters.setCurrentWindow (null, false);
      fireSelectionChanged(editor, null); // oldSelectedComposite -> null
    }

    editor.removeEditorManagerListener(myEditorManagerListener);

    final FileEditor[] editors = editor.getEditors();
    final FileEditorProvider[] providers = editor.getProviders();

    final FileEditor selectedEditor = editor.getSelectedEditor();
    for (int i = editors.length - 1; i >= 0; i--) {
      final FileEditor editor1 = editors[i];
      final FileEditorProvider provider = providers[i];
      if (!editor.equals(selectedEditor)) { // we already notified the myEditor (when fire event)
        if (selectedEditor.equals(editor1)) {
          editor1.deselectNotify();
        }
      }
      editor1.removePropertyChangeListener(myEditorPropertyChangeListener);
      provider.disposeEditor(editor1);
    }
  }

  protected EditorComposite getLastSelected() {
    final EditorWindow currentWindow = mySplitters.getCurrentWindow ();
    if (currentWindow != null) {
      return currentWindow.getSelectedEditor();
    }
    return null;
  }

  //================== Listeners =====================
  /**
   * Closes deleted files. Closes file which are in the deleted directories.
   */
  private final class MyVirtualFileListener extends VirtualFileAdapter{
    public void beforeFileDeletion(VirtualFileEvent e){
      assertThread();
      final VirtualFile file = e.getFile();
      final VirtualFile[] openFiles = getOpenFiles();
      for(int i = openFiles.length - 1; i >= 0; i--){
        if(VfsUtil.isAncestor(file, openFiles[i], false)){
          closeFile(openFiles[i]);
        }
      }
    }

    public void propertyChanged(VirtualFilePropertyEvent e){
      if(VirtualFile.PROP_NAME.equals(e.getPropertyName())){
        assertThread();
        final VirtualFile file = e.getFile();
        if(isFileOpen(file)){
          updateFileName(file);
          updateFileIcon(file, false); // file type can change after renaming
        }
      }
      else if(VirtualFile.PROP_WRITABLE.equals(e.getPropertyName())){
        assertThread();
        final VirtualFile file = e.getFile();
        if(isFileOpen(file)){
          updateFileIcon(file, false);
          if(file.equals(getSelectedFiles()[0])){ // update "write" status
            final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
            LOG.assertTrue(statusBar != null);
            statusBar.setWriteStatus(!file.isWritable());
          }
        }
      }
    }

    public void fileMoved(VirtualFileMoveEvent e){
      final VirtualFile file = e.getFile();
      final VirtualFile[] selectedFiles = getSelectedFiles();
      for (int i = 0; i < selectedFiles.length; i++) {
        final VirtualFile selectedFile = selectedFiles[i];
        if(VfsUtil.isAncestor(file, selectedFile, false)){
          updateFileName(selectedFile);
        }
      }
    }
  }

  /*
  private final class MyVirtualFileListener extends VirtualFileAdapter {
    public void beforeFileDeletion(final VirtualFileEvent e) {
      assertThread();
      final VirtualFile file = e.getFile();
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        if (VfsUtil.isAncestor(file, openFiles[i], false)) {
          closeFile(openFiles[i]);
        }
      }
    }

    public void propertyChanged(final VirtualFilePropertyEvent e) {
      if (VirtualFile.PROP_WRITABLE.equals(e.getPropertyName())) {
        assertThread();
        final VirtualFile file = e.getFile();
        if (isFileOpen(file)) {
          if (file.equals(getSelectedFiles()[0])) { // update "write" status
            final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
            LOG.assertTrue(statusBar != null);
            statusBar.setWriteStatus(!file.isWritable());
          }
        }
      }
    }

    //public void fileMoved(final VirtualFileMoveEvent e){ }
  }
  */

  private final class MyEditorManagerListener extends FileEditorManagerAdapter {
    public void selectionChanged(final FileEditorManagerEvent event) {
      final VirtualFile oldSelectedFile = event.getOldFile();
      final VirtualFile newSelectedFile = event.getNewFile();
      LOG.assertTrue(oldSelectedFile != null);
      LOG.assertTrue(oldSelectedFile.equals(newSelectedFile));
      if (getSplitters().myInsideChange > 0) { // do not react on own events
        return;
      }
      fireSelectionChanged(oldSelectedFile, event.getOldEditor(), event.getNewEditor());
    }
  }

  private final class MyEditorPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(final PropertyChangeEvent e) {
      assertThread();

      final String propertyName = e.getPropertyName();
      if (FileEditor.PROP_MODIFIED.equals(propertyName)) {
        final FileEditor editor = (FileEditor)e.getSource();
        final EditorComposite composite = getEditorComposite(editor);
        if (composite != null) {
          updateFileIcon(composite.getFile(), false);
        }
      }
      else if (FileEditor.PROP_VALID.equals(propertyName)) {
        final boolean valid = ((Boolean)e.getNewValue()).booleanValue();
        if (!valid) {
          final FileEditor editor = (FileEditor)e.getSource();
          LOG.assertTrue(editor != null);
          final EditorComposite composite = getEditorComposite(editor);
          LOG.assertTrue(composite != null);
          closeFile(composite.getFile());
        }
      }

    }
  }

  /**
   * Gets events from VCS and updates color of myEditor tabs
   */
  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() { // update color of all open files
      assertThread();
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        ApplicationManager.getApplication().invokeLater(new Runnable () {
          public void run() {
            updateFileStatus(file);
          }
        }, ModalityState.NON_MMODAL);
      }
    }

    public void fileStatusChanged(final VirtualFile file) { // update color of the file (if necessary)
      assertThread();
      if (file == null) {
        throw new IllegalArgumentException("file cannot be null");
      }
      if (isFileOpen(file)) {
        updateFileStatus(file);
      }
    }

    private void updateFileStatus(final VirtualFile file) {
      updateFileColor(file);
      updateFileIcon(file, false);
    }
  }

  /**
   * Gets events from FileTypeManager and updates icons on tabs
   */
  private final class MyFileTypeListener implements FileTypeListener {
    public void beforeFileTypesChanged(FileTypeEvent event) {
    }

    public void fileTypesChanged(final FileTypeEvent event) {
      assertThread();
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        updateFileIcon(file, false);
      }
    }
  }

  /**
   * Gets notifications from UISetting component to track changes of RECENT_FILES_LIMIT
   * and EDITOR_TAB_LIMIT, etc values.
   */
  private final class MyUISettingsListener implements UISettingsListener {
    public void uiSettingsChanged(final UISettings source) {
      assertThread();
      setTabsMode(source.EDITOR_TAB_PLACEMENT != UISettings.TABS_NONE);
      getSplitters().setTabsPlacement(source.EDITOR_TAB_PLACEMENT);
      getSplitters().trimToSize(source.EDITOR_TAB_LIMIT, null);

      // Tab layout policy
      if (source.SCROLL_TAB_LAYOUT_IN_EDITOR) {
        getSplitters().setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
      } else {
        getSplitters().setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
      }
      
      // "Mark modified files with asterisk"
      final VirtualFile[] openFiles = getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        updateFileIcon(file, false);
        updateFileName(file);
      }
    }
  }

  /**
   * Updates attribute of open files when roots change
   */
  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void propertyChanged(final PsiTreeChangeEvent e) {
      if (PsiTreeChangeEvent.PROP_ROOTS.equals(e.getPropertyName())) {
        assertThread();
        final VirtualFile[] openFiles = getOpenFiles();
        for (int i = openFiles.length - 1; i >= 0; i--) {
          final VirtualFile file = openFiles[i];
          LOG.assertTrue(file != null);
          updateFileIcon(file, false);
        }
      }
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      // use alarm to avoid constant icon update on typing
      final VirtualFile currentFile = getCurrentFile();
      if (currentFile != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable () {
          public void run() {
            updateFileIcon(currentFile, true);
          }
        }, ModalityState.NON_MMODAL);
      }
    }
  }


  //===================

  /**
   * @return file at the specified index in the tabbed container. Never <code>null</code>.
   */
  VirtualFile getFileAt(final EditorTabbedContainer tabbedContainer, final int tabIndex) {
    //final JComponent componentAt = tabbedContainer.getComponentAt(tabIndex);
    //OpenFile openFile = findOpenFile(componentAt);
    //return openFile.myFile;
    return null;
  }

  public void closeAllFiles() {
    final VirtualFile[] openFiles = getSplitters().getOpenFiles();
    for (int i = 0; i < openFiles.length; i++) {
      closeFile(openFiles[i]);
    }
    //getSplitters().clear();
  }

  public VirtualFile[] getSiblings(VirtualFile file) {
    return getOpenFiles();
  }

}
