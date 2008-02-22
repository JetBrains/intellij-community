package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PythonFileType;

import javax.swing.*;
import java.io.File;
import java.util.TreeSet;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  public PythonSdkType() {
    super("Python SDK");
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  public Icon getIconForAddAction() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @Nullable
  public String suggestHomePath() {
    TreeSet<String> candidates = new TreeSet<String>();
    if (SystemInfo.isWindows) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath("C:\\");
      if (rootDir != null) {
        VirtualFile[] topLevelDirs = rootDir.getChildren();
        for(VirtualFile dir: topLevelDirs) {
          if (dir.isDirectory() && dir.getName().toLowerCase().startsWith("python")) {
            candidates.add(dir.getPath());
          }
        }
      }
    }
    else if (SystemInfo.isMac) {
      return "/Library/Frameworks/Python.framework/Versions/Current/";
    }

    if (candidates.size() > 0) {
      // return latest version
      String[] candidateArray = candidates.toArray(new String[candidates.size()]);
      return candidateArray [candidateArray.length-1];
    }
    return null;
  }

  public boolean isValidSdkHome(final String path) {
    if (SystemInfo.isWindows) {
      return new File(path, "python.exe").exists();
    }
    else if (SystemInfo.isMac) {
      return new File(new File(path, "bin"), "python").exists();
    }
    
    return false;
  }

  public String suggestSdkName(final String currentSdkName, final String sdkHome) {
    return getVersionString(sdkHome);
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, final SdkModificator sdkModificator) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void saveAdditionalData(final SdkAdditionalData additionalData, final Element additional) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public String getPresentableName() {
    return "Python SDK";
  }

  public void setupSdkPaths(final Sdk sdk) {
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    VirtualFile libDir = sdk.getHomeDirectory().findChild("Lib");
    if (libDir != null) {
      if (SystemInfo.isMac) {
        for (VirtualFile child : libDir.getChildren()) {
          if (child.getName().startsWith("python")) {
            sdkModificator.addRoot(child, OrderRootType.SOURCES);
            sdkModificator.addRoot(child, OrderRootType.CLASSES);
            break;
          }
        }
      }
      else {
        sdkModificator.addRoot(libDir, OrderRootType.SOURCES);
        sdkModificator.addRoot(libDir, OrderRootType.CLASSES);
      }
    }

    sdkModificator.commitChanges();
  }

  @Nullable
  public String getVersionString(final String sdkHome) {
    String pythonBinary;
    if (SystemInfo.isWindows) {
      pythonBinary = new File(sdkHome, "python.exe").getPath();
    }
    else {
      pythonBinary = "python";
    }
    return SdkVersionUtil.readVersionFromProcessOutput(sdkHome, new String[] { pythonBinary, "-V" }, "Python");
  }
}
