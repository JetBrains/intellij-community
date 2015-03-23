import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.platform.ProjectTemplate;

import javax.swing.*;

public abstract class EduProjectTemplate implements ProjectTemplate {
  public static final ExtensionPointName<EduProjectTemplate> EP_NAME = ExtensionPointName.create("com.jetbrains.edu.interactivelearning.intellij.EduProjectTemplate");

  public static WizardInputField createWizardInputField(String id, final String label, final JComponent component) {
    return new WizardInputField(id, id) {
      @Override
      public String getLabel() {
        return label;
      }

      @Override
      public JComponent getComponent() {
        return component;
      }

      @Override
      public String getValue() {
        return null;
      }
    };
  }
}
