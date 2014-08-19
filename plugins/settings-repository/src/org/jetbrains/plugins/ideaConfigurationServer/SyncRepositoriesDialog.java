package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;

@SuppressWarnings("DialogTitleCapitalization")
public class SyncRepositoriesDialog extends DialogWrapper {
  private final JComponent parent;
  private final Project project;

  private JRadioButton mergeRadioButton;
  private JRadioButton resetToTheirsRadioButton;
  private JRadioButton resetToYoursRadioButton;
  private JPanel panel;
  private ButtonGroup syncTypeGroup;

  public SyncRepositoriesDialog(@NotNull JComponent parent, @Nullable Project project) {
    super(parent, true);

    this.parent = parent;
    this.project = project;

    setTitle(IcsBundle.message("sync.repositories.panel.title"));
    setResizable(false);
    init();
  }

  @Override
  protected void init() {
    super.init();

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        setOKActionEnabled(syncTypeGroup.getSelection() != null);
      }
    };

    Enumeration<AbstractButton> elements = syncTypeGroup.getElements();
    while (elements.hasMoreElements()) {
      elements.nextElement().addActionListener(actionListener);
    }
    setOKActionEnabled(false);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return panel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    return super.doValidate();
  }

  @Override
  protected void doOKAction() {
    SyncType syncType = null;
    if (mergeRadioButton.isSelected()) {
      syncType = SyncType.MERGE;
    }
    else if (resetToTheirsRadioButton.isSelected()) {
      syncType = SyncType.REBASE_TO_THEIRS;
    }
    else if (resetToYoursRadioButton.isSelected()) {
      syncType = SyncType.REBASE_TO_YOURS;
    }

    super.doOKAction();

    if (syncType != null) {
      try {
        IcsManager.getInstance().sync(syncType, project);
      }
      catch (Exception e) {
        Messages.showErrorDialog(parent, IcsBundle.message("sync.rejected.message", StringUtil.notNullize(e.getMessage(), "Internal error")),
                                 IcsBundle.message("sync.rejected.title"));
      }

      Messages.showInfoMessage(parent, IcsBundle.message("sync.done.message"), IcsBundle.message("sync.done.title"));
    }
  }
}