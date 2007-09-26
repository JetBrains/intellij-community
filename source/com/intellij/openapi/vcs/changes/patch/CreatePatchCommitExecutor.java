/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchBuilder;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.Icons;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class CreatePatchCommitExecutor implements CommitExecutor, ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor");
  
  private final Project myProject;
  private final ChangeListManager myChangeListManager;

  public String PATCH_PATH = "";
  public boolean REVERSE_PATCH = false;

  public static CreatePatchCommitExecutor getInstance(Project project) {
    return project.getComponent(CreatePatchCommitExecutor.class);
  }

  public CreatePatchCommitExecutor(final Project project, final ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
  }

  @NotNull
  public Icon getActionIcon() {
    return Icons.TASK_ICON;
  }

  @Nls
  public String getActionText() {
    return VcsBundle.message("create.patch.commit.action.text");
  }

  @Nls
  public String getActionDescription() {
    return VcsBundle.message("create.patch.commit.action.description");
  }

  @NotNull
  public CommitSession createCommitSession() {
    return new CreatePatchCommitSession();
  }

  public void projectOpened() {
    myChangeListManager.registerCommitExecutor(this);
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "CreatePatchCommitExecutor";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private class CreatePatchCommitSession implements CommitSession {
    private final CreatePatchConfigurationPanel myPanel = new CreatePatchConfigurationPanel();

    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return myPanel.getPanel();
    }

    public JComponent getAdditionalConfigurationUI(final Collection<Change> changes, final String commitMessage) {
      if (PATCH_PATH == "") {
        PATCH_PATH = myProject.getBaseDir().getPresentableUrl();
      }
      myPanel.setFileName(ShelveChangesManager.suggestPatchName(commitMessage, new File(PATCH_PATH)));
      myPanel.setReversePatch(REVERSE_PATCH);
      return myPanel.getPanel();
    }

    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return true;
    }

    public void execute(Collection<Change> changes, String commitMessage) {
      int binaryCount = 0;
      for(Change change: changes) {
        if (change.getBeforeRevision() instanceof BinaryContentRevision ||
            change.getAfterRevision() instanceof BinaryContentRevision) {
          binaryCount++;
        }
      }
      if (binaryCount == changes.size()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showInfoMessage(myProject, VcsBundle.message("create.patch.all.binary"),
                                     VcsBundle.message("create.patch.commit.action.title"));
          }
        });
        return;
      }
      try {
        final String fileName = myPanel.getFileName();
        final File file = new File(fileName).getAbsoluteFile();
        PATCH_PATH = file.getParent();
        REVERSE_PATCH = myPanel.isReversePatch();
        Writer writer = new OutputStreamWriter(new FileOutputStream(fileName));
        try {
          List<FilePatch> patches = PatchBuilder.buildPatch(changes, myProject.getBaseDir().getPresentableUrl(), false, REVERSE_PATCH);
          final String lineSeparator = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().getLineSeparator();
          UnifiedDiffWriter.write(patches, writer, lineSeparator);
        }
        finally {
          writer.close();
        }
        if (binaryCount == 0) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showInfoMessage(myProject, VcsBundle.message("create.patch.success.confirmation", file.getPath()),
                                       VcsBundle.message("create.patch.commit.action.title"));
            }
          });
        }
        else {
          final int binaryCount1 = binaryCount;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showInfoMessage(myProject, VcsBundle.message("create.patch.partial.success.confirmation", file.getPath(),
                                                                    binaryCount1),
                                       VcsBundle.message("create.patch.commit.action.title"));
            }
          });
        }
      }
      catch (final Exception ex) {
        LOG.info(ex);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle());
          }
        });
      }
    }

    public void executionCanceled() {
    }
  }
}