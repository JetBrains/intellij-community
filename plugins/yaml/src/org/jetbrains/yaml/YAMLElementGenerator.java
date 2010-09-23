package org.jetbrains.yaml;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

/**
 * @author traff
 */
public class YAMLElementGenerator {
  private final Project myProject;

  public YAMLElementGenerator(Project project) {
    myProject = project;
  }

  public static YAMLElementGenerator getInstance(Project project) {
    return ServiceManager.getService(project, YAMLElementGenerator.class);
  }

  public YAMLKeyValueImpl createYamlKeyValue(String name) {
    final PsiFile tempFile = createTempFile("script: " + name);
    return PsiTreeUtil.collectElementsOfType(tempFile, YAMLKeyValueImpl.class).iterator().next();
  }

  public PsiFile createTempFile(String contents) {
    return PsiFileFactory.getInstance(myProject).createFileFromText("app." + YAMLFileType.YML.getDefaultExtension(),
                                                                    YAMLFileType.YML, contents);
  }
}
