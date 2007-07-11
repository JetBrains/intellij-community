/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooserPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.CollectionListModel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author yole
 */
public class ApplyPatchDialog extends DialogWrapper {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchDialog");

  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JLabel myStatusLabel;
  private TextFieldWithBrowseButton myBaseDirectoryField;
  private JSpinner myStripLeadingDirectoriesSpinner;
  private JList myPatchContentsList;
  private ChangeListChooserPanel myChangeListChooser;
  private List<FilePatch> myPatches;
  private Collection<FilePatch> myPatchesFailedToLoad;
  private final Alarm myLoadPatchAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final Alarm myVerifyPatchAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private String myLoadPatchError = null;
  private String myDetectedBaseDirectory = null;
  private int myDetectedStripLeadingDirs = -1;
  private final Project myProject;
  private boolean myInnerChange;
  private LocalChangeList mySelectedChangeList;

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
    myBaseDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myInnerChange) {
          queueVerifyPatchPaths();
        }
      }
    });

    myStripLeadingDirectoriesSpinner.setModel(new SpinnerNumberModel(0, 0, 256, 1));
    myStripLeadingDirectoriesSpinner.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        if (!myInnerChange) {
          queueVerifyPatchPaths();
        }
      }
    });

    myPatchContentsList.setCellRenderer(new PatchCellRendererPanel());

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeListChooser.setChangeLists(changeListManager.getChangeLists());
    myChangeListChooser.setDefaultSelection(changeListManager.getDefaultChangeList());

    init();
    updateOKAction();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "vcs.ApplyPatchDialog";
  }

  private void queueVerifyPatchPaths() {
    myStatusLabel.setForeground(UIUtil.getLabelForeground());
    myStatusLabel.setText(VcsBundle.message("apply.patch.progress.verifying"));
    myVerifyPatchAlarm.cancelAllRequests();
    myVerifyPatchAlarm.addRequest(new Runnable() {
      public void run() {
        try {
          if (myPatches != null) {
            verifyPatchPaths();
          }
        }
        catch(Exception ex) {
          LOG.error(ex);
        }
      }
    }, 400);
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
    myPatchesFailedToLoad = new HashSet<FilePatch>();
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
    boolean autodetectFailed = false;
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
        myPatchesFailedToLoad.add(patch);
        continue;
      }
      if (fileToPatch == null) {
        boolean success = false;
        if (!autodetectFailed) {
          String oldDetectedBaseDirectory = myDetectedBaseDirectory;
          int oldDetectedStripLeadingDirs = myDetectedStripLeadingDirs;
          success = detectDirectoryByName(patch.getBeforeName());
          if (!success) {
            success = detectDirectoryByName(patch.getAfterName());
          }
          if (success) {
            if ((oldDetectedBaseDirectory != null && !Comparing.equal(oldDetectedBaseDirectory, myDetectedBaseDirectory)) ||
                (oldDetectedStripLeadingDirs >= 0 && oldDetectedStripLeadingDirs != myDetectedStripLeadingDirs)) {
              myDetectedBaseDirectory = null;
              myDetectedStripLeadingDirs = -1;
              autodetectFailed = true;
            }
          }
        }
        if (!success) {
          myPatchesFailedToLoad.add(patch);
        }
      }
    }
  }

  private Collection<String> verifyPatchPaths() {
    final ApplyPatchContext context = getApplyPatchContext();
    myPatchesFailedToLoad.clear();
    for(FilePatch patch: myPatches) {
      try {
        if (context.getBaseDir() == null || patch.findFileToPatch(context) == null) {
          myPatchesFailedToLoad.add(patch);
        }
      }
      catch (IOException e) {
        myPatchesFailedToLoad.add(patch);
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myPatchContentsList.repaint();
        myStatusLabel.setText("");
      }
    });
    return context.getMissingDirectories();
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
    myInnerChange = true;
    try {
      if (myDetectedBaseDirectory != null) {
        myBaseDirectoryField.setText(myDetectedBaseDirectory);
        myDetectedBaseDirectory = null;
      }
      if (myDetectedStripLeadingDirs != -1) {
        myStripLeadingDirectoriesSpinner.setValue(myDetectedStripLeadingDirs);
        myDetectedStripLeadingDirs = -1;
      }
    }
    finally {
      myInnerChange = false;
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
    updatePatchTableModel();
    updateOKAction();
  }

  private void updatePatchTableModel() {
    if (myPatches != null) {
      myPatchContentsList.setModel(new CollectionListModel(myPatches));
    }
    else {
      myPatchContentsList.setModel(new DefaultListModel());
    }
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
    StringBuilder summaryBuilder = new StringBuilder("<html><body><b>").append(VcsBundle.message("apply.patch.summary.title")).append("</b> ");
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
    myVerifyPatchAlarm.dispose();
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
      mySelectedChangeList = myChangeListChooser.getSelectedList(myProject);
      if (mySelectedChangeList == null) return;
      final Collection<String> missingDirs = verifyPatchPaths();
      if (missingDirs.size() > 0 && !checkCreateMissingDirs(missingDirs)) return;
      super.doOKAction();
    }
  }

  private boolean checkCreateMissingDirs(final Collection<String> missingDirs) {
    StringBuilder messageBuilder = new StringBuilder(VcsBundle.message("apply.patch.create.dirs.prompt.header"));
    for(String missingDir: missingDirs) {
      messageBuilder.append(missingDir).append("\r\n");
    }
    messageBuilder.append(VcsBundle.message("apply.patch.create.dirs.prompt.footer"));
    int rc = Messages.showYesNoCancelDialog(myProject, messageBuilder.toString(), VcsBundle.message("patch.apply.dialog.title"),
                                            Messages.getQuestionIcon());
    if (rc == 0) {
      for(String dir: missingDirs) {
        new File(dir).mkdirs();
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for(String dir: missingDirs) {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(dir);
          }
        }
      });
    }
    else if (rc != 1) {
      return false;
    }
    return true;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  public List<FilePatch> getPatches() {
    return myPatches;
  }

  private VirtualFile getBaseDirectory() {
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(myBaseDirectoryField.getText()));
  }

  private int getStripLeadingDirectories() {
    return ((Integer) myStripLeadingDirectoriesSpinner.getValue()).intValue();
  }

  public ApplyPatchContext getApplyPatchContext() {
    return new ApplyPatchContext(getBaseDirectory(), getStripLeadingDirectories(), false, false);
  }

  public LocalChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  private static String getChangeType(final FilePatch filePatch) {
    if (filePatch.isNewFile()) return VcsBundle.message("change.type.new");
    if (filePatch.isDeletedFile()) return VcsBundle.message("change.type.deleted");
    return VcsBundle.message("change.type.modified");
  }

  private class PatchCellRendererPanel extends JPanel implements ListCellRenderer {
    private PatchCellRenderer myRenderer;
    private JLabel myFileTypeLabel;

    public PatchCellRendererPanel() {
      super(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      myRenderer = new PatchCellRenderer();
      add(myRenderer, BorderLayout.CENTER);
      myFileTypeLabel = new JLabel();
      myFileTypeLabel.setHorizontalAlignment(JLabel.RIGHT);
      add(myFileTypeLabel, BorderLayout.EAST);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      FilePatch patch = (FilePatch) value;
      myRenderer.getListCellRendererComponent(list, value, index, isSelected, false);
      myFileTypeLabel.setText("(" + getChangeType(patch) + ")");
      if (isSelected) {
        setBackground(UIUtil.getListSelectionBackground());
        setForeground(UIUtil.getListSelectionForeground());
        myFileTypeLabel.setForeground(UIUtil.getListSelectionForeground());
      }
      else {
        setBackground(UIUtil.getListBackground());
        setForeground(UIUtil.getListForeground());        
        myFileTypeLabel.setForeground(Color.gray);
      }
      return this;
    }
  }

  private class PatchCellRenderer extends ColoredListCellRenderer {
    private SimpleTextAttributes myNewAttributes = new SimpleTextAttributes(0, FileStatus.ADDED.getColor());
    private SimpleTextAttributes myDeletedAttributes = new SimpleTextAttributes(0, FileStatus.DELETED.getColor());
    private SimpleTextAttributes myModifiedAttributes = new SimpleTextAttributes(0, FileStatus.MODIFIED.getColor());

    private boolean assumeProblemWillBeFixed(final FilePatch filePatch) {
      // if some of the files are valid, assume that "red" new files will be fixed by creating directories
      return (filePatch.isNewFile() && myPatchesFailedToLoad.size() != myPatches.size());
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      FilePatch filePatch = (FilePatch) value;
      String name = filePatch.getAfterNameRelative(getStripLeadingDirectories());

      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
      setIcon(fileType.getIcon());

      if (myPatchesFailedToLoad.contains(filePatch) && !assumeProblemWillBeFixed(filePatch)) {
        append(name, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (filePatch.isNewFile()) {
        append(name, myNewAttributes);
      }
      else if (filePatch.isDeletedFile()) {
        append(name, myDeletedAttributes);
      }
      else {
        append(name, myModifiedAttributes);
      }
    }
  }

}