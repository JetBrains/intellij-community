/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.merge;

import com.intellij.CommonBundle;
import com.intellij.peer.PeerFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.ui.table.TableView;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

/**
 * @author yole
 */
public class MultipleFileMergeDialog extends DialogWrapper {
  private JPanel myRootPanel;
  private JButton myAcceptYoursButton;
  private JButton myAcceptTheirsButton;
  private JButton myMergeButton;
  private TableView<VirtualFile> myTable;
  private final MergeProvider myProvider;
  private final List<VirtualFile> myFiles;
  private final ListTableModel<VirtualFile> myModel;
  private Project myProject;
  private ProjectManagerEx myProjectManager;

  private VirtualFileRenderer myVirtualFileRenderer = new VirtualFileRenderer();

  private ColumnInfo<VirtualFile, VirtualFile> NAME_COLUMN = new ColumnInfo<VirtualFile, VirtualFile>(VcsBundle.message("multiple.file.merge.column.name")) {
    public VirtualFile valueOf(final VirtualFile virtualFile) {
      return virtualFile;
    }

    @Override
    public TableCellRenderer getRenderer(final VirtualFile virtualFile) {
      return myVirtualFileRenderer;
    }
  };

  private ColumnInfo<VirtualFile, String> TYPE_COLUMN = new ColumnInfo<VirtualFile, String>(VcsBundle.message("multiple.file.merge.column.type")) {
    public String valueOf(final VirtualFile virtualFile) {
      return virtualFile.getFileType().isBinary()
             ? VcsBundle.message("multiple.file.merge.type.binary")
             : VcsBundle.message("multiple.file.merge.type.text");
    }

    @Override
    public String getMaxStringValue() {
      return VcsBundle.message("multiple.file.merge.type.binary");
    }

    @Override
    public int getAdditionalWidth() {
      return 10;
    }
  };

  public MultipleFileMergeDialog(Project project, final List<VirtualFile> files, final MergeProvider provider) {
    super(project, false);
    myProject = project;
    myProjectManager = ProjectManagerEx.getInstanceEx();
    myProjectManager.blockReloadingProjectOnExternalChanges();
    myFiles = new ArrayList<VirtualFile>(files);
    myProvider = provider;
    myModel = new ListTableModel<VirtualFile>(NAME_COLUMN, TYPE_COLUMN);
    myModel.setItems(files);
    myTable.setModel(myModel);
    myVirtualFileRenderer.setFont(UIUtil.getListFont());
    myTable.setRowHeight(myVirtualFileRenderer.getPreferredSize().height);
    setTitle(VcsBundle.message("multiple.file.merge.title"));
    init();
    myAcceptYoursButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        acceptRevision(true);
      }
    });
    myAcceptTheirsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        acceptRevision(false);
      }
    });
    myMergeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showMergeDialog();
      }
    });
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtonState();
      }
    });
    myTable.getSelectionModel().setSelectionInterval(0, 0);
  }

  private void updateButtonState() {
    boolean haveSelection = myTable.getSelectedRowCount() > 0;
    boolean haveBinaryFiles = false;
    for(VirtualFile file: myTable.getSelection()) {
      if (file.getFileType().isBinary()) {
        haveBinaryFiles = true;
        break;
      }
    }
    myAcceptYoursButton.setEnabled(haveSelection);
    myAcceptTheirsButton.setEnabled(haveSelection);
    myMergeButton.setEnabled(haveSelection && !haveBinaryFiles);
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  protected Action[] createActions() {
    return new Action[] { getCancelAction() };
  }

  @Override
  protected Action getCancelAction() {
    Action action = super.getCancelAction();
    action.putValue(Action.NAME, CommonBundle.getCloseButtonText());
    return action;
  }

  @Override
  protected void dispose() {
    myProjectManager.unblockReloadingProjectOnExternalChanges();
    super.dispose();
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "MultipleFileMergeDialog";
  }

  private void acceptRevision(final boolean isCurrent) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final Collection<VirtualFile> files = myTable.getSelection();
    for(final VirtualFile file: files) {
      final Ref<Exception> ex = new Ref<Exception>();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            MergeData data = myProvider.loadRevisions(file);
            if (isCurrent) {
              file.setBinaryContent(data.CURRENT);
            }
            else {
              file.setBinaryContent(data.LAST);
              checkMarkModifiedProject(file);
            }
            myProvider.conflictResolvedForFile(file);
          }
          catch (Exception e) {
            ex.set(e);
          }
        }
      });
      if (!ex.isNull()) {
        Messages.showErrorDialog(myRootPanel, "Error saving merged data: " + ex.get().getMessage());
        break;
      }

      myFiles.remove(file);
    }
    updateModelFromFiles();
  }

  private void updateModelFromFiles() {
    if (myFiles.size() == 0) {
      doCancelAction();
    }
    else {
      int selIndex = myTable.getSelectionModel().getMinSelectionIndex();
      myModel.setItems(myFiles);
      if (selIndex >= myFiles.size()) {
        selIndex = myFiles.size()-1;
      }
      myTable.getSelectionModel().setSelectionInterval(selIndex, selIndex);
    }
  }

  private void showMergeDialog() {
    final Collection<VirtualFile> files = myTable.getSelection();
    for(final VirtualFile file: files) {
      final Ref<MergeData> mergeDataRef = new Ref<MergeData>();
      final Ref<VcsException> exRef = new Ref<VcsException>();
      Task task = new Task.Modal(myProject, VcsBundle.message("multiple.file.merge.loading.progress.title"), false) {
        public void run(ProgressIndicator indicator) {
          try {
            mergeDataRef.set(myProvider.loadRevisions(file));
          }
          catch (VcsException e) {
            exRef.set(e);
          }
        }
      };
      ProgressManager.getInstance().run(task);
      if (!exRef.isNull()) {
        Messages.showErrorDialog(myRootPanel, "Error loading revisions to merge: " + exRef.get().getMessage());
        break;
      }

      final MergeData mergeData = mergeDataRef.get();
      if (mergeData.CURRENT == null || mergeData.LAST == null || mergeData.ORIGINAL == null) {
        Messages.showErrorDialog(myRootPanel, "Error loading revisions to merge");
        break;
      }

      final Document document = FileDocumentManager.getInstance().getDocument(file);
      final String contentWithMergeMarkers = document == null ? null : document.getText();

      String leftText = decodeContent(file, mergeData.CURRENT);
      String rightText = decodeContent(file, mergeData.LAST);
      String originalText = decodeContent(file, mergeData.ORIGINAL);

      DiffRequestFactory diffRequestFactory = PeerFactory.getInstance().getDiffRequestFactory();
      MergeRequest request = diffRequestFactory.createMergeRequest(leftText, rightText, originalText, file, myProject,
                                                                   ActionButtonPresentation.createApplyButton());
      String lastVersionTitle;
      if (mergeData.LAST_REVISION_NUMBER != null) {
        lastVersionTitle = VcsBundle.message("merge.version.title.last.version.number", mergeData.LAST_REVISION_NUMBER.asString());
      }
      else {
        lastVersionTitle = VcsBundle.message("merge.version.title.last.version");
      }
      request.setVersionTitles(new String[]{VcsBundle.message("merge.version.title.local.changes"),
        VcsBundle.message("merge.version.title.merge.result"),
        lastVersionTitle});
      request.setWindowTitle(VcsBundle.message("multiple.file.merge.request.title"));
      DiffManager.getInstance().getDiffTool().show(request);
      if (request.getResult() == DialogWrapper.OK_EXIT_CODE) {
        myFiles.remove(file);
        myProvider.conflictResolvedForFile(file);
        checkMarkModifiedProject(file);
      }
      else {
        restoreOriginalContent(file, contentWithMergeMarkers);
      }
    }
    updateModelFromFiles();
  }

  private void checkMarkModifiedProject(final VirtualFile file) {
    if (file.getFileType() == StdFileTypes.IDEA_MODULE ||
        file.getFileType() == StdFileTypes.IDEA_PROJECT ||
        file.getFileType() == StdFileTypes.IDEA_WORKSPACE) {
      myProjectManager.saveChangedProjectFile(file);
    }
  }

  private void restoreOriginalContent(final VirtualFile file, final String contentWithMergeMarkers) {
    CommandProcessor.getInstance().executeCommand(
      myProject,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              FileDocumentManager.getInstance().getDocument(file).setText(contentWithMergeMarkers);
            }
          });
        }
      }, "", null);
  }

  private static String decodeContent(final VirtualFile file, final byte[] content) {
    return StringUtil.convertLineSeparators(file.getCharset().decode(ByteBuffer.wrap(content)).toString());
  }

  private static class VirtualFileRenderer extends ColoredTableCellRenderer {
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      VirtualFile vf = (VirtualFile) value;
      setIcon(vf.getIcon());
      append(FileUtil.toSystemDependentName(vf.getPresentableUrl()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
