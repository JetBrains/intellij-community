package com.jetbrains.python;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class PythonMockSdk {
  @NonNls private static final String MOCK_SDK_NAME = "Mock Python SDK";

  private PythonMockSdk() {
  }

  public static Sdk findOrCreate(String version) {
    final List<Sdk> sdkList = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    for (Sdk sdk : sdkList) {
      if (sdk.getName().equals(MOCK_SDK_NAME + " " + version)) {
        return sdk;
      }
    }
    return create(version);
  }

  public static Sdk create(final String version) {
    final String mock_path = PythonTestUtil.getTestDataPath() + "/MockSdk" + version + "/";

    String sdkHome = new File(mock_path, "bin/python"+version).getPath();
    SdkType sdkType = PythonSdkType.getInstance();


    final Sdk sdk = new ProjectJdkImpl(MOCK_SDK_NAME + " " + version, sdkType) {
      @Override
      public String getVersionString() {
        return "Python " + version + " Mock SDK";
      }
    };
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkHome);

    File libPath = new File(mock_path, "Lib");
    if (libPath.exists()) {
      sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libPath), OrderRootType.CLASSES);
    }

    String mock_stubs_path = mock_path + PythonSdkType.SKELETON_DIR_NAME;
    sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(mock_stubs_path), PythonSdkType.BUILTIN_ROOT_TYPE);

    //PythonSdkType.setupSdkPaths(sdkModificator, null);
    sdkModificator.commitChanges();
    return sdk;
  }
}
