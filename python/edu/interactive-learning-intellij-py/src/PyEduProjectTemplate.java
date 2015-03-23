import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.ui.StudyNewProjectPanel;
import com.jetbrains.python.facet.PythonSdkComboBox;
import com.jetbrains.python.module.PythonModuleBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class PyEduProjectTemplate extends EduProjectTemplate {
  private static final Logger LOG = Logger.getInstance(PyEduProjectTemplate.class);

  @NotNull
  @Override
  public String getName() {
    return "Python";
  }

  @Nullable
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
    return new PythonEduModuleBuilder();
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }

  private static class PythonEduModuleBuilder extends PythonModuleBuilder {

    final StudyProjectGenerator studyProjectGenerator = new StudyProjectGenerator();
    final PythonSdkComboBox mySdkComboBox = new PythonSdkComboBox();

    @Override
    public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
      super.setupRootModel(rootModel);
      Sdk sdk = mySdkComboBox.getSelectedSdk();
      if (sdk != null) {
        rootModel.setSdk(sdk);
      }
    }

    @Override
    protected List<WizardInputField> getAdditionalFields() {
      final StudyNewProjectPanel panel = new StudyNewProjectPanel(studyProjectGenerator);
      List<WizardInputField> wizardInputFields = new ArrayList<WizardInputField>();
      wizardInputFields.add(createWizardInputField("Edu.ProjectSdk", "Python Interpreter:", mySdkComboBox));
      wizardInputFields.add(createWizardInputField("Edu.Courses", "Courses:", panel.getCoursesComboBox()));
      wizardInputFields.add(createWizardInputField("Edu.InfoPanel", "", panel.getInfoPanel()));
      return wizardInputFields;
    }

    @Nullable
    @Override
    public Module commitModule(@NotNull final Project project, @Nullable ModifiableModuleModel model) {
      Module module = super.commitModule(project, model);
      if (module != null) {
        final VirtualFile baseDir = project.getBaseDir();
        studyProjectGenerator.generateProject(project, baseDir);
        final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper.py");

        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
            if (projectDir != null) {
              try {
                FileTemplateUtil.createFromTemplate(template, "test_helper.py", null, projectDir);
              }
              catch (Exception e) {
                LOG.error("Failed to create test_helper", e);
              }
            }
          }
        });
      }
      return module;
    }
  }
}