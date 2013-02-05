package com.jetbrains.python.statistics;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PyInterpreterUsagesCollector extends AbstractApplicationUsagesCollector {
  private static final String GROUP_ID = "py-interpreter";

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    Set<UsageDescriptor> result = new HashSet<UsageDescriptor>();
    for(Module m: ModuleManager.getInstance(project).getModules()) {
      Sdk pythonSdk = PythonSdkType.findPythonSdk(m);
      if (pythonSdk != null) {
        String versionString = pythonSdk.getVersionString();
        if (versionString != null) {
          result.add(new UsageDescriptor(versionString, 1));
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }
}
