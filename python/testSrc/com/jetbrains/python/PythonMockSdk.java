// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.MockSdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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

/**
 * @author yole
 */
public final class PythonMockSdk {

  private PythonMockSdk() {
  }

  public static @NotNull Sdk create(@NotNull String name) {
    return create(name, LanguageLevel.getLatest());
  }

  public static @NotNull Sdk create(@NotNull LanguageLevel level, VirtualFile @NotNull ... additionalRoots) {
    return create("MockSdk", level, additionalRoots);
  }

  private static @NotNull Sdk create(@NotNull String name, @NotNull LanguageLevel level, VirtualFile @NotNull ... additionalRoots) {
    final String mockSdkPath = PythonTestUtil.getTestDataPath() + "/" + name;

    MultiMap<OrderRootType, VirtualFile> roots = MultiMap.create();
    roots.putValues(OrderRootType.CLASSES, createRoots(mockSdkPath, level));
    roots.putValues(OrderRootType.CLASSES, Arrays.asList(additionalRoots));

    MockSdk sdk = new MockSdk(
      "Mock " + PyNames.PYTHON_SDK_ID_NAME + " " + level.toPythonVersion(),
      mockSdkPath + "/bin/python",
      toVersionString(level),
      roots,
      new PyMockSdkType(level)
    );

    // com.jetbrains.python.psi.resolve.PythonSdkPathCache.getInstance() corrupts SDK, so have to clone
    return sdk.clone();
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

    @Nullable
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
