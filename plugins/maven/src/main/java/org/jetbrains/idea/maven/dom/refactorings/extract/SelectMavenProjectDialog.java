// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.refactorings.extract;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Set;

public class SelectMavenProjectDialog extends DialogWrapper {
  private final Set<? extends MavenDomProjectModel> myMavenDomProjectModels;
  private final boolean myHasExclusions;

  private JComboBox myMavenProjectsComboBox;
  private JPanel myMainPanel;
  private JCheckBox myReplaceAllCheckBox;
  private JCheckBox myExtractExclusions;
  private boolean myHasUsagesInProjects = false;

  private ItemListener myReplaceAllListener;
  private final Function<? super MavenDomProjectModel, ? extends Set<MavenDomDependency>> myOccurrencesCountFunction;

  public SelectMavenProjectDialog(@NotNull Project project,
                                  @NotNull Set<? extends MavenDomProjectModel> mavenDomProjectModels,
                                  @NotNull Function<? super MavenDomProjectModel, ? extends Set<MavenDomDependency>> funOccurrences,
                                  boolean hasExclusions) {
    super(project, true);
    myMavenDomProjectModels = mavenDomProjectModels;
    myHasExclusions = hasExclusions;

    setTitle(MavenDomBundle.message("choose.project"));

    myOccurrencesCountFunction = funOccurrences;
    for (MavenDomProjectModel model : myMavenDomProjectModels) {
      if (myOccurrencesCountFunction.fun(model).size() > 0) {
        myHasUsagesInProjects = true;
        break;
      }
    }

    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected void init() {
    super.init();

    updateOkStatus();
  }

  @Override
  protected void dispose() {
    super.dispose();
    if (myReplaceAllCheckBox != null) {
      myReplaceAllCheckBox.removeItemListener(myReplaceAllListener);
    }
  }

  public @Nullable MavenDomProjectModel getSelectedProject() {
    return (MavenDomProjectModel)ComboBoxUtil.getSelectedValue((DefaultComboBoxModel)myMavenProjectsComboBox.getModel());
  }

  public boolean isReplaceAllOccurrences() {
    return myReplaceAllCheckBox.isSelected();
  }

  public boolean isExtractExclusions() {
    return myExtractExclusions.isSelected();
  }

  @Override
  protected JComponent createCenterPanel() {
    ComboBoxUtil.setModel(myMavenProjectsComboBox, new DefaultComboBoxModel(), myMavenDomProjectModels,
                          model -> Pair.create(MavenDomUtil.getProjectName(model), model));

    myReplaceAllListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateControls();
      }
    };

    myMavenProjectsComboBox.addItemListener(myReplaceAllListener);
    myMavenProjectsComboBox.setSelectedItem(0);
    myReplaceAllCheckBox.setVisible(myHasUsagesInProjects);
    myExtractExclusions.setVisible(myHasExclusions);

    updateControls();

    return myMainPanel;
  }

  private void updateControls() {
    MavenDomProjectModel project = getSelectedProject();
    int count = myOccurrencesCountFunction.fun(project).size();
    myReplaceAllCheckBox.setText(RefactoringBundle.message("replace.all.occurences", count));

    myReplaceAllCheckBox.setEnabled(count != 0);
  }

  private void updateOkStatus() {
    setOKActionEnabled(getSelectedProject() != null);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMavenProjectsComboBox;
  }
}