package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeCounter;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.IOException;

public class MergeRequestImpl extends MergeRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.MergeData");
  private final DiffContent[] myDiffContents = new DiffContent[3];
  private String myWindowTitle = null;
  private String[] myVersionTitles = null;
  private int myResult;
  private String myHelpId;

  public MergeRequestImpl(String left, MergeVersion base, String right, Project project) {
    super(project);
    myDiffContents[0] = new SimpleContent(left);
    myDiffContents[1] = new MergeContent(base);
    myDiffContents[2] = new SimpleContent(right);
  }

  public DiffContent[] getContents() { return myDiffContents; }

  public String[] getContentTitles() { return myVersionTitles; }
  public void setVersionTitles(String[] versionTitles) { myVersionTitles = versionTitles; }

  public String getWindowTitle() { return myWindowTitle; }
  public void setWindowTitle(String windowTitle) { myWindowTitle = windowTitle; }

  public void setResult(int result) {
    if (result == DialogWrapper.OK_EXIT_CODE) getMergeContent().applyChanges();
    myResult = result;
  }

  public int getResult() { return myResult; }

  private MergeContent getMergeContent() { return (MergeContent)myDiffContents[1]; }

  public DiffContent getResultContent() { return getMergeContent(); }

  public void setActions(final DialogBuilder builder, MergePanel2 mergePanel) {
    builder.addOkAction().setText("Apply");
    builder.addCancelAction();
    builder.setCancelOperation(new Runnable() {
      public void run() {
        if (Messages.showYesNoDialog(getProject(),
                                     "Are you sure you want to exit without applying changes?",
                                     "Cancel Visual Merge",
                                     Messages.getQuestionIcon()) == 0) {
          builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
        }
      }
    });
    new AllResolvedListener(mergePanel, builder.getDialogWrapper());
  }

  public String getHelpId() {
    return myHelpId;
  }

  public void setHelpId(String helpId) {
    myHelpId = helpId;
  }

  private class MergeContent extends DiffContent {
    private final MergeVersion myTarget;
    private final Document myWorikingDocument;

    public MergeContent(MergeVersion target) {
      myTarget = target;
      myWorikingDocument = myTarget.createWorkingDocument(getProject());
      LOG.assertTrue(myWorikingDocument.isWritable());
    }

    public void applyChanges() {
      myTarget.applyText(myWorikingDocument.getText(), getProject());
    }

    public Document getDocument() { return myWorikingDocument; }

    public OpenFileDescriptor getOpenFileDescriptor(int offset) {
      VirtualFile file = getFile();
      if (file == null) return null;
      return new OpenFileDescriptor(getProject(), file, offset);
    }

    public VirtualFile getFile() {
      return myTarget.getFile();
    }

    public FileType getContentType() {
      return myTarget.getContentType();
    }

    public byte[] getBytes() throws IOException {
      return myTarget.getBytes();
    }
  }

  private class AllResolvedListener implements ChangeCounter.Listener, Runnable {
    private final MergePanel2 myMergePanel;
    private final DialogWrapper myDialogWrapper;
    private boolean myWasInvoked = false;

    public AllResolvedListener(MergePanel2 mergePanel, DialogWrapper dialogWrapper) {
      myMergePanel = mergePanel;
      myDialogWrapper = dialogWrapper;
      ChangeCounter.getOrCreate(myMergePanel.getMergeList()).addListener(this);
    }

    public void run() {
      if (myWasInvoked) return;
      if (!getWholePanel().isDisplayable()) return;
      myWasInvoked = true;
      ChangeCounter.getOrCreate(myMergePanel.getMergeList()).removeListener(this);
      int doApply = Messages.showDialog(getProject(),
                          "All changes have been processed.\nWould you like to save changes and finish merging?",
                          "All Changes Processed",
                          new String[]{"Save and &Finish", "&Continue"}, 0,
                          Messages.getQuestionIcon());
      if (doApply != 0) return;
      myDialogWrapper.close(DialogWrapper.OK_EXIT_CODE);
    }

    private JComponent getWholePanel() {
      return myMergePanel.getComponent();
    }

    public void onCountersChanged(ChangeCounter counter) {
      if (myWasInvoked) return;
      if (counter.getChangeCounter() != 0 || counter.getConflictCounter() != 0) return;
      ApplicationManager.getApplication().invokeLater(this);
    }
  }
}
