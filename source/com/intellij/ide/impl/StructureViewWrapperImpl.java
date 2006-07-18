package com.intellij.ide.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapperImpl implements StructureViewWrapper {
  private Project myProject;
  private FileEditor myFileEditor;

  private StructureView myStructureView;

  private JPanel myPanel;

  private FileEditorManagerListener myEditorManagerListener;

  private Alarm myAlarm;

  private FileTypeListener myFileTypeListener;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public StructureViewWrapperImpl(Project project) {
    myProject = project;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBackground(UIUtil.getTreeTextBackground());

    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    getComponent().addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
          rebuild();
        }
      }
    });

    myEditorManagerListener = new FileEditorManagerAdapter() {
      private FileEditorManagerEvent myLastEvent;
      public void selectionChanged(final FileEditorManagerEvent event) {
        myLastEvent = event;
        //System.out.println(event.getNewFile().getPath());
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(
          new Runnable() {
            public void run() {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  if (myLastEvent == null) {
                    return;
                  }
                  try {
                    if (psiManager.isDisposed()) {
                      return; // project may have been closed
                    }
                    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                    setFileEditor(myLastEvent.getNewEditor());
                  }
                  finally {
                    myLastEvent = null;
                  }
                }
              }, ModalityState.NON_MMODAL);
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

  public boolean selectCurrentElement(FileEditor fileEditor, boolean requestFocus) {
    if (myStructureView != null) {
      if (!Comparing.equal(myStructureView.getFileEditor(), fileEditor)){
        setFileEditor(fileEditor);
        rebuild();
      }
      return myStructureView.navigateToSelectedElement(requestFocus);
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
    final boolean fileChanged = myFileEditor != null ? !myFileEditor.equals(fileEditor) : fileEditor != null;
    if (fileChanged) {
      myFileEditor = fileEditor;      
    }
    if (fileChanged || (isStructureViewShowing() && myPanel.getComponentCount() == 0 && myFileEditor != null)) {
      rebuild();
    }
  }

  public void rebuild() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    boolean hadFocus = myStructureView != null && IJSwingUtilities.hasFocus2(myStructureView.getComponent());
    if (myStructureView != null) {
      myStructureView.storeState();
      Disposer.dispose(myStructureView);
      myStructureView = null;
    }
    myPanel.removeAll();

    if (!isStructureViewShowing()) {
      return;
    }

    if (myFileEditor!=null && myFileEditor.isValid()) {
      final StructureViewBuilder structureViewBuilder = myFileEditor.getStructureViewBuilder();
      if (structureViewBuilder != null) {
        myStructureView = structureViewBuilder.createStructureView(myFileEditor, myProject);
        myPanel.add(myStructureView.getComponent(), BorderLayout.CENTER);
        if (hadFocus) {
          IdeFocusTraversalPolicy.getPreferredFocusedComponent(myStructureView.getComponent()).requestFocus();
        }
        myStructureView.restoreState();
        myStructureView.centerSelectedRow();
      }
    }
    if (myStructureView == null) {
      myPanel.add(new JLabel(IdeBundle.message("message.nothing.to.show.in.structure.view"), JLabel.CENTER), BorderLayout.CENTER);
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