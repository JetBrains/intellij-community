package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class EditorHistoryManager implements ProjectComponent, JDOMExternalizable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorHistoryManager");
  private Element myElement;

  public static EditorHistoryManager getInstance(final Project project){
    return project.getComponent(EditorHistoryManager.class);
  }

  private final Project myProject;
  /**
   * State corresponding to the most recent file is the last
   */
  private final ArrayList<HistoryEntry> myEntriesList;
  /**
   * Listen opening of myEditor to update history
   */
  private final MyEditorManagerListener myEditorManagerListener;
  /**
   * Updates history length
   */
  private final MyUISettingsListener myUISettingsListener;

  /** Invoked by reflection */
  EditorHistoryManager(final Project project, FileEditorManager fileEditorManager, UISettings uiSettings){
    myProject = project;
    myEntriesList = new ArrayList<HistoryEntry>();
    myEditorManagerListener = new MyEditorManagerListener();
    myUISettingsListener = new MyUISettingsListener();

    fileEditorManager.addFileEditorManagerListener(myEditorManagerListener);
    uiSettings.addUISettingsListener(myUISettingsListener);
  }

  public void projectOpened(){
    StartupManager.getInstance(myProject).registerPostStartupActivity(
      new Runnable(){
        public void run(){
          // myElement may be null if node that correspondes to this manager does not exist
          if (myElement != null){
            final List children = myElement.getChildren(HistoryEntry.TAG);
            myElement = null;
            //noinspection unchecked
            for (final Element e : ((Iterable<Element>)children)) {
              try {
                myEntriesList.add(new HistoryEntry(myProject, e));
              }
              catch (InvalidDataException e1) {
              }
            }
            trimToSize();
          }
        }
      }
    );
  }

  public void projectClosed(){}

  @NotNull
  public String getComponentName(){
    return "editorHistoryManager";
  }

  public void initComponent() { }

  public void disposeComponent(){
    FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myEditorManagerListener);
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);
  }

  /**
   * Makes file most recent one
   */
  private void fileOpenedImpl(final VirtualFile file){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();

    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(myProject);

    final Pair<FileEditor[], FileEditorProvider[]> editorsWithProviders = editorManager.getEditorsWithProviders(file);
    final FileEditor         []      editors = editorsWithProviders.getFirst();
    final FileEditorProvider [] oldProviders = editorsWithProviders.getSecond();
    if (editors.length <= 0) {
      LOG.assertTrue(false, "No editors for file " + file.getPresentableUrl());
    }
    final FileEditor selectedEditor = editorManager.getSelectedEditor(file);
    LOG.assertTrue(selectedEditor != null);
    final int selectedProviderIndex = ArrayUtil.find(editors, selectedEditor);
    LOG.assertTrue(selectedProviderIndex != -1);

    final HistoryEntry entry = getEntry(file);
    if(entry != null){
      myEntriesList.remove(entry);
      myEntriesList.add(entry);
    }else{
      final FileEditorState[] states=new FileEditorState[editors.length];
      final FileEditorProvider[] providers=new FileEditorProvider[editors.length];
      for (int i = states.length - 1; i >= 0; i--) {
        //final FileEditorProvider provider = FileEditorManagerEx.getInstanceEx(myProject).getProvider(editors[i]);
        final FileEditorProvider provider = oldProviders [i];
        LOG.assertTrue(provider != null);
        providers[i] = provider;
        states[i] = editors[i].getState(FileEditorStateLevel.FULL);
      }
      myEntriesList.add(new HistoryEntry(file, providers, states, providers[selectedProviderIndex]));
      trimToSize();
    }
  }

  private void updateHistoryEntry(final VirtualFile file, final boolean changeEntryOrderOnly){
    if (file == null){
      return;
    }
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(myProject);
    final Pair<FileEditor[], FileEditorProvider[]> editorsWithProviders = editorManager.getEditorsWithProviders(file);
    final FileEditor[]   editors = editorsWithProviders.getFirst();
    if (editors.length == 0) {
      // obviously not opened in any editor at the moment,
      // makes no sense to put the file in the history
      return;
    }
    final HistoryEntry entry = getEntry(file);
    if(entry == null){
      // Size of entry list can be less than number of opened editors (some entries can be removed)
      if (file.isValid()) {
        // the file could have been deleted, so the isValid() check is essential
        fileOpenedImpl(file);
      }
      return;
    }

    if (!changeEntryOrderOnly) { // update entry state
      final FileEditorProvider [] providers = editorsWithProviders.getSecond();
      //LOG.assertTrue(editors.length > 0);
      for (int i = editors.length - 1; i >= 0; i--) {
        final FileEditor           editor = editors   [i];
        final FileEditorProvider provider = providers [i];
        if (!editor.isValid()) {
          // this can happen for example if file extension was changed
          // and this method was called during correponding myEditor close up
          continue;
        }

        final FileEditorState oldState = entry.getState(provider);
        final FileEditorState newState = editor.getState(FileEditorStateLevel.FULL);
        if (!newState.equals(oldState)) {
          entry.putState(provider, newState);
        }
      }
    }
    final Pair <FileEditor, FileEditorProvider> selectedEditorWithProvider = editorManager.getSelectedEditorWithProvider(file);
    if (selectedEditorWithProvider != null) {
      //LOG.assertTrue(selectedEditorWithProvider != null);
      entry.mySelectedProvider = selectedEditorWithProvider.getSecond ();
      LOG.assertTrue(entry.mySelectedProvider != null);

      if(changeEntryOrderOnly){
        myEntriesList.remove(entry);
        myEntriesList.add(entry);
      }
    }
  }

  /**
   * Removes all entries that correspond to invalid files
   */
  private void validateEntries(){
    for(int i=myEntriesList.size()-1; i>=0; i--){
      final HistoryEntry entry = myEntriesList.get(i);
      if(!entry.myFile.isValid()){
        myEntriesList.remove(i);
      }
    }
  }

  /**
   * @return array of valid files that are in the history. The greater is index the more recent the file is.
   */
  public VirtualFile[] getFiles(){
    validateEntries();
    final VirtualFile[] result = new VirtualFile[myEntriesList.size()];
    for(int i=myEntriesList.size()-1; i>=0 ;i--){
      result[i] = myEntriesList.get(i).myFile;
    }
    return result;
  }

  /**
   * Removes specified <code>file</code> from history. The method does
   * nothing if <code>file</code> is not in the history.
   *
   * @exception java.lang.IllegalArgumentException if <code>file</code>
   * is <code>null</code>
   */
  public void removeFile(final VirtualFile file){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    final HistoryEntry entry = getEntry(file);
    if(entry != null){
      myEntriesList.remove(entry);
    }
  }

  public FileEditorState getState(final VirtualFile file, final FileEditorProvider provider) {
    validateEntries();
    final HistoryEntry entry = getEntry(file);
    return entry != null ? entry.getState(provider) : null;
  }

  /**
   * @return may be null
   */
  public FileEditorProvider getSelectedProvider(final VirtualFile file) {
    validateEntries();
    final HistoryEntry entry = getEntry(file);
    return entry != null ? entry.mySelectedProvider : null;
  }

  private HistoryEntry getEntry(final VirtualFile file){
    validateEntries();
    for (int i = myEntriesList.size() - 1; i >= 0; i--) {
      final HistoryEntry entry = myEntriesList.get(i);
      if(file.equals(entry.myFile)){
        return entry;
      }
    }
    return null;
  }

  /**
   * If total number of files in history more then <code>UISettings.RECENT_FILES_LIMIT</code>
   * then removes the oldest ones to fit the history to new size.
   */
  private void trimToSize(){
    final int limit = UISettings.getInstance().RECENT_FILES_LIMIT;
    while(myEntriesList.size()>limit){
      myEntriesList.remove(0);
    }
  }

  public void readExternal(final Element element) {
    // we have to delay xml processing because history entries require EditorStates to be created 
    // which is done via corresponding EditorProviders, those are not accessible before their 
    // is initComponent() called 
    myElement = (Element)element.clone();
  }

  public void writeExternal(final Element element){
    // update history before saving
    final VirtualFile[] openFiles = FileEditorManager.getInstance(myProject).getOpenFiles();
    for (int i = openFiles.length - 1; i >= 0; i--) {
      final VirtualFile file = openFiles[i];
      if(getEntry(file) != null){ // we have to update only files that are in history
        updateHistoryEntry(file, false);
      }
    }

    for (final HistoryEntry entry : myEntriesList) {
      entry.writeExternal(element, myProject);
    }
  }

  /**
   * Updates history
   */
  private final class MyEditorManagerListener extends FileEditorManagerAdapter{
    public void fileOpened(final FileEditorManager source, final VirtualFile file){
      fileOpenedImpl(file);
    }

    public void selectionChanged(final FileEditorManagerEvent event){
      updateHistoryEntry(event.getOldFile(), false);
      updateHistoryEntry(event.getNewFile(), true);
    }
  }

  /**
   * Cuts/extends history length
   */
  private final class MyUISettingsListener implements UISettingsListener{
    public void uiSettingsChanged(final UISettings source) {
      trimToSize();
    }
  }
}
