// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PresentationSettingsPanel implements ConfigurableUi<SvnConfiguration> {

  private final @NotNull Project myProject;

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

  @Override
  public @NotNull JComponent getComponent() {
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
