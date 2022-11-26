/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.tools.sdkTools;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;


/**
 * Engine to create SDK for tests.
 * See {@link #createTempSdk(VirtualFile, SdkCreationType, Module)}
 *
 * @author Ilya.Kazakevich
 */
public final class PySdkTools {

  private static final Sdk[] NO_SDK = new Sdk[0];

  private PySdkTools() {

  }

  /**
   * Creates SDK by its path and associates it with module (if module provided)
   *
   * @param sdkHome         path to sdk
   * @param sdkCreationType SDK creation strategy (see {@link SdkCreationType} doc)
   * @return sdk
   */
  @NotNull
  public static Sdk createTempSdk(@NotNull final VirtualFile sdkHome,
                                  @NotNull final SdkCreationType sdkCreationType,
                                  @Nullable final Module module
  )
    throws InvalidSdkException {
    final Ref<Sdk> ref = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      // sdkHome guarantees SDK name uniqueness. SdkUtil can't do that since no current SDK are provided.
      final Sdk sdk = SdkConfigurationUtil.setupSdk(NO_SDK, sdkHome, PythonSdkType.getInstance(), true, null, sdkHome.getPath());
      Assert.assertNotNull("Failed to create SDK on " + sdkHome, sdk);
      ref.set(sdk);
    });
    final Sdk sdk = ref.get();
    if (sdkCreationType != SdkCreationType.EMPTY_SDK) {
      try {
        generateTempSkeletonsOrPackages(sdk, sdkCreationType == SdkCreationType.SDK_PACKAGES_AND_SKELETONS, module);
      }
      catch (ExecutionException e) {
        throw new InvalidSdkException("Can't generate skeleton packages", e);
      }
    }
    ApplicationManager.getApplication().invokeAndWait(() -> SdkConfigurationUtil.addSdk(sdk));
    return sdk;
  }


  /**
   * Adds installed eggs to SDK, generates skeletons (optionally) and associates it with module.
   *
   * @param sdk          sdk to process
   * @param addSkeletons add skeletons or only packages
   * @param module       module to associate with (if provided)
   * @throws InvalidSdkException bas sdk
   */
  public static void generateTempSkeletonsOrPackages(@NotNull final Sdk sdk,
                                                     final boolean addSkeletons,
                                                     @Nullable final Module module)
    throws InvalidSdkException, ExecutionException {
    Project project = null;

    if (module != null) {
      project = module.getProject();

      PySdkExtKt.setPythonSdk(project, sdk);
      PySdkExtKt.setPythonSdk(module, sdk);
    }
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }


    final SdkModificator modificator = sdk.getSdkModificator();

    modificator.setSdkAdditionalData(new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk)));

    for (final String path : new PyTargetsIntrospectionFacade(sdk, project).getInterpreterPaths(new EmptyProgressIndicator())) {
      addTestSdkRoot(modificator, path);
    }
    if (!addSkeletons) {
      ApplicationManager.getApplication().invokeAndWait(modificator::commitChanges);
      return;
    }

    final String skeletonsPath = PythonSdkUtil.getSkeletonsPath(PathManager.getSystemPath(), sdk.getHomePath());
    addTestSdkRoot(modificator, skeletonsPath);

    ApplicationManager.getApplication().invokeAndWait(modificator::commitChanges);

    PySkeletonRefresher
      .refreshSkeletonsOfSdk(project, null, skeletonsPath, sdk);
  }

  public static void addTestSdkRoot(@NotNull SdkModificator sdkModificator, @NotNull String path) {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (file != null) {
      sdkModificator.addRoot(PythonSdkType.getSdkRootVirtualFile(file), OrderRootType.CLASSES);
    }
  }
}
