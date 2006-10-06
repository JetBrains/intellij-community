package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
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
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;

public class MergeRequestImpl extends MergeRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl");
  private final DiffContent[] myDiffContents = new DiffContent[3];
  private String myWindowTitle = null;
  private String[] myVersionTitles = null;
  private int myResult = DialogWrapper.CANCEL_EXIT_CODE;
  private String myHelpId;
  private final ActionButtonPresentation myActionButtonPresenation;

  public MergeRequestImpl(String left,
                          MergeVersion base,
                          String right,
                          Project project,
                          final ActionButtonPresentation actionButtonPresentation) {
    super(project);
    myActionButtonPresenation = actionButtonPresentation;
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
    if (result == DialogWrapper.OK_EXIT_CODE) applyChanges();
    myResult = result;
  }

  public void applyChanges() {
    getMergeContent().applyChanges();
  }

  public int getResult() { return myResult; }

  private MergeContent getMergeContent() { return (MergeContent)myDiffContents[1]; }

  public DiffContent getResultContent() { return getMergeContent(); }

  public void setActions(final DialogBuilder builder, MergePanel2 mergePanel, boolean initial) {
    if (builder.getOkAction() == null) {
      builder.addOkAction();
    }
    if (builder.getCancelAction() == null) {
      builder.addCancelAction();
    }

    (builder.getOkAction()).setText(myActionButtonPresenation.getName());

    builder.setOkActionEnabled(myActionButtonPresenation.isEnabled());
    final Action action = ((DialogBuilder.ActionDescriptor)builder.getOkAction()).getAction(builder.getDialogWrapper());
    String actionName = myActionButtonPresenation.getName();
    final int index = actionName.indexOf("&");
    final char mnemonic;
    if (index >= 0 && index < actionName.length() - 1) {
      mnemonic = actionName.charAt(index + 1);
      actionName = actionName.substring(0, index) + actionName.substring(index + 1);
    } else {
      mnemonic = 0;
    }
    action.putValue(Action.NAME, actionName);
    if (mnemonic > 0) {
      action.putValue(Action.MNEMONIC_KEY, new Integer(mnemonic));
    }
    builder.setOkOperation(new Runnable() {
      public void run() {
        myActionButtonPresenation.run((DiffViewer)DataManager.getInstance().getDataContext(builder.getCenterPanel()).getData(DataConstants.DIFF_VIEWER));
        if (myActionButtonPresenation.closeDialog()) {
          builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        }
      }
    });
    builder.setCancelOperation(new Runnable() {
      public void run() {
        if (Messages.showYesNoDialog(getProject(),
                                     DiffBundle.message("merge.dialog.exit.without.applying.changes.confirmation.message"),
                                     DiffBundle.message("cancel.visual.merge.dialog.title"),
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

  public void setHelpId(@NonNls String helpId) {
    myHelpId = helpId;
  }

  public VirtualFile getVirtualFile() {
    return getMergeContent().getFile();
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
      final ChangeCounter changeCounter = ChangeCounter.getOrCreate(myMergePanel.getMergeList());
      changeCounter.removeListener(this);
      changeCounter.addListener(this);
    }

    public void run() {
      if (!myActionButtonPresenation.closeDialog()) return;
      if (myWasInvoked) return;
      if (!getWholePanel().isDisplayable()) return;
      myWasInvoked = true;
      ChangeCounter.getOrCreate(myMergePanel.getMergeList()).removeListener(this);
      int doApply = Messages.showDialog(getProject(),
                                        DiffBundle.message("merge.all.changes.have.processed.save.and.finish.confirmation.text"),
                                        DiffBundle.message("all.changes.processed.dialog.title"),
                                        new String[]{DiffBundle.message("merge.save.and.finish.button"),
                                          DiffBundle.message("merge.continue.button")}, 0,
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
