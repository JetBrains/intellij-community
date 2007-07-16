package com.intellij.projectImport;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class SelectImportedProjectsStep<T> extends ProjectImportWizardStep {

  public interface Context<T> {
    List<T> getList();

    boolean isMarked(final T element);

    void setList(List<T> list) throws ValidationException;

    void setOpenProjectSettingsAfter(boolean on);

    String getName();

    class ValidationException extends Exception {
      public ValidationException (String message){
        super(message);
      }
    }
  }

  private Context<T> myContext;

  private final JPanel panel;
  protected final ElementsChooser<T> fileChooser;
  private final JCheckBox openModuleSettingsCheckBox;

  public SelectImportedProjectsStep(WizardContext context) {
    super(context);
    fileChooser = new ElementsChooser<T>(true) {
      protected String getItemText(T item) {
        return getElementText(item);
      }

      protected Icon getItemIcon(final T item) {
        return getElementIcon (item);
      }
    };

    panel = new JPanel(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));

    panel.add(fileChooser, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null));
    openModuleSettingsCheckBox = new JCheckBox(IdeBundle.message("project.import.show.settings.after"));
    panel.add(openModuleSettingsCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null));
  }

  @Nullable
  protected Icon getElementIcon(final T item) {
    return null;    
  }

  protected abstract String getElementText(final T item);

  public JComponent getComponent() {
    return panel;
  }

  public void updateStep() {
    fileChooser.clear();
    for (T element : getContext().getList()) {
      fileChooser.addElement(element, getContext().isMarked(element));
    }
    openModuleSettingsCheckBox.setSelected(getBuilder().isOpenProjectSettingsAfter());
  }

  public boolean validate() {
    try {
      fileChooser.setBorder(BorderFactory.createTitledBorder(IdeBundle.message("project.import.select.title", getContext().getName())));
      getContext().setList(fileChooser.getMarkedElements());
    }
    catch (Context.ValidationException e) {
      Messages.showErrorDialog(panel, e.getMessage(), IdeBundle.message("project.import.wizard.title", getContext().getName()));
      return false;
    }
    return fileChooser.getMarkedElements().size() != 0;
  }

  public void updateDataModel() {
    getContext().setOpenProjectSettingsAfter(openModuleSettingsCheckBox.isSelected());
  }

  public Context<T> getContext() {
    if (myContext == null) {
      myContext = (Context<T>)getWizardContext().getProjectBuilder();
    }
    return myContext;
  }
}

