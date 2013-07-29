/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.jira;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.jira.jql.JqlFileType;
import com.intellij.tasks.jira.jql.JqlLanguage;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class JiraRepositoryEditor extends BaseRepositoryEditor<JiraRepository> {
  private EditorTextField mySearchQueryField;
  private JBLabel mySearchLabel;

  public JiraRepositoryEditor(Project project, JiraRepository repository, Consumer<JiraRepository> changeListener) {
    super(project, repository, changeListener);
  }

  @Override
  public void apply() {
    myRepository.setSearchQuery(mySearchQueryField.getText());
    super.apply();
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    mySearchLabel = new JBLabel("Search:", SwingConstants.RIGHT);
    PsiFileFactory fileFactory = PsiFileFactory.getInstance(myProject);
    PsiFile psiFile = fileFactory.createFileFromText("query.jql", JqlLanguage.INSTANCE, myRepository.getSearchQuery());
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    mySearchQueryField = new EditorTextField(document, myProject, JqlFileType.INSTANCE);
    installListener(document);
    return FormBuilder.createFormBuilder().addLabeledComponent(mySearchLabel, mySearchQueryField).getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    mySearchLabel.setAnchor(anchor);
  }
}
