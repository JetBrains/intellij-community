// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.sdk.PythonSdkType.MOCK_PY_MARKER_KEY;


public final class PythonMockSdk {

  private PythonMockSdk() {
  }

  public static @NotNull Sdk create() {
    return create(LanguageLevel.getLatest());
  }

  public static @NotNull Sdk create(@NotNull String sdkPath) {
    return create(sdkPath, LanguageLevel.getLatest());
  }

  public static @NotNull Sdk create(@NotNull LanguageLevel level, VirtualFile @NotNull ... additionalRoots) {
    return create(PythonTestUtil.getTestDataPath() + "/MockSdk", level, additionalRoots);
  }

  private static @NotNull Sdk create(@NotNull String sdkPath, @NotNull LanguageLevel level, VirtualFile @NotNull ... additionalRoots) {
    String sdkName = "Mock " + PyNames.PYTHON_SDK_ID_NAME + " " + level.toPythonVersion();
    return create(sdkName, sdkPath, new PyMockSdkType(level), level, additionalRoots);
  }

  public static @NotNull Sdk create(@NotNull String sdkName,
                                    @NotNull String sdkPath,
                                    @NotNull SdkTypeId sdkType,
                                    @NotNull LanguageLevel level,
                                    VirtualFile @NotNull ... additionalRoots) {
    Sdk sdk = ProjectJdkTable.getInstance().createSdk(sdkName, sdkType);
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkPath + "/bin/python");
    sdkModificator.setVersionString(toVersionString(level));

    createRoots(sdkPath, level).forEach(vFile -> {
      sdkModificator.addRoot(vFile, OrderRootType.CLASSES);
    });

    Arrays.asList(additionalRoots).forEach(vFile -> {
      sdkModificator.addRoot(vFile, OrderRootType.CLASSES);
    });

    Application application = ApplicationManager.getApplication();
    Runnable runnable = () -> sdkModificator.commitChanges();
    if (application.isDispatchThread()) {
      application.runWriteAction(runnable);
    } else {
      application.invokeAndWait(() -> application.runWriteAction(runnable));
    }
    sdk.putUserData(MOCK_PY_MARKER_KEY, true);
    return sdk;

    // com.jetbrains.python.psi.resolve.PythonSdkPathCache.getInstance() corrupts SDK, so have to clone
    //return sdk.clone();
  }

  private static @NotNull List<VirtualFile> createRoots(@NotNull @NonNls String mockSdkPath, @NotNull LanguageLevel level) {
    final var result = new ArrayList<VirtualFile>();

    final var localFS = LocalFileSystem.getInstance();
    ContainerUtil.addIfNotNull(result, localFS.refreshAndFindFileByIoFile(new File(mockSdkPath, "Lib")));
    ContainerUtil.addIfNotNull(result, localFS.refreshAndFindFileByIoFile(new File(mockSdkPath, PythonSdkUtil.SKELETON_DIR_NAME)));

    ContainerUtil.addIfNotNull(result, PyUserSkeletonsUtil.getUserSkeletonsDirectory());

    result.addAll(PyTypeShed.INSTANCE.findRootsForLanguageLevel(level));

    return result;
  }

  private static @NotNull String toVersionString(@NotNull LanguageLevel level) {
    return "Python " + level.toPythonVersion();
  }

  private static final class PyMockSdkType implements SdkTypeId {

    @NotNull
    private final LanguageLevel myLevel;

    private PyMockSdkType(@NotNull LanguageLevel level) {
      myLevel = level;
    }

    @NotNull
    @Override
    public String getName() {
      return PyNames.PYTHON_SDK_ID_NAME;
    }

    @Override
    public @NotNull String getVersionString(@NotNull Sdk sdk) {
      return toVersionString(myLevel);
    }

    @Override
    public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
    }

    @Nullable
    @Override
    public SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, @NotNull Element additional) {
      return null;
    }
  }
}
