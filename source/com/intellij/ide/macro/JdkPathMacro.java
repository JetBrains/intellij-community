
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.util.PathUtil;

import java.io.File;

public final class JdkPathMacro extends Macro {
  public String getName() {
    return "JDKPath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.jdk.path");
  }

  public String expand(DataContext dataContext) {
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    final ProjectJdk anyJdk = PathUtilEx.getAnyJdk(project);
    if (anyJdk == null) {
      return null;
    }
    String jdkHomePath = PathUtil.getLocalPath(anyJdk.getHomeDirectory());
    if (jdkHomePath != null) {
      jdkHomePath = jdkHomePath.replace('/', File.separatorChar);
    }
    return jdkHomePath;
  }
}
