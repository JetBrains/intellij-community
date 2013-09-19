package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenJdkCombobBox extends JComboBox {

  @Nullable
  private final Project myProject;

  public MavenJdkCombobBox(@Nullable Project project) {
    myProject = project;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public void refreshData(@Nullable String selectedValue) {
    Map<String, String> jdkMap = collectJdkNamesAndDescriptions();
    if (selectedValue != null && !jdkMap.containsKey(selectedValue)) {
      assert selectedValue.length() > 0;
      jdkMap.put(selectedValue, selectedValue);
    }

    removeAllItems();

    for (Map.Entry<String, String> entry : jdkMap.entrySet()) {
      ComboBoxUtil.addToModel((DefaultComboBoxModel)getModel(), entry.getKey(), entry.getValue());
    }

    ComboBoxUtil.select((DefaultComboBoxModel)getModel(), selectedValue);
  }

  public String getSelectedValue() {
    return ComboBoxUtil.getSelectedString((DefaultComboBoxModel)getModel());
  }

  private Map<String, String> collectJdkNamesAndDescriptions() {
    Map<String, String> result = new LinkedHashMap<String, String>();

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
      String name = projectJdk.getName();
      result.put(name, name);
    }

    result.put(MavenRunnerSettings.USE_INTERNAL_JAVA, RunnerBundle.message("maven.java.internal"));

    if (myProject != null) {
      String projectJdkTitle;

      String projectJdk = ProjectRootManager.getInstance(myProject).getProjectSdkName();
      if (projectJdk == null) {
        projectJdkTitle = "Use Project JDK (not defined yet)";
      }
      else {
        projectJdkTitle = "Use Project JDK (" + projectJdk + ')';
      }

      result.put(MavenRunnerSettings.USE_PROJECT_JDK, projectJdkTitle);
    }

    result.put(MavenRunnerSettings.USE_JAVA_HOME, RunnerBundle.message("maven.java.home.env"));

    return result;
  }

}
