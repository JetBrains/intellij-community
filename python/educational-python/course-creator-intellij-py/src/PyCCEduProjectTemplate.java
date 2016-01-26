import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.PyCCProjectGenerator;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import com.jetbrains.python.facet.PythonSdkComboBox;
import com.jetbrains.python.module.PythonModuleBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class PyCCEduProjectTemplate extends EduProjectTemplate {

  @NotNull
  @Override
  public String getName() {
    return "Python Course Creation";
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @NotNull
  @Override
  public AbstractModuleBuilder createModuleBuilder() {
    return new PythonEduModuleBuilder() ;
  }

  @Override
  public ValidationInfo validateSettings() {
    return null;
  }

  private static class PythonEduModuleBuilder extends PythonModuleBuilder {
    @Override
    public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
      super.setupRootModel(rootModel);
      Sdk sdk = mySdkComboBox.getSelectedSdk();
      if (sdk != null) {
        rootModel.setSdk(sdk);
      }
    }

    final PythonSdkComboBox mySdkComboBox = new PythonSdkComboBox();
    final CCNewProjectPanel panel = new CCNewProjectPanel();


    @Override
    protected List<WizardInputField> getAdditionalFields() {
      List<WizardInputField> wizardInputFields = new ArrayList<WizardInputField>();
      wizardInputFields.add(createWizardInputField("Edu.CCProjectSdk", "Python Interpreter:", mySdkComboBox));
      wizardInputFields.add(createWizardInputField("Edu.Author", "Author:", panel.getAuthorField()));
      wizardInputFields.add(createWizardInputField("Edu.Name", "Name:", panel.getNameField()));
      wizardInputFields.add(createWizardInputField("Edu.Description", "Description:", panel.getDescriptionField()));
      return wizardInputFields;
    }

    @Nullable
    @Override
    public Module commitModule(@NotNull final Project project, @Nullable ModifiableModuleModel model) {
      Module module = super.commitModule(project, model);
      if (module != null) {
        final VirtualFile baseDir = project.getBaseDir();
        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            PyCCProjectGenerator.generateProject(project, baseDir, panel.getName(), panel.getAuthors(), panel.getDescription());
          }
        });
      }
      return module;
    }
  }
}
