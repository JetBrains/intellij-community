/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.attach;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.attach.LocalAttachSettings;
import com.intellij.xdebugger.attach.XAttachDebugger;
import com.intellij.xdebugger.attach.XAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XAttachGroup;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class PyLocalAttachDebuggerProvider implements XAttachDebuggerProvider<LocalAttachSettings> {
  private static final Key<List<XAttachDebugger<LocalAttachSettings>>> DEBUGGERS_KEY =
    Key.create("PyLocalAttachDebuggerProvider.DEBUGGERS");

  @NotNull
  @Override
  public XAttachGroup<LocalAttachSettings> getAttachGroup() {
    return PyLocalAttachGroup.INSTANCE;
  }

  @NotNull
  @Override
  public List<XAttachDebugger<LocalAttachSettings>> getAvailableDebuggers(@NotNull Project project,
                                                                          @NotNull LocalAttachSettings settings,
                                                                          @NotNull UserDataHolder contextHolder) {
    if (StringUtil.containsIgnoreCase(settings.getInfo().getExecutableName(), "python")) {
      List<XAttachDebugger<LocalAttachSettings>> result = contextHolder.getUserData(DEBUGGERS_KEY);
      if (result != null) return result;

      if (settings.getInfo().getExecutableCannonicalPath().isPresent() &&
          new File(settings.getInfo().getExecutableCannonicalPath().get()).exists()) {
        result =
          Lists.newArrayList(new PyLocalAttachDebugger(settings.getInfo().getExecutableCannonicalPath().get()));
      }
      else {
        result = ContainerUtil.map(PythonSdkType.getAllLocalCPythons(), sdk -> new PyLocalAttachDebugger(sdk));
      }

      // most recent python version goes first
      Collections.sort(result, (a, b) -> -a.getDebuggerDisplayName().compareToIgnoreCase(b.getDebuggerDisplayName()));

      contextHolder.putUserData(DEBUGGERS_KEY, Collections.unmodifiableList(result));
      return result;
    }
    return Collections.emptyList();
  }

  private static class PyLocalAttachDebugger implements XAttachDebugger<LocalAttachSettings> {
    private final String mySdkHome;
    @NotNull private final String myName;

    public PyLocalAttachDebugger(@NotNull Sdk sdk) {
      mySdkHome = sdk.getHomePath();
      myName = PythonSdkType.getInstance().getVersionString(sdk) + " Debugger";
    }

    public PyLocalAttachDebugger(@NotNull String sdkHome) {
      mySdkHome = sdkHome;
      myName = "Python Debugger";
    }

    @NotNull
    @Override
    public String getDebuggerDisplayName() {
      return myName;
    }

    @Override
    public void attachDebugSession(@NotNull Project project, @NotNull LocalAttachSettings settings) throws ExecutionException {
      PyAttachToProcessDebugRunner runner = new PyAttachToProcessDebugRunner(project, settings.getInfo().getPid(), mySdkHome);
      runner.launch();
    }
  }
}
