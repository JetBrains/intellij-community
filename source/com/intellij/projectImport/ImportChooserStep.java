/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.projectImport;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;

public class ImportChooserStep extends ProjectImportWizardStep {
  private final StepSequence mySequence;
  private JList myList;
  private JCheckBox myUpdateCurrentProject;
  private final JPanel myPanel;

  public ImportChooserStep(final ProjectImportProvider[] providers, final StepSequence sequence, final WizardContext context) {
    super(context);
    mySequence = sequence;
    myPanel = new JPanel(new BorderLayout());
    final DefaultListModel model = new DefaultListModel();
    myList = new JList(model);

    for (ProjectImportProvider provider : sorted(providers)) {
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
    myPanel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    final boolean selected = ProjectImportBuilder.getCurrentProject() != null;
    myUpdateCurrentProject = new JCheckBox(ProjectBundle.message("project.import.reuse.current.project.checkbox.name"), false);
    myUpdateCurrentProject.setVisible(selected);
    myPanel.add(myUpdateCurrentProject, BorderLayout.SOUTH);
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        mySequence.setType(((ProjectImportProvider)myList.getSelectedValue()).getId());
      }
    });
    myList.setSelectedIndex(0);
  }

  private static List<ProjectImportProvider> sorted(ProjectImportProvider[] providers) {
    List<ProjectImportProvider> result = new ArrayList<ProjectImportProvider>();
    Collections.addAll(result, providers);
    Collections.sort(result, new Comparator<ProjectImportProvider>() {
      public int compare(ProjectImportProvider l, ProjectImportProvider r) {
        return l.getName().compareToIgnoreCase(r.getName());
      }
    });
    return result;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
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

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import";
  }
}