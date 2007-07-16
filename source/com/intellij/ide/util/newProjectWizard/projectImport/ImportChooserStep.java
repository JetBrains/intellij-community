/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.projectImport;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;

public class ImportChooserStep extends ProjectImportWizardStep {
  private final ProjectImportProvider[] myProviders;
  private final StepSequence mySequence;
  private JList myList;
  private JCheckBox myUpdateCurrentProject;

  public ImportChooserStep(final ProjectImportProvider[] providers, final StepSequence sequence, final WizardContext context) {
    super(context);
    myProviders = providers;
    mySequence = sequence;
  }

  public JComponent getComponent() {
    final JPanel panel = new JPanel(new BorderLayout());
    final DefaultListModel model = new DefaultListModel();

    myList = new JList(model);
    for (ProjectImportProvider provider : myProviders) {
      model.addElement(provider);
    }
    myList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(((ProjectImportProvider)value).getName());
        setIcon(((ProjectImportProvider)value).getIcon());
        return rendererComponent;
      }
    });
    myList.setSelectedIndex(0);
    panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    final boolean selected = ProjectImportBuilder.getCurrentProject() != null;
    myUpdateCurrentProject = new JCheckBox(ProjectBundle.message("project.import.reuse.current.project.checkbox.name"), selected);
    myUpdateCurrentProject.setVisible(selected);
    panel.add(myUpdateCurrentProject, BorderLayout.SOUTH);
    return panel;
  }

  public void updateDataModel() {
    final Object selectedValue = myList.getSelectedValue();
    if (selectedValue instanceof ProjectImportProvider) {
      mySequence.setType(((ProjectImportProvider)selectedValue).getId());
      final ProjectImportBuilder builder = ((ProjectImportProvider)selectedValue).getBuilder();
      getWizardContext().setProjectBuilder(builder);
      builder.setUpdate(myUpdateCurrentProject.isSelected());
    }
  }

  public void updateStep() {
    updateDataModel();
    super.updateStep();
  }
}