/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
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
    Set<UsageDescriptor> result = new HashSet<>();
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      Sdk pythonSdk = PythonSdkType.findPythonSdk(m);
      if (pythonSdk != null) {
        String versionString = pythonSdk.getVersionString();
        if (StringUtil.isEmpty(versionString)) {
          versionString = "unknown version";
        }
        if (PythonSdkType.isRemote(pythonSdk)) {
          versionString = versionString + " (" + getRemoteSuffix(pythonSdk) + ")";
        }
        
        if (PythonSdkType.isVirtualEnv(pythonSdk)) {
          versionString += " [virtualenv]";
        }

        if (PythonSdkType.isCondaVirtualEnv(pythonSdk)) {
          versionString += " [condavenv]";
        }
        result.add(new UsageDescriptor(versionString, 1));
      }
    }
    return result;
  }

  @NotNull
  private static String getRemoteSuffix(@NotNull Sdk pythonSdk) {
    return pythonSdk.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataBase
           ? "Remote " + (((PyRemoteSdkAdditionalDataBase)pythonSdk.getSdkAdditionalData()).getRemoteConnectionType()).getName()
           : "";
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }
}
