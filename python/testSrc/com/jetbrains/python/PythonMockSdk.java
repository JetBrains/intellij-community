package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
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

  public static Sdk findOrCreate() {
    final List<Sdk> sdkList = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    for (Sdk sdk : sdkList) {
      if (sdk.getName().equals(MOCK_SDK_NAME)) {
        return sdk;
      }
    }
    return create();
  }

  public static Sdk create() {
    final String version = "2.5"; // TODO: implement language level here
    final String mock_path = PythonTestUtil.getTestDataPath() + "/MockSdk" + version + "/";

    String sdkHome = new File(mock_path, "bin/python"+version).getPath();
    SdkType sdkType = PythonSdkType.getInstance();


    final Sdk sdk = new ProjectJdkImpl(MOCK_SDK_NAME, sdkType) {
      @Override
      public String getVersionString() {
        return "Python " + version + " Mock SDK";
      }
    };
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkHome);
    String mock_stubs_path = mock_path + PythonSdkType.SKELETON_DIR_NAME;
    sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(mock_stubs_path), PythonSdkType.BUILTIN_ROOT_TYPE);
    //PythonSdkType.setupSdkPaths(sdkModificator, null);
    sdkModificator.commitChanges();
    return sdk;
  }
}
