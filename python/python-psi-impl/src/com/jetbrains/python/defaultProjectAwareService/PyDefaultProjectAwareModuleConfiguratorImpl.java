// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.defaultProjectAwareService;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class PyDefaultProjectAwareModuleConfiguratorImpl<
  STATE,
  SERVICE extends PyDefaultProjectAwareService<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE>,
  APP_SERVICE extends SERVICE,
  MODULE_SERVICE extends SERVICE> implements PyDefaultProjectAwareServiceModuleConfigurator {
  @NotNull
  private final PyDefaultProjectAwareServiceClasses<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE> myClasses;
  @Nullable
  private final Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> myAutoDetector;

  public PyDefaultProjectAwareModuleConfiguratorImpl(@NotNull PyDefaultProjectAwareServiceClasses<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE> classes) {
    this(classes, null);
  }

  public PyDefaultProjectAwareModuleConfiguratorImpl(@NotNull PyDefaultProjectAwareServiceClasses<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE> classes,
                                                     @Nullable Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> autoDetector) {
    myClasses = classes;
    myAutoDetector = autoDetector;
  }

  @Override
  public void configureModule(@NotNull Module module, boolean newProject) {
    Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> detector = myAutoDetector;
    if (newProject || detector == null) {
      // For new project only copy from app.
      // For opened only copy if no autoconfiguration
      myClasses.copyFromAppToModule(module);
      return;
    }


    //Try to autodetect
    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> autodetectSettings(module, detector), 10, TimeUnit.SECONDS);
  }

  private void autodetectSettings(@NotNull Module module,
                                  @NotNull Function<Pair<Module, Collection<VirtualFile>>, ? extends STATE> detector) {
    final String extension = PythonFileType.INSTANCE.getDefaultExtension();
    // Module#getModuleScope() and GlobalSearchScope#getModuleScope() search only in source roots
    Project project = module.getProject();
    final GlobalSearchScope searchScope = new ModulesScope(Collections.singleton(module), project);

    Collection<VirtualFile> pyFiles = ReadAction.nonBlocking(() -> FilenameIndex
        .getAllFilesByExt(project, extension, searchScope))
        .inSmartMode(project)
        .executeSynchronously();

    STATE state = detector.fun(Pair.create(module, pyFiles));
    if (state != null) {
      myClasses.getModuleService(module).loadState(state);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> myClasses.copyFromAppToModule(module));
    }
  }
}
