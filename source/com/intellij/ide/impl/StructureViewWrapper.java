package com.intellij.ide.impl;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapper implements StructureView {
  private Project myProject;
  private FileEditor myFileEditor;

  private StructureViewComponent myStructureView;

  private JPanel myPanel;

  private FileEditorManagerListener myEditorManagerListener;

  private Alarm myAlarm;

  private StructureViewFactoryImpl myStructureViewFactory;
  private FileTypeListener myFileTypeListener;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public StructureViewWrapper(Project project) {
    myProject = project;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBackground(UIManager.getColor("Tree.textBackground"));

    myAlarm = new Alarm();

    myStructureViewFactory = (StructureViewFactoryImpl)StructureViewFactory.getInstance(myProject);

    getComponent().addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
          rebuild();
        }
      }
    });

    myEditorManagerListener = new FileEditorManagerAdapter() {
      public void selectionChanged(final FileEditorManagerEvent event) {
        final FileEditor newEditor = event.getNewEditor();
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(
          new Runnable() {
            public void run() {
              if (psiManager.isDisposed()) {
                return; // project may have been closed
              }
              setFileEditor(newEditor);
              if (myStructureViewFactory.AUTOSCROLL_FROM_SOURCE && myStructureView != null) {
                FileEditor newEditor = event.getNewEditor();
                myStructureView.scrollToElementAtCaret(newEditor);
              }
            }
          }, 400
        );
      }
    };
    FileEditorManager.getInstance(project).addFileEditorManagerListener(myEditorManagerListener);

    myFileTypeListener = new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent event){
        //VirtualFile[] files = FileEditorManager.getInstance(myProject).getSelectedFiles();
        //PsiFile psiFile = files.length != 0 ? PsiManager.getInstance(myProject).findFile(files[0]) : null;
        //setFileEditor(psiFile);
      }
    };
    FileTypeManager.getInstance().addFileTypeListener(myFileTypeListener);
  }

  // -------------------------------------------------------------------------
  // StructureView interface implementation
  // -------------------------------------------------------------------------

  public JComponent getComponent() {
    return myPanel;
  }

  public void dispose() {
    myFileEditor = null;
    FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myEditorManagerListener);
    FileTypeManager.getInstance().removeFileTypeListener(myFileTypeListener);
    rebuild();
  }

  public boolean select(Object element, FileEditor fileEditor, boolean requestFocus) {
    if (myStructureView != null) {
      if (!Comparing.equal(myStructureView.getFileEditor(), fileEditor)){
        setFileEditor(fileEditor);
        rebuild();
      }
      return myStructureView.select(element, requestFocus);
    } else {
      return false;
    }
  }

  public FileEditor getFileEditor() {
    return myFileEditor;
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------


  public void setFileEditor(FileEditor fileEditor) {
    if (myFileEditor != null? !myFileEditor.equals(fileEditor) : fileEditor != null) {
      myFileEditor = fileEditor;
      rebuild();
    }
  }

  public void rebuild() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    boolean hadFocus = myStructureView != null && IJSwingUtilities.hasFocus2(myStructureView.getComponent());
    if (myStructureView != null) {
      myStructureView.dispose();
      myStructureView = null;
    }
    myPanel.removeAll();

    if (!isStructureViewShowing()) {
      return;
    }

    if (myFileEditor!=null && myFileEditor.isValid()) {
      StructureViewModel structureViewModel = myFileEditor.getStructureViewModel();
      if (structureViewModel != null) {
        myStructureView = new StructureViewComponent(myFileEditor, structureViewModel, myProject);
        myPanel.add(myStructureView.getComponent(), BorderLayout.CENTER);
        if (hadFocus) {
          IdeFocusTraversalPolicy.getPreferredFocusedComponent(myStructureView.getComponent()).requestFocus();
        }
        myStructureView.centerSelectedRow();
      }
    }
    if (myStructureView == null) {
      myPanel.add(new JLabel("Nothing to show in the Structure View", JLabel.CENTER), BorderLayout.CENTER);
    }

    myPanel.validate();
    myPanel.repaint();
  }

  protected boolean isStructureViewShowing() {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow=windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW);
    if (toolWindow!=null) { // it means that window is registered
      return toolWindow.isVisible();
    }
    return false;
  }

}