package com.intellij.openapi.fileEditor.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This class hides internal structure of UI component which represent
 * set of opened editors. For example, one myEditor is represented by its
 * component, more then one myEditor is wrapped into tabbed pane.
 *
 * @author Vladimir Kondratyev
 */
public abstract class EditorComposite{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorComposite");

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
  private final FileEditorManagerEx myFileEditorManager;
  private final long myInitialFileModificationStamp;
  private Map<FileEditor, FileEditorInfoPane> myInfoPanes = new HashMap<FileEditor, FileEditorInfoPane>();
  private Map<FileEditor, JComponent> myTopComponents = new HashMap<FileEditor, JComponent>();
  private Map<FileEditor, JComponent> myBottomComponents = new HashMap<FileEditor, JComponent>();

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
    @NotNull final VirtualFile file,
    @NotNull final FileEditor[] editors,
    @NotNull final FileEditorManagerEx fileEditorManager
  ){
    myFile = file;
    myEditors = editors;
    myFileEditorManager = fileEditorManager;
    myInitialFileTimeStamp     = myFile.getTimeStamp();
    myInitialFileModificationStamp = myFile.getModificationStamp();

    if(editors.length > 1){
      final TabbedPaneWrapper wrapper = new TabbedPaneWrapper(SwingConstants.BOTTOM, false);
      myTabbedPaneWrapper=wrapper;
      myComponent=new MyComponent(wrapper.getComponent()){
        public void requestFocus() {
          wrapper.getComponent().requestFocus();
        }

        public boolean requestDefaultFocus() {
          return wrapper.getComponent().requestDefaultFocus();
        }
      };
      for (FileEditor editor : editors) {
        wrapper.addTab(editor.getName(), createEditorComponent(editor));
      }
      myTabbedPaneWrapper.addChangeListener(new MyChangeListener());
    }
    else if(editors.length==1){
      myTabbedPaneWrapper=null;
      myComponent = new MyComponent(createEditorComponent(editors[0])){
        public void requestFocus() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          if (component != null) {
            component.requestFocus();
          }
        }

        public boolean requestDefaultFocus() {
          JComponent component = editors[0].getPreferredFocusedComponent();
          if (component != null) {
            return component.requestDefaultFocus();
          }
          return false;
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

  private JComponent createEditorComponent(final FileEditor editor) {
    JPanel component = new JPanel(new BorderLayout());
    component.add(editor.getComponent(), BorderLayout.CENTER);

    final JPanel topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
    myTopComponents.put(editor, topPanel);
    component.add(topPanel, BorderLayout.NORTH);

    final JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
    myBottomComponents.put(editor, bottomPanel);
    component.add(bottomPanel, BorderLayout.SOUTH);

    FileEditorInfoPane infoPane = new FileEditorInfoPane();
    myInfoPanes.put(editor, infoPane);
    addTopComponent(editor, infoPane);

    return component;
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

  private void fireSelectedEditorChanged(final FileEditor oldSelectedEditor, final FileEditor newSelectedEditor){
    if (!myFileEditorManager.isInsideChange() && !Comparing.equal(oldSelectedEditor, newSelectedEditor)) {
      final FileEditorManagerEvent event = new FileEditorManagerEvent(myFileEditorManager, myFile, oldSelectedEditor, myFile, newSelectedEditor);
      final FileEditorManagerListener publisher = myFileEditorManager.getProject().getMessageBus().syncPublisher(ProjectTopics.FILE_EDITOR_MANAGER);
      publisher.selectionChanged(event);
    }
  }


  /**
   * @return preferred focused component inside myEditor composite. Composite uses FocusWatcher to
   * track focus movement inside the myEditor.
   */
  JComponent getPreferredFocusedComponent(){
    final Component component = myFocusWatcher.getFocusedComponent();
    if(!(component instanceof JComponent) || !component.isShowing() || !component.isEnabled() || !component.isFocusable()){
      return getSelectedEditor().getPreferredFocusedComponent();
    }
    return (JComponent)component;
  }

  /**
   * @return file for which composite was created. The method always
   * returns not <code>null</code> valus.
   */
  public VirtualFile getFile() {
    return myFile;
  }

  public FileEditorManager getFileEditorManager() {
    return myFileEditorManager;
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

  public void addTopComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, true, false);
  }

  public void removeTopComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, true, true);
  }

  public void addBottomComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, false, false);
  }

  public void removeBottomComponent(FileEditor editor, JComponent component) {
    manageTopOrBottomComponent(editor, component, false, true);
  }

  private void manageTopOrBottomComponent(FileEditor editor, JComponent component, boolean top, boolean remove) {
    final JComponent container = top ? myTopComponents.get(editor) : myBottomComponents.get(editor);
    assert container != null;

    if (remove) {
      container.remove(component);
    } else {
      container.add(component);
    }
    container.revalidate();
  }

  public FileEditorInfoPane getPane(FileEditor editor) {
    return myInfoPanes.get(editor);
  }

  /**
   * @return currently selected myEditor. The method never returns <code>null</code>.
   */
  @NotNull FileEditor getSelectedEditor(){
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
