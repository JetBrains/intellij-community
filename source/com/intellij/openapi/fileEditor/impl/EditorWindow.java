package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ArrayListSet;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Author: msk
 */
public class EditorWindow {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorWindow");

  protected JPanel myPanel;
  private EditorTabbedContainer myTabbedPane;
  public int myInsideTabChange;
  private MyTabbedContainerChangeListener myChangeListener;
  protected final EditorsSplitters myOwner;

  protected EditorWindow(final EditorsSplitters owner) {
    myOwner = owner;
    myPanel = new JPanel(new BorderLayout());
    myTabbedPane = null;

    final int tabPlacement = UISettings.getInstance().EDITOR_TAB_PLACEMENT;
    //if (tabPlacement == UISettings.TABS_NONE) {
    //  tabPlacement = SwingConstants.TOP;
    //}
    //else {
    //}
    if (tabPlacement != UISettings.TABS_NONE)
      createTabs(tabPlacement);

    // Tab layout policy
    if (UISettings.getInstance().SCROLL_TAB_LAYOUT_IN_EDITOR) {
      setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    } else {
      setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
    }

    getWindows().add(this);
    if (myOwner.myCurrentWindow == null) {
      myOwner.setCurrentWindow(this, false);
    }
  }

  private void createTabs(int tabPlacement) {
    LOG.assertTrue (myTabbedPane == null);
    myTabbedPane = new EditorTabbedContainer(this, getManager().myProject, tabPlacement, getManager());
    myTabbedPane.addChangeListener(myChangeListener = new MyTabbedContainerChangeListener());
    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
  }

  private ArrayListSet<EditorWindow> getWindows() {
    return myOwner.myWindows;
  }



  private void dispose() {
    disposeTabs();
    myChangeListener = null;
    getWindows ().remove(this);
  }

  private void disposeTabs() {
    if (myTabbedPane != null) {
      myTabbedPane.removeChangeListener(myChangeListener);
      myTabbedPane = null;
    }
    myPanel.removeAll();
    myPanel.revalidate();
  }


  public void closeFile(final VirtualFile file) {
    closeFile (file, true);
  }

  public void closeFile(final VirtualFile file, final boolean unsplit) {
    final EditorWithProviderComposite[] editors = getManager().getEditorComposites(file);
    LOG.assertTrue(editors != null);
    final EditorWithProviderComposite editor = findFileComposite(file);
    getManager().disposeComposite(editor);

    if (myTabbedPane != null) {
      final int componentIndex = findComponentIndex(editor.getComponent());
      if (componentIndex >= 0) { // editor could close itself on decomposition
        if (componentIndex > 0) {
          myTabbedPane.setSelectedIndex(componentIndex - 1);
        }
        myTabbedPane.removeTabAt(componentIndex);
      }
      if (unsplit && getTabCount() == 0) {
        unsplit ();
      }
    }
    else {
      myPanel.removeAll ();
    }
    myPanel.revalidate ();
    if (editors.length == 1) {
      getManager().closeFile(file);
    }
  }

  public void removeEditor(final EditorWithProviderComposite editor) {
    getManager().disposeComposite(editor);
    if (myTabbedPane != null) {
      int componentIndex = findComponentIndex(editor.getComponent());
      if (componentIndex >= 0) { // editor could close itself while disposing.
        myTabbedPane.removeTabAt(componentIndex);
      }
    }
  }

  public FileEditorManagerImpl getManager() { return myOwner.getManager(); }

  public int getTabCount() {
    if (myTabbedPane != null)
      return myTabbedPane.getTabCount ();
    return myPanel.getComponentCount();
  }

  public void setForegroundAt(final int index, final Color color) {
    if (myTabbedPane != null) {
      myTabbedPane.setForegroundAt(index, color);
    }
  }

  public void setIconAt(final int index, final Icon icon) {
    if (myTabbedPane != null) {
      myTabbedPane.setIconAt(index, icon);
    }
  }

  public void setTitleAt(final int index, final String text) {
    if (myTabbedPane != null) {
      myTabbedPane.setTitleAt(index, text);
    }
  }

  public void setToolTipTextAt(final int index, final String text) {
    if (myTabbedPane != null) {
      myTabbedPane.setToolTipTextAt(index, text);
    }
  }


  public void setTabLayoutPolicy(final int policy) {
    try {
      ++ myInsideTabChange;
      if (myTabbedPane != null) {
        myTabbedPane.setTabLayoutPolicy(policy);
      }
    }
    finally {
      -- myInsideTabChange;
    }
  }

  public void setTabsPlacement(final int tabPlacement) {
    try {
      ++ myInsideTabChange;
      if (tabPlacement != UISettings.TABS_NONE) {
        if (myTabbedPane == null) {
          final EditorWithProviderComposite editor = getSelectedEditor();
          myPanel.removeAll();
          createTabs(tabPlacement);
          setEditor (editor);
        }
        else {
          myTabbedPane.setTabPlacement(tabPlacement);
        }
      }
      else if (myTabbedPane != null) {
        final boolean focusEditor = ToolWindowManager.getInstance(getManager().myProject).isEditorComponentActive();
        final VirtualFile currentFile = getSelectedFile();
        final VirtualFile[] files = getFiles();
        for (int i = 0; i < files.length; i++) {
          closeFile(files[i], false);
        }
        disposeTabs();
        getManager().openFileImpl2(this, currentFile, focusEditor && (myOwner.getCurrentWindow() == this), null);
      }
    }
    finally {
      -- myInsideTabChange;
    }
  }

  public void setAsCurrentWindow(final boolean requestFocus) {
    myOwner.setCurrentWindow(this, requestFocus);
  }

  protected class TComp extends JPanel {
    final EditorWithProviderComposite myEditor;

    TComp(final EditorWithProviderComposite editor) {
      super(new BorderLayout());
      myEditor = editor;
      add(editor.getComponent(), BorderLayout.CENTER);
    }
  }

  private final class MyTabbedContainerChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      //assertThread();
      if (myInsideTabChange > 0) { // do not react on own events
        return;
      }
      final EditorComposite composite = getSelectedEditor();
      if (composite != null) {
        final Project project = getManager().myProject;
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            getManager().openFileImpl2(EditorWindow.this, composite.getFile(), true, null);
          }
        },
                                                      "",
                                                      null);
      }
    }
  }

  private void check() {
    for (Iterator<EditorWindow> iterator = getWindows ().iterator(); iterator.hasNext();) {
      final EditorWindow window = iterator.next();
      if (window == this) {
        return;
      }
    }
    LOG.error("EditorWindow not in collection");
  }

  public EditorWithProviderComposite getSelectedEditor() {
    final TComp comp;
    if (myTabbedPane != null) {
      comp = (TComp)myTabbedPane.getSelectedComponent();
    }
    else if (myPanel.getComponentCount () != 0) {
      comp = (TComp)myPanel.getComponent(0);
    }
    else
      return null;

    if (comp != null) {
      return comp.myEditor;
    }
    return null;
  }

  public EditorWithProviderComposite[] getEditors() {
    final int tabCount = getTabCount();
    final EditorWithProviderComposite[] res = new EditorWithProviderComposite[tabCount];
    for (int i = 0; i != tabCount; ++i) {
      res[i] = getEditorAt(i);
    }
    return res;
  }

  public VirtualFile[] getFiles() {
    final int tabCount = getTabCount();
    final VirtualFile[] res = new VirtualFile[tabCount];
    for (int i = 0; i != tabCount; ++i) {
      res[i] = getEditorAt(i).getFile();
    }
    return res;
  }

  public void setSelectedEditor(final EditorComposite editor) {
    if (myTabbedPane == null) return;
    try {
      ++myInsideTabChange;
      if (editor != null) {
        final int index = findFileIndex(editor.getFile());
        if (index != -1) {
          myTabbedPane.setSelectedIndex(index);
        }
      }
    }
    finally {
      --myInsideTabChange;
    }
  }

  public void setEditor(final EditorWithProviderComposite editor) {
    if (editor != null) {
      if (myTabbedPane == null) {
        myPanel.removeAll ();
        myPanel.add (new TComp(editor), BorderLayout.CENTER);
        myPanel.revalidate ();
        return;
      }

      final int index = findEditorIndex(editor);
      try {
        ++myInsideTabChange;
        if (index != -1) {
          setSelectedEditor(editor);
        }
        else {
          final int indexToInsert = myTabbedPane.getSelectedIndex() + 1;
          final VirtualFile file = editor.getFile();
          myTabbedPane.insertTab(file.getPresentableName(), null, new TComp(editor), null, indexToInsert);
          trimToSize(UISettings.getInstance().EDITOR_TAB_LIMIT, file);
          setSelectedEditor(editor);
          myOwner.updateFileIcon(file, true);
          myOwner.updateFileColor(file);
        }
        myOwner.setCurrentWindow(this, false);
      }
      finally {
        --myInsideTabChange;
      }
    }
    myPanel.revalidate();
  }

  public boolean splitAvailable() {
    return getTabCount() >= (getManager().isSingleFileMode() ? 2 : 1);
  }

  public EditorWindow split(final int orientation) {
    check();
    if (splitAvailable()) {
      final JPanel panel = myPanel;
      final int tabCount = getTabCount();
      if (tabCount != 0) {
        final boolean singleFileMode = getManager().isSingleFileMode();
        final EditorWithProviderComposite firstEC = getEditorAt(0);
        myPanel = new JPanel(new BorderLayout());
        final Splitter splitter = new Splitter(orientation == JSplitPane.VERTICAL_SPLIT, 0.5f, 0.1f, 0.9f);
        final EditorWindow res = new EditorWindow(myOwner);
        if (myTabbedPane != null) {
          final EditorWithProviderComposite selectedEditor = getSelectedEditor();
          panel.remove(myTabbedPane.getComponent());
          panel.add(splitter, BorderLayout.CENTER);
          splitter.setFirstComponent(myPanel);
          myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
          splitter.setSecondComponent(res.myPanel);
          if (singleFileMode) {
            final TComp component = (TComp)myTabbedPane.getComponent();
            myOwner.remove(component);
            res.setEditor(component.myEditor);
          }
          else {
            for (int i = 0; i != tabCount; ++i) {
              final EditorWithProviderComposite eC = getEditorAt(i);
              final VirtualFile file = eC.getFile();
              FileEditorManagerImpl.openFileImpl3(res, file, false, null);
              res.setFilePinned (file, isFilePinned (file));
            }
            res.setSelectedEditor(selectedEditor);
            selectedEditor.getComponent().requestFocus();
          }

          panel.revalidate();
        }
        else {
          panel.removeAll();
          panel.add(splitter, BorderLayout.CENTER);
          splitter.setFirstComponent(myPanel);
          splitter.setSecondComponent(res.myPanel);
          panel.revalidate();
          final VirtualFile file = firstEC.getFile();
          FileEditorManagerImpl.openFileImpl3(this, file, true, null);
          FileEditorManagerImpl.openFileImpl3(res, file, false, null);
        }
        return res;
      }
    }
    return this;
  }


  public EditorWindow[] findSiblings() {
    check();
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();
    if (myPanel.getParent() instanceof Splitter) {
      final Splitter splitter = (Splitter)myPanel.getParent();
      for (Iterator<EditorWindow> elem = getWindows().iterator(); elem.hasNext();) {
        final EditorWindow win = elem.next();
        if (win != this && SwingUtilities.isDescendingFrom(win.myPanel, splitter)) {
          res.add(win);
        }
      }
    }
    return res.toArray(new EditorWindow[res.size()]);
  }

  public Container getParent() {
    check();
    return myPanel.getParent();
  }

  public void changeOrientation() {
    check();
    final Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      final Splitter splitter = (Splitter)parent;
      splitter.setOrientation(!splitter.getOrientation());
    }
  }

  protected void updateFileIcon(VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    LOG.assertTrue(index != -1);
    setIconAt(index, getFileIcon(file));
  }

  private String getFileTooltipText(final VirtualFile file) {
    final StringBuffer tooltipText = new StringBuffer();
    final Module module = VfsUtil.getModuleForFile(getManager().myProject, file);
    if (module != null) {
      tooltipText.append("[");
      tooltipText.append(module.getName());
      tooltipText.append("] ");
    }
    tooltipText.append(file.getPresentableUrl());
    final String text = tooltipText.toString();
    return text;
  }

  protected void updateFileName(VirtualFile file) {
    final int index = findEditorIndex(findFileComposite(file));
    if (index != -1) {
      setTitleAt(index, file.getPresentableName());
      setToolTipTextAt(index, getFileTooltipText(file));
    }
  }

  /**
   * @return icon which represents file's type and modification status
   */
  protected Icon getFileIcon(final VirtualFile file) {
    LOG.assertTrue(file != null);

    final ArrayList<Icon> icons = new ArrayList<Icon>(6);
    icons.add(IconUtil.getIcon(file, 0, getManager ().myProject));

    // Read-only
    if (!file.isWritable()) {
      icons.add(FileEditorManagerImpl.LOCKED_ICON);
    }

    // Pinned
    final EditorComposite composite = findFileComposite(file);
    if (composite != null && composite.isPinned()) {
      icons.add(IconLoader.getIcon("/nodes/tabPin.png"));
    }

    // Modified
    if (UISettings.getInstance().MARK_MODIFIED_TABS_WITH_ASTERISK) {
      if (composite != null && composite.isModified()) {
        icons.add(FileEditorManagerImpl.MODIFIED_ICON);
      }
      else {
        icons.add(FileEditorManagerImpl.GAP_ICON);
      }
    }

    final LayeredIcon result = new LayeredIcon(icons.size());
    for (int i = icons.size() - 1; i >= 0; i--) {
      result.setIcon(icons.get(i), i);
    }

    return result;
  }
  public void unsplit() {
    check();
    final Container parent = myPanel.getParent();
    if (parent instanceof Splitter) {
      final EditorWindow[] siblings = findSiblings();
      final JPanel parent2 = (JPanel)parent.getParent();
      for (int i = 0; i < siblings.length; i++) {
        final EditorWindow sibling = siblings[i];
        final EditorWithProviderComposite[] editors = sibling.getEditors();
        for (int j = 0; j < editors.length; j++) {
          final EditorWithProviderComposite editor = editors[j];
          if (myTabbedPane != null && getTabCount() < UISettings.getInstance().EDITOR_TAB_LIMIT && findFileComposite(editor.getFile()) == null) {
            setEditor(editor);
          }
          else {
            getManager().disposeComposite(editor);
          }
        }
        LOG.assertTrue(sibling != this);
        sibling.dispose();
      }
      parent2.remove(parent);
      if (myTabbedPane != null) {
        parent2.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
      }
      else {
        parent2.add(myPanel.getComponent(0), BorderLayout.CENTER);
      }
      parent2.revalidate();
      myPanel = parent2;
      myOwner.setCurrentWindow(this, false);
    }
  }

  public void unsplitAll() {
    check();
    while (inSplitter()) {
      unsplit();
    }
  }

  public boolean inSplitter() {
    check();
    return (myPanel.getParent() instanceof Splitter);
  }

  public VirtualFile getSelectedFile() {
    check();
    final EditorWithProviderComposite editor = getSelectedEditor();
    return (editor == null ? null : editor.getFile());
  }

  public EditorWithProviderComposite findFileComposite(final VirtualFile file) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getFile ().equals (file)) {
        return editor;
      }
    }
    return null;
  }


  public int findComponentIndex(final Component component) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.getComponent ().equals (component)) {
        return i;
      }
    }
    return -1;
  }

  public int findEditorIndex(final EditorComposite editorToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      final EditorWithProviderComposite editor = getEditorAt(i);
      if (editor.equals (editorToFind)) {
        return i;
      }
    }
    return -1;
  }

  public int findFileIndex(final VirtualFile fileToFind) {
    for (int i = 0; i != getTabCount(); ++i) {
      final VirtualFile file = getFileAt(i);
      if (file.equals (fileToFind)) {
        return i;
      }
    }
    return -1;
  }

  private EditorWithProviderComposite getEditorAt(final int i) {
    final TComp comp;
    if (myTabbedPane != null)
      comp = (TComp)myTabbedPane.getComponentAt(i);
    else {
      LOG.assertTrue (i <= 1);
      comp = (TComp)myPanel.getComponent(i);
    }
    return comp.myEditor;
  }

  public boolean isFileOpen(final VirtualFile file) {
    return findFileComposite(file) != null;
  }

  public boolean isFilePinned(final VirtualFile file) {
    if(!isFileOpen(file)){
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    getManager().assertThread();
    final EditorComposite editorComposite = findFileComposite(file);
    return editorComposite.isPinned();
  }

  public void setFilePinned(final VirtualFile file, final boolean pinned) {
    if(!isFileOpen(file)){
      throw new IllegalArgumentException("file is not open: " + file.getPath());
    }
    getManager().assertThread();
    final EditorComposite editorComposite = findFileComposite(file);
    editorComposite.setPinned(pinned);
    updateFileIcon(file);
  }

  void trimToSize(final int limit, final VirtualFile fileToIgnore) {
    if (myTabbedPane == null)
      return;
    final boolean closeNonModifiedFilesFirst = UISettings.getInstance().CLOSE_NON_MODIFIED_FILES_FIRST;
    final EditorComposite selectedComposite = getSelectedEditor();
    try {
      myInsideTabChange++;
      while_label:
      while (myTabbedPane.getTabCount() > limit && myTabbedPane.getTabCount() > 0) {
        // If all tabs are pinned then do nothings. Othrwise we will get infinitive loop
        boolean allTabsArePinned = true;
        for (int i = myTabbedPane.getTabCount() - 1; i >= 0; i--) {
          final VirtualFile file = getFileAt(i);
          if (fileCanBeClosed(file, fileToIgnore)) {
            allTabsArePinned = false;
            break;
          }
        }
        if (allTabsArePinned) {
          return;
        }

        // Try to close non-modified files first (is specified in oprion)
        if (closeNonModifiedFilesFirst) {
          // Search in history
          final VirtualFile[] allFiles = getFiles();
          final VirtualFile[] histFiles = EditorHistoryManager.getInstance(getManager ().myProject).getFiles();

          // first, we search for files not in history
          for (int i = 0; i != allFiles.length; ++ i) {
            final VirtualFile file = allFiles[i];
            if (fileCanBeClosed(file, fileToIgnore)) {
              boolean found = false;
              for (int j = 0; j != histFiles.length; j++)
                if (histFiles[j] == file) {
                  found = true;
                  break;
                }
              if (!found) {
                closeFile(file);
                continue while_label;
              }
            }
          }

          for (int i = 0; i < histFiles.length; i++) {
            final VirtualFile file = histFiles[i];
            if (!fileCanBeClosed(file, fileToIgnore)) {
              continue;
            }

            final EditorComposite composite = findFileComposite(file);
            //LOG.assertTrue(composite != null);
            if (composite != null && composite.getInitialFileTimeStamp() == file.getTimeStamp()) {
              // we found non modified file
              closeFile(file);
              continue while_label;
            }
          }

          // Search in tabbed pane
          final VirtualFile selectedFile = getSelectedFile();
          for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            final VirtualFile file = getFileAt(i);
            final EditorComposite composite = getEditorAt(i);
            if (!fileCanBeClosed(file, fileToIgnore)) {
              continue;
            }
            if (!selectedFile.equals(file)) {
              if (composite.getInitialFileTimeStamp() == file.getTimeStamp()) {
                // we found non modified file
                closeFile(file);
                continue while_label;
              }
            }
          }
        }

        // It's non enough to close non-modified files only. Try all other files.
        // Search in history from less frequently used.
        {
          final VirtualFile[]  allFiles = getFiles();
          final VirtualFile[] histFiles = EditorHistoryManager.getInstance(getManager ().myProject).getFiles();

          // first, we search for files not in history
          for (int i = 0; i != allFiles.length; ++ i) {
            final VirtualFile file = allFiles[i];
            if (fileCanBeClosed(file, fileToIgnore)) {
              boolean found = false;
              for (int j = 0; j != histFiles.length; j++)
                if (histFiles[j] == file) {
                  found = true;
                  break;
                }
              if (!found) {
                closeFile(file);
                continue while_label;
              }
            }
          }


          for (int i = 0; i < histFiles.length; i++) {
            final VirtualFile file = histFiles[i];
            if (fileCanBeClosed(file, fileToIgnore)) {
              closeFile(file);
              continue while_label;
            }
          }
        }

        // Close first opened file in tabbed pane that isn't a selected one
        {
          final VirtualFile selectedFile = getSelectedFile();
          for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            final VirtualFile file = getFileAt(i);
            if (!fileCanBeClosed(file, fileToIgnore)) {
              continue;
            }
            if (!selectedFile.equals(file)) {
              closeFile(file);
              continue while_label;
            }
            else if (i == myTabbedPane.getTabCount() - 1) {
              // if file is selected one and it's last file that we have no choice as close it
              closeFile(file);
              continue while_label;
            }
          }
        }
      }
    }
    finally {
      setSelectedEditor(selectedComposite);
      --myInsideTabChange;
    }
  }

  private boolean fileCanBeClosed(final VirtualFile file, final VirtualFile fileToIgnore) {
    return isFileOpen (file) && !file.equals(fileToIgnore) && !isFilePinned(file);
  }

  protected VirtualFile getFileAt(int i) {
    return getEditorAt(i).getFile();
  }
}
