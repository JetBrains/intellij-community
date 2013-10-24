/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.statistics;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class PyPackageUsagesCollector extends AbstractApplicationUsagesCollector {
  private static final String GROUP_ID = "py-packages";

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    final Set<UsageDescriptor> result = new HashSet<UsageDescriptor>();
    for(final Module m: ModuleManager.getInstance(project).getModules()) {
      Sdk pythonSdk = PythonSdkType.findPythonSdk(m);
      if (pythonSdk != null) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            List<PyRequirement> requirements = PyPackageManagerImpl.getRequirements(m);
            if (requirements != null) {
              Collection<String> packages;
              try {
                packages = new HashSet<String>(PyPIPackageUtil.INSTANCE.getPackageNames());
              }
              catch (IOException e) {
                return;
              }
              for (PyRequirement requirement : requirements) {
                String name = requirement.getName();
                if (packages.contains(name)) {
                  result.add(new UsageDescriptor(name, 1));
                }
              }
            }
          }
        });
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
