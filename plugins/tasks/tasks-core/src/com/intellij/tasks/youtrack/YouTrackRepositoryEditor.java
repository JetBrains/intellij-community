package com.intellij.tasks.youtrack;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.youtrack.lang.YouTrackLanguage;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class YouTrackRepositoryEditor extends BaseRepositoryEditor<YouTrackRepository> {
  private static final Logger LOG = Logger.getInstance(YouTrackRepository.class);

  private EditorTextField myDefaultSearch;
  private JBLabel mySearchLabel;

  public YouTrackRepositoryEditor(final Project project, final YouTrackRepository repository, Consumer<YouTrackRepository> changeListener) {
    super(project, repository, changeListener);

    // Setup document for completion and highlighting
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDefaultSearch.getDocument());
    assert file != null;
    file.putUserData(YouTrackIntellisense.INTELLISENSE_KEY, new YouTrackIntellisense(myRepository));
  }

  @Override
  protected void afterTestConnection(boolean connectionSuccessful) {
    super.afterTestConnection(connectionSuccessful);
    // highlight query if connection was successful
    if (connectionSuccessful) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
    }
  }

  @Override
  public void apply() {
    myRepository.setDefaultSearch(myDefaultSearch.getText());
    super.apply();
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    mySearchLabel = new JBLabel("Search:", SwingConstants.RIGHT);
    myDefaultSearch = new LanguageTextField(YouTrackLanguage.INSTANCE, myProject, myRepository.getDefaultSearch());
    installListener(myDefaultSearch);
    return FormBuilder.createFormBuilder().addLabeledComponent(mySearchLabel, myDefaultSearch).getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    mySearchLabel.setAnchor(anchor);
  }
}
