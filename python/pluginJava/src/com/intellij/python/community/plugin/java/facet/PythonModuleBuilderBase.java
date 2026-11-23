// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.configuration.PyModuleInterpreterSetter;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;


class PythonModuleBuilderBase extends ModuleBuilder {
  private final List<Runnable> myInterpreterChangedListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final DirectoryProjectGenerator myGenerator;
  private @Nullable PythonInterpreter myInterpreter;

  PythonModuleBuilderBase() {
    myGenerator = null;
  }

  @Override
  public String getGroupName() {
    return "Python";
  }

  @Override
  public void setupRootModel(final @NotNull ModifiableRootModel rootModel) throws ConfigurationException {
    // The SDK of a module is never a Python SDK: the interpreter is a library entry, see commitModule.
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
  public ModuleType<?> getModuleType() {
    return PythonModuleTypeBase.getInstance();
  }

  /** The interpreter that the user picked for the new module, or {@code null} for none. */
  public @Nullable PythonInterpreter getInterpreter() {
    return myInterpreter;
  }

  public void setInterpreter(@Nullable PythonInterpreter interpreter) {
    if (!Objects.equals(myInterpreter, interpreter)) {
      myInterpreter = interpreter;
      for (Runnable runnable : myInterpreterChangedListeners) {
        runnable.run();
      }
    }
  }

  public void addInterpreterChangedListener(Runnable runnable) {
    myInterpreterChangedListeners.add(runnable);
  }

  /** A Python SDK is never the SDK of a module or a project, see {@link #setupRootModel}. */
  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return !(sdk instanceof PythonSdkType);
  }

  @Override
  public @Nullable Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    PythonInterpreter interpreter = myInterpreter;
    if (module != null && interpreter != null) {
      // The wizard commits the module in a write action, and a modal dialog can hold more models of the module.
      // So the interpreter is written when no modal dialog is open.
      ApplicationManager.getApplication().invokeLater(() -> PyModuleInterpreterSetter.setPythonInterpreterBlocking(module, interpreter),
                                                      ModalityState.nonModal(), module.getDisposed());
    }
    if (module != null && myGenerator != null) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] contentRoots = moduleRootManager.getContentRoots();
      VirtualFile dir = module.getProject().getBaseDir();
      if (contentRoots.length > 0 && contentRoots[0] != null) {
        dir = contentRoots[0];
      }
      myGenerator.generateProject(project, dir, new Object(), module);
    }
    return module;
  }
}
