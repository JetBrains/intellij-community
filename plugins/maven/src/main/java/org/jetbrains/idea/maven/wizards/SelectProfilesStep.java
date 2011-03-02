/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class SelectProfilesStep extends ProjectImportWizardStep {
  private JPanel panel;
  private ElementsChooser<String> profileChooser;

  public SelectProfilesStep(final WizardContext context) {
    super(context);
  }

  public boolean isStepVisible() {
    if (!super.isStepVisible()) {
      return false;
    }
    final MavenProjectBuilder importBuilder = getBuilder();
    if (importBuilder != null) {
      return !importBuilder.getProfiles().isEmpty();
    }
    return false;
  }

  protected MavenProjectBuilder getBuilder() {
    return (MavenProjectBuilder)super.getBuilder();
  }

  public void createUIComponents() {
    profileChooser = new ElementsChooser<String>(true);
  }

  public JComponent getComponent() {
    return panel;
  }

  public void updateStep() {
    List<String> allProfiles = getBuilder().getProfiles();
    List<String> markedProfiles = new ArrayList<String>(getBuilder().getSelectedProfiles());
    markedProfiles.retainAll(allProfiles); // mark only existing profiles

    profileChooser.setElements(allProfiles, false);
    profileChooser.markElements(markedProfiles);
  }

  public boolean validate() throws ConfigurationException {
    return getBuilder().setSelectedProfiles(profileChooser.getMarkedElements());
  }

  public void updateDataModel() {
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import.maven.page2";
  }
}
