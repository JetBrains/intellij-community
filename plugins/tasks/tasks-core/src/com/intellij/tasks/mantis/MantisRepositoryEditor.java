// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.mantis;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.impl.TaskUiUtil;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author Mikhail Golubev
 * @author evgeny.zakrevsky
 */
public class MantisRepositoryEditor extends BaseRepositoryEditor<MantisRepository> {
  private ComboBox<MantisProject> myProjectCombobox;
  private ComboBox<MantisFilter> myFilterCombobox;
  private JBLabel myProjectLabel;
  private JBLabel myFilterLabel;

  private boolean myInitialized = false;

  public MantisRepositoryEditor(Project project, MantisRepository repository, Consumer<? super MantisRepository> changeListener) {
    super(project, repository, changeListener);

    myTestButton.setText("Login");
    myTestButton.setEnabled(myRepository.isConfigured());

    // Populate filters list on project selection
    myProjectCombobox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          // equality check is needed to prevent resetting of combobox with filters
          // on initial projects update
          MantisProject project = (MantisProject)myProjectCombobox.getSelectedItem();
          if (project != null) {
            //noinspection unchecked
            myFilterCombobox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(project.getFilters())));
            if (!project.equals(myRepository.getCurrentProject())) {
              // unspecified filter should always be available
              myFilterCombobox.setSelectedIndex(0);
              doApply();
            }
            else if (!myInitialized) {
              // matters only on initialization
              myFilterCombobox.setSelectedItem(myRepository.getCurrentFilter());
              myInitialized = true;
            }
          }
        }
      }
    });
    installListener(myFilterCombobox);

    // Update the rest of projects in combobox, if repository is already configured
    if (myRepository.getCurrentProject() != null) {
      UIUtil.invokeLaterIfNeeded(() -> new FetchMantisProjects().queue());
    }
  }


  @Override
  public void apply() {
    super.apply();
    myRepository.setCurrentProject((MantisProject)myProjectCombobox.getSelectedItem());
    myRepository.setCurrentFilter((MantisFilter)myFilterCombobox.getSelectedItem());
    myTestButton.setEnabled(myRepository.isConfigured());
  }

  @Override
  protected void afterTestConnection(final boolean connectionSuccessful) {
    super.afterTestConnection(connectionSuccessful);
    if (connectionSuccessful) {
      new FetchMantisProjects().queue();
    }
    else {
      myProjectCombobox.removeAllItems();
      myFilterCombobox.removeAllItems();
    }
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myProjectLabel.setAnchor(anchor);
    myFilterLabel.setAnchor(anchor);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectLabel = new JBLabel("Project:", SwingConstants.RIGHT);
    myProjectCombobox = new ComboBox<>(200);
    myProjectCombobox.setRenderer(SimpleListCellRenderer.create("Login first", MantisProject::getName));
    myFilterLabel = new JBLabel("Filter:", SwingConstants.RIGHT);
    myFilterCombobox = new ComboBox<>(200);
    myFilterCombobox.setRenderer(SimpleListCellRenderer.create("Login first", MantisFilter::getName));
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myProjectLabel, myProjectCombobox)
      .addLabeledComponent(myFilterLabel, myFilterCombobox)
      .getPanel();
  }

  private final class FetchMantisProjects extends TaskUiUtil.ComboBoxUpdater<MantisProject> {
    private FetchMantisProjects() {
      super(MantisRepositoryEditor.this.myProject, "Downloading Mantis Projects...", myProjectCombobox);
    }

    @NotNull
    @Override
    protected List<MantisProject> fetch(@NotNull ProgressIndicator indicator) throws Exception {
      myRepository.refreshProjects();
      return myRepository.getProjects();
    }

    @Nullable
    @Override
    public MantisProject getSelectedItem() {
      return myRepository.getCurrentProject();
    }

    @Override
    protected void handleError() {
      super.handleError();
      myFilterCombobox.removeAllItems();
    }
  }
}
