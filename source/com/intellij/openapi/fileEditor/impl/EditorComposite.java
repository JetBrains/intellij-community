package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.editor.Document;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.*;

/**
 * This class hides internal structure of UI component which represent
 * set of opened editors. For example, one myEditor is represented by its
 * component, more then one myEditor is wrapped into tabbed pane.
 *
 * @author Vladimir Kondratyev
 */
public abstract class EditorComposite{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorComposite");

  private final EventListenerList myListenerList;
  /**
   * File for which composite is created
   */
  private final VirtualFile myFile;
  /**
   * Whether the composite is pinned or not
   */
  private boolean myPinned;
  /**
   * Editors which are opened in the composite
   */
  protected final FileEditor[] myEditors;
  /**
   * This is initial timestamp of the file. It uses to implement
   * "close non modified editors first" feature.
   */
  private final long myInitialFileTimeStamp;
  protected final TabbedPaneWrapper myTabbedPaneWrapper;
  private final MyComponent myComponent;
  private final FocusWatcher myFocusWatcher;
  /**
   * Currently selected myEditor
   */
  private FileEditor mySelectedEditor;
  private final FileEditorManager myFileEditorManager;
  private final long myInitialFileModificationStamp;

  /**
   * @param file <code>file</code> for which composite is being constructed
   *
   * @param editors <code>edittors</code> that should be placed into the composite
   *
   * @exception java.lang.IllegalArgumentException if <code>editors</code>
   * is <code>null</code>
   *
   * @exception java.lang.IllegalArgumentException if <code>providers</code>
   * is <code>null</code>
   *
   * @exception java.lang.IllegalArgumentException if <code>myEditor</code>
   * arrays is empty
   */
  EditorComposite(
    final VirtualFile file,
    final FileEditor[] editors,
    final FileEditorManager fileEditorManager
  ){
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    if(editors==null){
      throw new IllegalArgumentException("editors cannot be null");
    }
    if (fileEditorManager == null) {
      throw new IllegalArgumentException("fileEditorManager cannot be null");
    }

    myFile = file;
    myEditors = editors;
    myFileEditorManager = fileEditorManager;
    myListenerList = new EventListenerList();
    myInitialFileTimeStamp     = myFile.getTimeStamp();
    myInitialFileModificationStamp = myFile.getModificationStamp();

    if(editors.length > 1){
      final TabbedPaneWrapper wrapper = new TabbedPaneWrapper(SwingConstants.BOTTOM);
      myTabbedPaneWrapper=wrapper;
      myComponent=new MyComponent(wrapper.getComponent()){
        public void requestFocus() {
          wrapper.getComponent().requestFocus();
        }

        public boolean requestDefaultFocus() {
          return wrapper.getComponent().requestDefaultFocus();
        }
      };
      for(int i=0;i<editors.length;i++){
        FileEditor editor = editors[i];
        wrapper.addTab(editor.getName(),editor.getComponent());
      }
      myTabbedPaneWrapper.addChangeListener(new MyChangeListener());
    }
    else if(editors.length==1){
      myTabbedPaneWrapper=null;
      myComponent = new MyComponent(editors[0].getComponent()){
        public void requestFocus() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          LOG.assertTrue(component != null);
          component.requestFocus();
        }

        public boolean requestDefaultFocus() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          LOG.assertTrue(component != null);
          return component.requestDefaultFocus();
        }
      };
    }
    else{
      throw new IllegalArgumentException("editors array cannot be empty");
    }

    mySelectedEditor = editors[0];
    myFocusWatcher = new FocusWatcher();
    myFocusWatcher.install(myComponent);
  }

  /**
   * @return whether myEditor composite is pinned
   */
  public boolean isPinned(){
    return myPinned;
  }

  /**
   * Sets new "pinned" state
   */
  void setPinned(final boolean pinned){
    myPinned = pinned;
  }

  void addEditorManagerListener(final FileEditorManagerListener listener){
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
    myListenerList.add(FileEditorManagerListener.class, listener);
  }

  public void removeEditorManagerListener(final FileEditorManagerListener listener){
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
    myListenerList.remove(FileEditorManagerListener.class, listener);
  }

  private void fireSelectedEditorChanged(
    final FileEditor oldSelectedEditor,
    final FileEditor newSelectedEditor
  ){
    FileEditorManagerEvent event = new FileEditorManagerEvent(myFileEditorManager, myFile, oldSelectedEditor, myFile, newSelectedEditor);
    FileEditorManagerListener[] listeners = (FileEditorManagerListener[])myListenerList.getListeners(FileEditorManagerListener.class);
    for(int i=0; i < listeners.length; i++){
      // source of event is null because the event will be retranslated by the EditorManagerImpl
      listeners[i].selectionChanged(event);
    }
  }

  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to
   * track focus movement inside the myEditor.
   */
  JComponent getPreferredFocusedComponent(){
    Component component = myFocusWatcher.getFocusedComponent();
    if(!(component instanceof JComponent) || !component.isShowing() || !component.isEnabled() || !component.isFocusable()){
      return getSelectedEditor().getPreferredFocusedComponent();
    }
    else{
      return (JComponent)component;
    }
  }

  /**
   * @return file for which composite was created. The method always
   * returns not <code>null</code> valus.
   */
  public VirtualFile getFile() {
    return myFile;
  }

  /**
   * @return initial time stamp of the file (on moment of creation of
   * the composite)
   */
  public long getInitialFileTimeStamp() {
    return myInitialFileTimeStamp;
  }

  /**
   * @return initial modifcation stamp of the file (on moment of creation of
   * the composite)
   */
  public long getInitialFileModificationStamp() {
    return myInitialFileModificationStamp;
  }

  /**
   * @return editors which are opened in the composite. <b>Do not modify
   * this array</b>.
   */
  public FileEditor[] getEditors() {
    return myEditors;
  }

  /**
   * @return currently selected myEditor. The method never returns <code>null</code>.
   */
  FileEditor getSelectedEditor(){
    return getSelectedEditorWithProvider ().getFirst ();
  }

  /**
   * @return currently selected myEditor with its provider. The method never returns <code>null</code>.
   */
  public abstract Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider();

  void setSelectedEditor(final int index){
    if(myEditors.length == 1){
      // nothing to do
      LOG.assertTrue(myTabbedPaneWrapper == null);
    }
    else{
      LOG.assertTrue(myTabbedPaneWrapper != null);
      myTabbedPaneWrapper.setSelectedIndex(index);
    }
  }

  /**
   * @return component which represents set of file editors in the UI
   */
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * @return <code>true</code> if the composite contains at least one
   * modified myEditor
   */
  public boolean isModified(){
    for(int i=myEditors.length-1;i>=0;i--){
      if(myEditors[i].isModified()){
        return true;
      }
    }
    return false;
  }

  /**
   * Handles changes of selected myEditor
   */
  private final class MyChangeListener implements ChangeListener{
    public void stateChanged(ChangeEvent e) {
      FileEditor oldSelectedEditor = mySelectedEditor;
      LOG.assertTrue(oldSelectedEditor != null);
      int selectedIndex = myTabbedPaneWrapper.getSelectedIndex();
      LOG.assertTrue(selectedIndex != -1);
      mySelectedEditor = myEditors[selectedIndex];
      fireSelectedEditorChanged(oldSelectedEditor, mySelectedEditor);
    }
  }
  
  private abstract class MyComponent extends JPanel implements DataProvider{
    public MyComponent(JComponent realComponent){
      super(new BorderLayout());
      add(realComponent, BorderLayout.CENTER);
    }

    public final Object getData(String dataId){
      if (DataConstants.FILE_EDITOR.equals(dataId)) {
        return getSelectedEditor();
      }
      else if(DataConstants.VIRTUAL_FILE.equals(dataId)){
        return myFile;
      }
      else if(DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)){
        return new VirtualFile[] {myFile};
      }
      else{
        JComponent component = getPreferredFocusedComponent();
        if(component instanceof DataProvider){
          return ((DataProvider)component).getData(dataId);
        }
        else{
          return null;
        }
      }
    }
  }
}
