package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class CommitToIcsDialog extends DialogWrapper {
  private final ChangesBrowser browser;

  public CommitToIcsDialog(Project project, List<Change> projectFileChanges) {
    super(project, true);

    browser = new ChangesBrowser(project, Collections.<ChangeList>emptyList(), projectFileChanges, null, true, false, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    browser.setChangesToDisplay(projectFileChanges);

    setTitle(IcsBundle.message("action.CommitToIcs.text"));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return browser;
  }
}
