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
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author yole
 */
public final class PythonMockSdk {
  @NonNls private static final String MOCK_SDK_NAME = "Mock Python SDK";

  private PythonMockSdk() {
  }

  public static Sdk create(final String version, final VirtualFile @NotNull ... additionalRoots) {
    final String mock_path = PythonTestUtil.getTestDataPath() + "/MockSdk" + version + "/";

    String sdkHome = new File(mock_path, "bin/python" + version).getPath();

    MultiMap<OrderRootType, VirtualFile> roots = MultiMap.create();
    OrderRootType classes = OrderRootType.CLASSES;

    ContainerUtil.putIfNotNull(classes, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(mock_path, "Lib")), roots);

    ContainerUtil.putIfNotNull(classes, PyUserSkeletonsUtil.getUserSkeletonsDirectory(), roots);

    final LanguageLevel level = LanguageLevel.fromPythonVersion(version);
    final VirtualFile typeShedDir = PyTypeShed.INSTANCE.getDirectory();
    PyTypeShed.INSTANCE
      .findRootsForLanguageLevel(level)
      .forEach(path -> ContainerUtil.putIfNotNull(classes, typeShedDir.findFileByRelativePath(path), roots));

    String mock_stubs_path = mock_path + PythonSdkUtil.SKELETON_DIR_NAME;
    ContainerUtil.putIfNotNull(classes, LocalFileSystem.getInstance().refreshAndFindFileByPath(mock_stubs_path), roots);

    roots.putValues(classes, Arrays.asList(additionalRoots));

    MockSdk sdk = new MockSdk(MOCK_SDK_NAME + " " + version, sdkHome, "Python " + version + " Mock SDK", roots, new PyMockSdkType(version));

    // com.jetbrains.python.psi.resolve.PythonSdkPathCache.getInstance() corrupts SDK, so have to clone
    return sdk.clone();
  }

  private static final class PyMockSdkType implements SdkTypeId {

    @NotNull
    private final String myVersionString;
    private final String mySdkIdName;

    private PyMockSdkType(@NotNull String string) {
      mySdkIdName = PyNames.PYTHON_SDK_ID_NAME;
      myVersionString = string;
    }

    @NotNull
    @Override
    public String getName() {
      return mySdkIdName;
    }

    @Nullable
    @Override
    public String getVersionString(@NotNull Sdk sdk) {
      return myVersionString;
    }

    @Override
    public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {

    }

    @Nullable
    @Override
    public SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, @NotNull Element additional) {
      return null;
    }

    @Override
    public boolean isLocalSdk(@NotNull Sdk sdk) {
      return false;
    }
  }
}
