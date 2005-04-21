
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListPopup;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ShowRecentFilesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files");
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    show(project);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
  }

  private static void show(final Project project){
    final DefaultListModel model = new DefaultListModel();

    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    
    VirtualFile[] files = EditorHistoryManager.getInstance(project).getFiles();
    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();

    for(int i=files.length-1; i>= 0; i--){ // reverse order of files
      VirtualFile file = files[i];
      if(ArrayUtil.find(selectedFiles, file) != 0 && editorProviderManager.getProviders(project, file).length > 0){
        // 1. do not put currently selected file
        // 2. do not include file with no corresponding editor providers
        model.addElement(file);
      }
    }
    final JList list = new JList(model);
    list.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_DELETE) {
            int index = list.getSelectedIndex();
            if (index == -1 || index >= list.getModel().getSize()){
              return;
            }
            Object[] values = list.getSelectedValues();
            for (int i = 0; i < values.length; i++) {
              VirtualFile file=(VirtualFile)values[i];
              model.removeElement(file);
              if(model.getSize() > 0) {
                if(model.getSize()==index){
                  list.setSelectedIndex(model.getSize()-1);
                } else if (model.getSize() > index) {
                  list.setSelectedIndex(index);
                }
              }else{
                list.clearSelection();
              }
              EditorHistoryManager.getInstance(project).removeFile(file);
            }
          }
        }
      }
    );
    Runnable runnable = new Runnable() {
      public void run() {
        Object[] values = list.getSelectedValues();
        for (int i = 0; i < values.length; i++) {
          VirtualFile file = (VirtualFile)values[i];
          FileEditorManager.getInstance(project).openFile(file, true);
        }
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }
    new MyListSpeedSearch(list);

    Window window = null;

    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    if(focusedComponent!=null){
      if(focusedComponent instanceof Window){
        window=(Window)focusedComponent;
      }else{
        window=SwingUtilities.getWindowAncestor(focusedComponent);
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    Rectangle r;
    if (window != null) {
      r = window.getBounds();
    }
    else {
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
    }

    ListPopup popup = new MyListPopup(list, runnable, project);

    if (model.getSize() > 0) {
      Dimension listPreferredSize = list.getPreferredSize();
      list.setVisibleRowCount(0);
      Dimension viewPreferredSize = new Dimension(listPreferredSize.width, Math.min(listPreferredSize.height, r.height - 20));
      ((JViewport)list.getParent()).setPreferredSize(viewPreferredSize);
    }

    popup.getWindow().pack();
    Dimension popupSize=popup.getSize();
    int x = r.x + r.width/2 - popupSize.width/2;
    int y = r.y + r.height/2 - popupSize.height/2;

    popup.show(x,y);
  }

  private static class MyListPopup extends ListPopup {
    public MyListPopup(JList list, Runnable runnable, Project project) {
      super(" Recent Files ", list, runnable, project);
      list.setCellRenderer(new RecentFilesRenderer(project));
    }

    // TODO[anton,vova] use actions and local shortcuts instead
    protected KeyStroke[] getAdditionalSelectKeystrokes() {
      Shortcut[] shortcuts=KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EDIT_SOURCE);
      for (int i = 0; i < shortcuts.length; i++) {
        Shortcut shortcut = shortcuts[i];
        if (shortcut instanceof KeyboardShortcut) {
          return new KeyStroke[] {((KeyboardShortcut)shortcut).getFirstKeyStroke()};
        }
      }
      return new KeyStroke[0];
    }
  }

  private static class RecentFilesRenderer extends ColoredListCellRenderer {
    private final Project myProject;

    public RecentFilesRenderer(Project project) {
      myProject = project;
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof VirtualFile) {
        VirtualFile virtualFile = (VirtualFile)value;
        String name = virtualFile.getName();
        setIcon(IconUtilEx.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, myProject));
        FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE,
                                                       Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));
      }
    }
  }

  private static class MyListSpeedSearch extends ListSpeedSearch {
    public MyListSpeedSearch(JList list) {
      super(list);
    }

    protected String getElementText(Object element) {
      return element instanceof VirtualFile ? ((VirtualFile)element).getName() : null;
    }
  }
}
