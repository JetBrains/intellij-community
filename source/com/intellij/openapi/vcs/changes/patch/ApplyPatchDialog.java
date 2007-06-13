/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2006
 * Time: 17:09:11
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApplyPatchDialog extends DialogWrapper {
  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JLabel myStatusLabel;
  private TextFieldWithBrowseButton myBaseDirectoryField;
  private JSpinner myStripLeadingDirectoriesSpinner;
  private List<FilePatch> myPatches;
  private Alarm myLoadPatchAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private String myLoadPatchError = null;
  private String myDetectedBaseDirectory = null;
  private int myDetectedStripLeadingDirs = -1;
  private final Project myProject;

  public ApplyPatchDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("patch.apply.dialog.title"));
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getFileType() == StdFileTypes.PATCH || file.getFileType() == StdFileTypes.PLAIN_TEXT;
      }
    };
    myFileNameField.addBrowseFolderListener(VcsBundle.message("patch.apply.select.title"), "", project, descriptor);
    myFileNameField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateOKAction();
        myStatusLabel.setForeground(UIUtil.getLabelForeground());
        myStatusLabel.setText(VcsBundle.message("patch.load.progress"));
        myPatches = null;
        myLoadPatchAlarm.cancelAllRequests();
        myLoadPatchAlarm.addRequest(new Runnable() {
          public void run() {
            checkLoadPatches();
          }
        }, 400);
      }
    });

    myBaseDirectoryField.setText(project.getBaseDir().getPresentableUrl());
    myBaseDirectoryField.addBrowseFolderListener(VcsBundle.message("patch.apply.select.base.directory.title"), "", project,
                                                 new FileChooserDescriptor(false, true, false, false, false, false));

    myStripLeadingDirectoriesSpinner.setModel(new SpinnerNumberModel(0, 0, 256, 1));

    init();
    updateOKAction();
  }

  public void setFileName(String fileName) {
    myFileNameField.setText(fileName);
    checkLoadPatches();
  }

  private void checkLoadPatches() {
    final String fileName = myFileNameField.getText().replace(File.separatorChar, '/');
    final VirtualFile patchFile = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (patchFile == null) {
      queueUpdateStatus("Cannot find patch file");
      return;
    }
    myPatches = new ArrayList<FilePatch>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PatchReader reader;
        try {
          reader = new PatchReader(patchFile);
        }
        catch (IOException e) {
          queueUpdateStatus(VcsBundle.message("patch.apply.open.error", e.getMessage()));
          return;
        }
        while(true) {
          FilePatch patch;
          try {
            patch = reader.readNextPatch();
          }
          catch (PatchSyntaxException e) {
            if (e.getLine() >= 0) {
              queueUpdateStatus(VcsBundle.message("patch.apply.load.error.line", e.getMessage(), e.getLine()));
            }
            else {
              queueUpdateStatus(VcsBundle.message("patch.apply.load.error", e.getMessage()));
            }
            return;
          }
          if (patch == null) {
            break;
          }
          myPatches.add(patch);
        }
        if (myPatches.isEmpty()) {
          queueUpdateStatus(VcsBundle.message("patch.apply.no.patches.found"));
          return;
        }
        
        autoDetectBaseDirectory();
        queueUpdateStatus(null);
      }
    });
  }

  private void autoDetectBaseDirectory() {
    for(FilePatch patch: myPatches) {
      VirtualFile baseDir = myDetectedBaseDirectory == null
                            ? getBaseDirectory()
                            : LocalFileSystem.getInstance().findFileByPath(myDetectedBaseDirectory.replace(File.separatorChar, '/'));
      int skipTopDirs = myDetectedStripLeadingDirs >= 0 ? myDetectedStripLeadingDirs : 0;
      VirtualFile fileToPatch;
      try {
        fileToPatch = patch.findFileToPatch(new ApplyPatchContext(baseDir, skipTopDirs, false, false));
      }
      catch (IOException e) {
        continue;
      }
      if (fileToPatch == null) {
        String oldDetectedBaseDirectory = myDetectedBaseDirectory;
        int oldDetectedStripLeadingDirs = myDetectedStripLeadingDirs;
        boolean success = detectDirectoryByName(patch.getBeforeName());
        if (!success) {
          success = detectDirectoryByName(patch.getAfterName());
        }
        if (success) {
          if ((oldDetectedBaseDirectory != null && !Comparing.equal(oldDetectedBaseDirectory, myDetectedBaseDirectory)) ||
              (oldDetectedStripLeadingDirs >= 0 && oldDetectedStripLeadingDirs != myDetectedStripLeadingDirs)) {
            myDetectedBaseDirectory = null;
            myDetectedStripLeadingDirs = -1;
            break;
          }
        }
      }
    }
  }

  private boolean detectDirectoryByName(final String patchFileName) {
    String[] nameComponents = patchFileName.split("/");
    final String patchName = nameComponents[nameComponents.length - 1];
    final PsiFile[] psiFiles = PsiManager.getInstance(myProject).getShortNamesCache().getFilesByName(patchName);
    if (psiFiles.length == 1) {
      PsiDirectory parent = psiFiles [0].getContainingDirectory();
      for(int i=nameComponents.length-2; i >= 0; i--) {
        if (!parent.getName().equals(nameComponents [i]) || parent.getVirtualFile() == myProject.getBaseDir()) {
          myDetectedStripLeadingDirs = i+1;
          myDetectedBaseDirectory = parent.getVirtualFile().getPresentableUrl();
          return true;
        }
        parent = parent.getParentDirectory();
      }
      myDetectedStripLeadingDirs = 0;
      myDetectedBaseDirectory = parent.getVirtualFile().getPresentableUrl();
      return true;
    }
    return false;
  }

  private void queueUpdateStatus(final String s) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          queueUpdateStatus(s);
        }
      });
      return;
    }
    if (myDetectedBaseDirectory != null) {
      myBaseDirectoryField.setText(myDetectedBaseDirectory);
      myDetectedBaseDirectory = null;
    }
    if (myDetectedStripLeadingDirs != -1) {
      myStripLeadingDirectoriesSpinner.setValue(myDetectedStripLeadingDirs);
      myDetectedStripLeadingDirs = -1;
    }
    myLoadPatchError = s;
    if (s == null) {
      myStatusLabel.setForeground(UIUtil.getLabelForeground());
      myStatusLabel.setText(buildPatchSummary());
    }
    else {
      myStatusLabel.setText(s);
      myStatusLabel.setForeground(Color.red);
    }
    updateOKAction();
  }

  private String buildPatchSummary() {
    int newFiles = 0;
    int changedFiles = 0;
    int deletedFiles = 0;
    for(FilePatch patch: myPatches) {
      if (patch.isNewFile()) {
        newFiles++;
      }
      else if (patch.isDeletedFile()) {
        deletedFiles++;
      }
      else {
        changedFiles++;
      }
    }
    StringBuilder summaryBuilder = new StringBuilder("<html><body><b>Summary:</b> ");
    appendSummary(changedFiles, 0, summaryBuilder, "patch.summary.changed.files");
    appendSummary(newFiles, changedFiles, summaryBuilder, "patch.summary.new.files");
    appendSummary(deletedFiles, changedFiles + newFiles, summaryBuilder, "patch.summary.deleted.files");
    summaryBuilder.append("</body></html>");
    return summaryBuilder.toString();
  }

  private static void appendSummary(final int count, final int prevCount, final StringBuilder summaryBuilder,
                                    @PropertyKey(resourceBundle = "messages.VcsBundle") final String key) {
    if (count > 0) {
      if (prevCount > 0) {
        summaryBuilder.append(", ");
      }
      summaryBuilder.append(VcsBundle.message(key, count));
    }
  }

  @Override
  protected void dispose() {
    myLoadPatchAlarm.dispose();
    super.dispose();
  }

  private void updateOKAction() {
    setOKActionEnabled(myFileNameField.getText().length() > 0 && myLoadPatchError == null);
  }

  @Override
  protected void doOKAction() {
    if (myPatches == null) {
      myLoadPatchAlarm.cancelAllRequests();
      checkLoadPatches();
    }
    if (myLoadPatchError == null) {
      super.doOKAction();
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  public List<FilePatch> getPatches() {
    return myPatches;
  }

  public VirtualFile getBaseDirectory() {
    return LocalFileSystem.getInstance().findFileByPath(myBaseDirectoryField.getText().replace(File.separatorChar, '/'));
  }

  public int getStripLeadingDirectories() {
    return ((Integer) myStripLeadingDirectoriesSpinner.getValue()).intValue();
  }

  public ApplyPatchContext getApplyPatchContext() {
    return new ApplyPatchContext(getBaseDirectory(), getStripLeadingDirectories(), false, false);
  }
}