/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PresentationSettingsPanel implements ConfigurableUi<SvnConfiguration> {

  @NotNull private final Project myProject;

  private JPanel myMainPanel;

  private JCheckBox myCheckNestedInQuickMerge;
  private JCheckBox myIgnoreWhitespaceDifferenciesInCheckBox;
  private JCheckBox myShowMergeSourceInAnnotate;

  private JCheckBox myMaximumNumberOfRevisionsCheckBox;
  private JSpinner myNumRevsInAnnotations;

  public PresentationSettingsPanel(@NotNull Project project) {
    myProject = project;

    myMaximumNumberOfRevisionsCheckBox
      .addActionListener(e -> myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected()));
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void reset(@NotNull SvnConfiguration configuration) {
    myCheckNestedInQuickMerge.setSelected(configuration.isCheckNestedForQuickMerge());
    myIgnoreWhitespaceDifferenciesInCheckBox.setSelected(configuration.isIgnoreSpacesInAnnotate());
    myShowMergeSourceInAnnotate.setSelected(configuration.isShowMergeSourcesInAnnotate());
    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    if (annotateRevisions == -1) {
      myMaximumNumberOfRevisionsCheckBox.setSelected(false);
      myNumRevsInAnnotations.setValue(SvnConfiguration.ourMaxAnnotateRevisionsDefault);
    }
    else {
      myMaximumNumberOfRevisionsCheckBox.setSelected(true);
      myNumRevsInAnnotations.setValue(annotateRevisions);
    }
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
  }

  @Override
  public boolean isModified(@NotNull SvnConfiguration configuration) {
    if (configuration.isCheckNestedForQuickMerge() != myCheckNestedInQuickMerge.isSelected()) {
      return true;
    }
    if (configuration.isIgnoreSpacesInAnnotate() != myIgnoreWhitespaceDifferenciesInCheckBox.isSelected()) {
      return true;
    }
    if (configuration.isShowMergeSourcesInAnnotate() != myShowMergeSourceInAnnotate.isSelected()) {
      return true;
    }
    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    final boolean useMaxInAnnot = annotateRevisions != -1;
    if (useMaxInAnnot != myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      return true;
    }
    if (myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      if (annotateRevisions != ((SpinnerNumberModel)myNumRevsInAnnotations.getModel()).getNumber().intValue()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply(@NotNull SvnConfiguration configuration) {
    configuration.setCheckNestedForQuickMerge(myCheckNestedInQuickMerge.isSelected());
    configuration.setIgnoreSpacesInAnnotate(myIgnoreWhitespaceDifferenciesInCheckBox.isSelected());
    configuration.setShowMergeSourcesInAnnotate(myShowMergeSourceInAnnotate.isSelected());
    if (!myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      configuration.setMaxAnnotateRevisions(-1);
    }
    else {
      configuration.setMaxAnnotateRevisions(((SpinnerNumberModel)myNumRevsInAnnotations.getModel()).getNumber().intValue());
    }
  }

  private void createUIComponents() {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    int value = configuration.getMaxAnnotateRevisions();
    value = (value == -1) ? SvnConfiguration.ourMaxAnnotateRevisionsDefault : value;
    myNumRevsInAnnotations = new JSpinner(new SpinnerNumberModel(value, 10, 100000, 100));
  }
}
