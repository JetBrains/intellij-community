import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EduProjectTemplateFactory extends ProjectTemplatesFactory {

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[] {"Edu"};
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(@Nullable String group, WizardContext context) {
    return EduProjectTemplate.EP_NAME.getExtensions();
  }
}