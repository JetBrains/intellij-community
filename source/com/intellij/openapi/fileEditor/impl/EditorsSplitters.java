package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ArrayListSet;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * Author: msk
 */
public class EditorsSplitters extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.EditorsSplitters");
  protected EditorWindow myCurrentWindow;
  private final FileEditorManagerImpl myManager;
  private Element mySplittersElement;  // temporarily used during initialization
  private final MyFocusWatcher myFocusWatcher;
  protected int myInsideChange;

  public EditorsSplitters(final FileEditorManagerImpl manager) {
    super(new BorderLayout());
    setOpaque(true);
    setBackground(Color.GRAY);
    myInsideChange = 0;
    myFocusWatcher = new MyFocusWatcher();
    myFocusWatcher.install(this);
    myManager = manager;
    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    clear();
  }

  public FileEditorManagerImpl getManager() {
    return myManager;
  }

  public void clear() {
    removeAll();
    myWindows.clear();
    myCurrentWindow = null;
    repaint (); // revalidate doesn't repaint correctly after "Close All" 
  }

  public VirtualFile getCurrentFile() {
    if (myCurrentWindow != null) {
      return myCurrentWindow.getSelectedFile();
    }
    return null;
  }


  public void writeExternal(final Element element) {
    if (getComponentCount() != 0) {
      final Component comp = getComponent(0);
      LOG.assertTrue(comp instanceof JPanel);
      final JPanel panel = (JPanel)comp;
      if (panel.getComponentCount() != 0) {
        final Element res = writePanel(panel);
        element.addContent(res);
      }
    }
  }

  public Element writePanel(final JPanel panel) {
    final Component comp = panel.getComponent(0);
    if (comp instanceof Splitter) {
      final Splitter splitter = (Splitter)comp;
      final Element res = new Element("splitter");
      res.setAttribute("split-orientation", splitter.getOrientation() ? "vertical" : "horizontal");
      res.setAttribute("split-proportion", Float.toString(splitter.getProportion()));
      final Element first = new Element("split-first");
      first.addContent(writePanel((JPanel)splitter.getFirstComponent()));
      final Element second = new Element("split-second");
      second.addContent(writePanel((JPanel)splitter.getSecondComponent()));
      res.addContent(first);
      res.addContent(second);
      return res;
    }
    else if (comp instanceof JPanel) {
      final Element res = new Element("leaf");
      final EditorWindow window = findWindowWith(comp);
      if (window != null) {
        final EditorWithProviderComposite[] composites = window.getEditors();
        for (int i = 0; i < composites.length; i++) {
          final Element fileElement = new Element("file");
          final VirtualFile file = window.getFileAt(i);
          fileElement.setAttribute("leaf-file-name", file.getName()); // TODO: all files
          final EditorWithProviderComposite composite = composites[i];
          final FileEditor[] editors = composite.getEditors();
          final FileEditorState[] states = new FileEditorState[editors.length];
          for (int j = 0; j < states.length; j++) {
            states[j] = editors[j].getState(FileEditorStateLevel.FULL);
            LOG.assertTrue(states[j] != null);
          }
          final int selectedProviderIndex = ArrayUtil.find(editors, composite.getSelectedEditor());
          LOG.assertTrue(selectedProviderIndex != -1);
          final FileEditorProvider[] providers = composite.getProviders();
          final HistoryEntry entry = new HistoryEntry(file, providers, states, providers[selectedProviderIndex]); // TODO
          entry.writeExternal(fileElement, getManager().myProject);
          fileElement.setAttribute("pinned",         Boolean.toString(window.isFilePinned(window.getSelectedFile())));
          fileElement.setAttribute("current",        Boolean.toString(composite.equals (getManager ().getLastSelected ())));
          fileElement.setAttribute("current-in-tab", Boolean.toString(composite.equals (window.getSelectedEditor())));
          res.addContent(fileElement);
        }
      }
      return res;
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public void openFiles() {
    if (mySplittersElement != null) {
      LOG.assertTrue(myCurrentWindow == null);
      final JPanel comp = readExternalPanel(mySplittersElement);
      if (comp != null) {
        removeAll();
        add(comp, BorderLayout.CENTER);
        final EditorComposite lastSelected = getManager ().getLastSelected();
        if(lastSelected != null)  {
          FileEditorManagerImpl.openFileImpl3(myCurrentWindow, lastSelected.getFile(), true, null);
          //lastSelected.getComponent().requestFocus();
          //ToolWindowManager.getInstance(getManager().myProject).activateEditorComponent();
        }
        mySplittersElement = null;
      }
    }
  }

  public void readExternal(final Element element) {
    mySplittersElement = element;
  }

  public JPanel readExternalPanel(final Element element) {
    final Element splitterElement = element.getChild("splitter");
    if (splitterElement != null) {
      LOG.info("splitter");
      final JPanel res = new JPanel(new BorderLayout());
      final boolean orientation = "vertical".equals(splitterElement.getAttributeValue("split-orientation"));
      final float proportion = Float.valueOf(splitterElement.getAttributeValue("split-proportion")).floatValue();
      final Element first = splitterElement.getChild("split-first");
      final Element second = splitterElement.getChild("split-second");
      final Splitter splitter = new Splitter(orientation, proportion, 0.1f, 0.9f);
      splitter.setFirstComponent(readExternalPanel(first));
      splitter.setSecondComponent(readExternalPanel(second));
      res.add(splitter, BorderLayout.CENTER);
      return res;
    }
    else {
      final Element leaf = element.getChild("leaf");
      if (leaf != null) {
        final EditorWindow window = new EditorWindow(this);
        try {
          window.myInsideTabChange++;
          final List<Element> children = leaf.getChildren("file");
          VirtualFile currentFile = null;
          for (Iterator<Element> iterator = children.iterator(); iterator.hasNext();) {
            final Element file = iterator.next();
            final HistoryEntry entry;
            entry = new HistoryEntry(getManager().myProject, file.getChild(HistoryEntry.TAG));
            FileEditorManagerImpl.openFileImpl3(window, entry.myFile, false, entry);
            if (getManager().isFileOpen(entry.myFile)) {
              window.setFilePinned(entry.myFile, Boolean.valueOf(file.getAttributeValue("pinned")).booleanValue());
              if (Boolean.valueOf(file.getAttributeValue("current-in-tab")).booleanValue()) {
                currentFile = entry.myFile;
              }
              if (Boolean.valueOf(file.getAttributeValue("current")).booleanValue()) {
                setCurrentWindow (window, false);
              }
            }
          }
          if (currentFile != null) {
            final EditorComposite editor = window.findFileComposite(currentFile);
            if (editor != null) {
              window.setSelectedEditor(editor);
            }
          }
        }
        catch (InvalidDataException e) {
        }
        finally {
          window.myInsideTabChange--;
        }
        return window.myPanel;
      }
    }
    return null;
  }

  public VirtualFile[] getOpenFiles() {
    final ArrayListSet<VirtualFile> files = new ArrayListSet<VirtualFile>();
    for (final Iterator<EditorWindow> iter = myWindows.iterator(); iter.hasNext();) {
      final EditorWithProviderComposite[] editors = iter.next().getEditors();
      for (int i = 0; i < editors.length; i++) {
        final EditorWithProviderComposite editor = editors[i];
        files.add(editor.getFile());
      }
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public VirtualFile[] getSelectedFiles() {
    final ArrayListSet<VirtualFile> files = new ArrayListSet<VirtualFile>();
    for (Iterator<EditorWindow> iterator = myWindows.iterator(); iterator.hasNext();) {
      final EditorWindow window = iterator.next();
      final VirtualFile file = window.getSelectedFile();
      if (file != null) {
        files.add(file);
      }
    }
    final VirtualFile[] virtualFiles = files.toArray(new VirtualFile[files.size()]);
    final VirtualFile currentFile = getCurrentFile();
    if (currentFile != null) {
      for (int i = 0; i != virtualFiles.length; ++i) {
        if (virtualFiles[i] == currentFile) {
          virtualFiles[i] = virtualFiles[0];
          virtualFiles[0] = currentFile;
          break;
        }
      }
    }
    return virtualFiles;
  }

  public FileEditor[] getSelectedEditors() {
    final List<FileEditor> editors = new ArrayList<FileEditor>();
    final EditorWindow currentWindow = getCurrentWindow();
    if (currentWindow != null) {
      final EditorWithProviderComposite composite = currentWindow.getSelectedEditor();
      if (composite != null) {
        editors.add (composite.getSelectedEditor());
      }
    }

    for (Iterator<EditorWindow> iterator = myWindows.iterator(); iterator.hasNext();) {
      final EditorWindow window = iterator.next();
      if (!window.equals (currentWindow)) {
        final EditorWithProviderComposite composite = window.getSelectedEditor();
        if (composite != null) {
          editors.add(composite.getSelectedEditor());
        }
      }
    }
    return editors.toArray(new FileEditor[editors.size()]);
  }

  protected void updateFileIcon(final VirtualFile file, final boolean b) {
    LOG.assertTrue(file != null);
    final EditorWindow[] windows = findWindows(file);
    if (windows != null) {
      for (int i = 0; i < windows.length; i++) {
        windows[i].updateFileIcon(file);
      }
    }
  }

  public void updateFileColor(final VirtualFile file) {
    LOG.assertTrue(file != null);
    final EditorWindow[] windows = findWindows(file);
    if (windows != null) {
      for (int i = 0; i < windows.length; i++) {
        final EditorWindow window = windows[i];
        final int index = window.findEditorIndex(window.findFileComposite(file));
        LOG.assertTrue(index != -1);
        window.setForegroundAt(index, getManager().getFileColor(file));
      }
    }
  }

  public void trimToSize(final int editor_tab_limit, final VirtualFile fileToIgnore) {
    for (Iterator<EditorWindow> iterator = myWindows.iterator(); iterator.hasNext();) {
      final EditorWindow window = iterator.next();
      window.trimToSize(editor_tab_limit, fileToIgnore);
    }
  }

  public void setTabsPlacement(final int tabPlacement) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabsPlacement(tabPlacement);
    }
  }

  public void setTabLayoutPolicy(int scrollTabLayout) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabLayoutPolicy(scrollTabLayout);
    }
  }

  public void updateFileName(final VirtualFile file) {
    final EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows [i].updateFileName(file);
    }
  }

  private final class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      if (myCurrentWindow != null) {
        return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myCurrentWindow.getSelectedEditor().getComponent(), this);
      }
      else {
        return IdeFocusTraversalPolicy.getPreferredFocusedComponent(EditorsSplitters.this, this);
      }
    }
  }

  public EditorWindow getCurrentWindow() {
    return myCurrentWindow;
  }

  public EditorWindow getOrCreateCurrentWindow() {
    if (getCurrentWindow() == null) {
      final Iterator<EditorWindow> iterator = myWindows.iterator();
      if (iterator.hasNext()) {
        setCurrentWindow(iterator.next(), false);
      }
      else {
        createCurrentWindow();
      }
    }
    return getCurrentWindow();
  }

  public EditorWindow createCurrentWindow() {
    LOG.assertTrue(myCurrentWindow == null);
    myCurrentWindow = new EditorWindow(this);
    add(myCurrentWindow.myPanel, BorderLayout.CENTER);
    return myCurrentWindow;
  }

  public void setCurrentWindow(final EditorWindow window, final boolean requestFocus) {
    myCurrentWindow = window;
    if (window != null) {
      final EditorWithProviderComposite selectedEditor = myCurrentWindow.getSelectedEditor();
      if (selectedEditor != null) {
        final boolean focusEditor = ToolWindowManager.getInstance(myManager.myProject).isEditorComponentActive();
        if (focusEditor || requestFocus)
          selectedEditor.getComponent().requestFocus ();
      }
    }
  }

  //---------------------------------------------------------

  public EditorWindow findWindow(final EditorComposite editor) {
    for (Iterator<EditorWindow> elem = myWindows.iterator(); elem.hasNext();) {
      final EditorWindow window = elem.next();
      if (window.findEditorIndex(editor) != -1) {
        return window;
      }
    }
    return null;
  }

  public EditorWindow findWindow(final Component editorComponent) {
    for (Iterator<EditorWindow> elem = myWindows.iterator(); elem.hasNext();) {
      final EditorWindow window = elem.next();
      for (Component component = editorComponent; component != null; component = component.getParent()) {
        if (window.findComponentIndex(component) != -1) {
          return window;
        }
      }
    }
    return null;
  }

  public EditorWithProviderComposite[] getEditorsComposites() {
    final ArrayList<EditorWithProviderComposite> res = new ArrayList<EditorWithProviderComposite>();

    for (final Iterator<EditorWindow> iter = myWindows.iterator(); iter.hasNext();) {
      final EditorWithProviderComposite[] editors = iter.next().getEditors();
      for (int i = 0; i < editors.length; i++) {
        final EditorWithProviderComposite editor = editors[i];
        res.add(editor);
      }
    }
    return res.toArray(new EditorWithProviderComposite[res.size()]);
  }

  //---------------------------------------------------------

  protected final ArrayListSet<EditorWindow> myWindows = new ArrayListSet<EditorWindow>();

  public EditorWithProviderComposite[] findEditorComposites(final VirtualFile file) {
    final ArrayList<EditorWithProviderComposite> res = new ArrayList<EditorWithProviderComposite>();
    for (final Iterator<EditorWindow> iter = myWindows.iterator(); iter.hasNext();) {
      final EditorWindow window = iter.next();
      final EditorWithProviderComposite fileComposite = window.findFileComposite(file);
      if (fileComposite != null) {
        res.add(fileComposite);
      }
    }
    return (res.size() == 0 ? null : res.toArray(new EditorWithProviderComposite[res.size()]));
  }

  public EditorWindow[] findWindows(final VirtualFile file) {
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();
    for (final Iterator<EditorWindow> iter = myWindows.iterator(); iter.hasNext();) {
      final EditorWindow window = iter.next();
      if (window.findFileComposite(file) != null) {
        res.add(window);
      }
    }
    return (res.size() == 0 ? null : res.toArray(new EditorWindow[res.size()]));
  }

  public EditorWindow[] findWindowsWithCurrent(final VirtualFile file) {
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();
    for (final Iterator<EditorWindow> iter = myWindows.iterator(); iter.hasNext();) {
      final EditorWindow window = iter.next();
      if (window.getSelectedFile() == file) {
        res.add(window);
      }
    }
    return (res.size() == 0 ? null : res.toArray(new EditorWindow[res.size()]));
  }

  public EditorWindow [] getWindows() {
    return myWindows.toArray(new EditorWindow [myWindows.size()]);
  }

  public EditorWindow[] getOrderedWindows() {
    final ArrayList<EditorWindow> res = new ArrayList<EditorWindow>();

    // Collector for windows in tree ordering:
    class Inner{
      final void collect(final JPanel panel){
        final Component comp = panel.getComponent(0);
        if (comp instanceof Splitter) {
          final Splitter splitter = (Splitter)comp;
          collect((JPanel)splitter.getFirstComponent());
          collect((JPanel)splitter.getSecondComponent());
        }
        else if (comp instanceof JPanel) {
          final EditorWindow window = findWindowWith(comp);
          if (window != null) {
            res.add(window);
          }
        }
      }
    };

    // get root component and traverse splitters tree:
    {
      if (getComponentCount() != 0) {
        final Component comp = getComponent(0);
        LOG.assertTrue(comp instanceof JPanel);
        final JPanel panel = (JPanel)comp;
        if (panel.getComponentCount() != 0) {
          new Inner().collect (panel);
        }
      }
    }

    LOG.assertTrue(res.size() == myWindows.size());
    return res.toArray(new EditorWindow [res.size()]);
  }

  public EditorWindow findWindowWith(final Component component) {
    if (component != null) {
      for (final Iterator<EditorWindow> iter = myWindows.iterator(); iter.hasNext();) {
        final EditorWindow window = iter.next();
        if (SwingUtilities.isDescendingFrom(component, window.myPanel)) {
          return window;
        }
      }
    }
    return null;
  }

  private final class MyFocusWatcher extends FocusWatcher {
    protected void focusedComponentChanged(final Component component) {
      if (myInsideChange > 0 || component == null) {
        return;
      }
      final EditorWindow oldActiveWindow = getCurrentWindow();
      final EditorWindow newActiveWindow = findWindowWith(component);
      if (oldActiveWindow != newActiveWindow) {
        setCurrentWindow(newActiveWindow, false);
        getManager().updateFileName(newActiveWindow.getSelectedFile());
        getManager().fireSelectionChanged((oldActiveWindow == null ? null : oldActiveWindow.getSelectedEditor()), newActiveWindow.getSelectedEditor());
      }
    }
  }
}
