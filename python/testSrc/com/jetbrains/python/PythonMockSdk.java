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
package com.jetbrains.python;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.MockSdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class PythonMockSdk {
  @NonNls private static final String MOCK_SDK_NAME = "Mock Python SDK";

  private PythonMockSdk() {
  }

  public static Sdk findOrCreate(final String version, @NotNull final VirtualFile ... additionalRoots) {
    final List<Sdk> sdkList = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    for (Sdk sdk : sdkList) {
      if (sdk.getName().equals(MOCK_SDK_NAME + " " + version)) {
        return sdk;
      }
    }
    final String mock_path = PythonTestUtil.getTestDataPath() + "/MockSdk" + version + "/";

    String sdkHome = new File(mock_path, "bin/python"+version).getPath();
    SdkType sdkType = PythonSdkType.getInstance();

    MultiMap<OrderRootType, VirtualFile> roots = MultiMap.create();

    File libPath = new File(mock_path, "Lib");
    if (libPath.exists()) {
      roots.putValue(OrderRootType.CLASSES, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libPath));
    }

    roots.putValue(OrderRootType.CLASSES, PyUserSkeletonsUtil.getUserSkeletonsDirectory());

    final LanguageLevel level = LanguageLevel.fromPythonVersion(version);
    final VirtualFile typeShedDir = PyTypeShed.INSTANCE.getDirectory();
    PyTypeShed.INSTANCE.findRootsForLanguageLevel(level).forEach(path -> {
      final VirtualFile file = typeShedDir.findFileByRelativePath(path);
      if (file != null) {
        roots.putValue(OrderRootType.CLASSES, file);
      }
    });

    String mock_stubs_path = mock_path + PythonSdkType.SKELETON_DIR_NAME;
    roots.putValue(PythonSdkType.BUILTIN_ROOT_TYPE, LocalFileSystem.getInstance().refreshAndFindFileByPath(mock_stubs_path));

    for (final VirtualFile root : additionalRoots) {
      roots.putValue(OrderRootType.CLASSES, root);
    }


    MockSdk sdk = new MockSdk(MOCK_SDK_NAME + " " + version, sdkHome, "Python " + version + " Mock SDK", roots, sdkType);

    final FileBasedIndex index = FileBasedIndex.getInstance();
    index.requestRebuild(StubUpdatingIndex.INDEX_ID);
    index.requestRebuild(PyModuleNameIndex.NAME);

    // com.jetbrains.python.psi.resolve.PythonSdkPathCache.getInstance() corrupts SDK, so have to clone
    return sdk.clone();
  }
}
