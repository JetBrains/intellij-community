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
package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PythonModuleBuilderBase extends ModuleBuilder {
  private final List<Runnable> mySdkChangedListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final DirectoryProjectGenerator myGenerator;
  private Sdk mySdk;

  public PythonModuleBuilderBase() {
    myGenerator = null;
  }

  public PythonModuleBuilderBase(DirectoryProjectGenerator generator) {
    myGenerator = generator;
  }

  @Override
  public String getGroupName() {
    return "Python";
  }

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    // false for the module automatically created in a new project
    if (myJdk != null) {
      rootModel.setSdk(myJdk);
    }
    else {
      rootModel.inheritSdk();
    }

    doAddContentEntry(rootModel);
  }

  @Override
  public ModuleType getModuleType() {
    return PythonModuleTypeBase.getInstance();
  }

  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(final Sdk sdk) {
    if (mySdk != sdk) {
      mySdk = sdk;
      for (Runnable runnable : mySdkChangedListeners) {
        runnable.run();
      }
    }
  }

  public void addSdkChangedListener(Runnable runnable) {
    mySdkChangedListeners.add(runnable);
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk instanceof PythonSdkType;
  }

  @Nullable
  @Override
  public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    if (module != null && myGenerator != null) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] contentRoots = moduleRootManager.getContentRoots();
      VirtualFile dir = module.getProject().getBaseDir();
      if (contentRoots.length > 0 && contentRoots[0] != null) {
        dir = contentRoots[0];
      }
      myGenerator.generateProject(project, dir, null, module);
    }
    return module;
  }
}
