package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  private static final Logger LOG = Logger.getInstance("#" + PythonSdkType.class.getName());

  public static PythonSdkType getInstance() {
    return SdkType.findInstance(PythonSdkType.class);
  }

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
    else if (SystemInfo.isLinux) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath("/usr/lib");
      if (rootDir != null) {
        VirtualFile[] suspect_dirs = rootDir.getChildren();
        for(VirtualFile dir: suspect_dirs) {
          if (dir.isDirectory() && dir.getName().startsWith("python")) {
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
    return isPythonSdkHome(path) || isJythonSdkHome(path);
  }

  /**
   * Checks if the path is a valid home.
   * Valid CPython home must contain some standard libraries. Of them we look for re.py, __future__.py and site-packages/.
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  @NonNls
  private static boolean isPythonSdkHome(final String path) {
    /*
    File f = getPythonBinaryPath(path);
    return f != null && f.exists();
    */
    File f_re = new File(path, "re.py");
    File f_future = new File(path, "__future__.py");
    File f_site = new File(path, "site-packages");
    return (
      f_re.exists() &&
      f_future.exists() &&
      f_site.exists() &&  f_site.isDirectory()
    );
  }

  private static boolean isJythonSdkHome(final String path) {
    File f = getJythonBinaryPath(path);
    return f != null && f.exists();
  }

  private static File getJythonBinaryPath(final String path) {
    if (SystemInfo.isWindows) {
      return new File(path, "jython.bat");
    }
    // TODO: support Mac and Linux
    return null;
  }

  @NonNls
  private static File getPythonBinaryPath(final String path) {
    if (SystemInfo.isWindows) {
      return new File(path, "python.exe");
    }
    else if (SystemInfo.isMac) {
      return new File(new File(path, "bin"), "python");
    }
    else if (SystemInfo.isLinux) {
      // most probably path is like "/usr/lib/pythonX.Y"; executable is most likely /usr/bin/pythonX.Y
      Matcher m = Pattern.compile(".*/(python\\d\\.\\d)").matcher(path);
      File py_binary;
      if (m.matches()) {
        String py_name = m.group(1); // $1
        py_binary = new File("/usr/bin/"+py_name);
      }
      else py_binary = new File("/usr/bin/python"); // TODO: search in $PATH
      if (py_binary.exists()) return py_binary;
    }
    return null;
  }

  public String suggestSdkName(final String currentSdkName, final String sdkHome) {
    String name = getVersionString(sdkHome);
    if (name == null) name = "Unknown at " + sdkHome; // last resort
    return name;
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, final SdkModificator sdkModificator) {
    return null;
  }

  public void saveAdditionalData(final SdkAdditionalData additionalData, final Element additional) {
  }

  @Override
  public SdkAdditionalData loadAdditionalData(final Sdk currentSdk, final Element additional) {
    final String[] urls = currentSdk.getRootProvider().getUrls(OrderRootType.SOURCES);
    for (String url : urls) {
      if (url.contains("python_stubs")) {
        final String path = VfsUtil.urlToPath(url);
        if (!new File(path).exists()) {
          generateStubs(currentSdk.getHomePath(), path);
        }
        break;
      }
    }
    return null;
  }

  public String getPresentableName() {
    return "Python SDK";
  }

  public void setupSdkPaths(final Sdk sdk) {
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    VirtualFile libDir;
    if (SystemInfo.isLinux) libDir = sdk.getHomeDirectory();
    else libDir = sdk.getHomeDirectory().findChild("Lib");
    final String stubsPath =
        PathManager.getSystemPath() + File.separator + "python_stubs" + File.separator + sdk.getHomePath().hashCode() + File.separator;
    if (libDir != null) {
      if (SystemInfo.isMac) {
        for (VirtualFile child : libDir.getChildren()) {
          if (child.getName().startsWith("python")) {
            sdkModificator.addRoot(child, OrderRootType.SOURCES);
            sdkModificator.addRoot(child, OrderRootType.CLASSES);
            break;
          }
        }
        generateStubs(sdk.getHomePath(), stubsPath);
      }
      else {
        sdkModificator.addRoot(libDir, OrderRootType.SOURCES);
        sdkModificator.addRoot(libDir, OrderRootType.CLASSES);
        generateStubs(sdk.getHomePath(), stubsPath);
      }
      sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(stubsPath), OrderRootType.SOURCES);
    }

    sdkModificator.commitChanges();
  }

  @Nullable
  public String getVersionString(final String sdkHome) {
    String binaryPath = getInterpreterPath(sdkHome);
    final boolean isJython = isJythonSdkHome(sdkHome);
    String marker = isJython ? "Jython" : "Python";
    String version = SdkVersionUtil.readVersionFromProcessOutput(sdkHome, new String[] { binaryPath, "-V" }, marker);
    if (version != null && isJython) {
      int p = version.indexOf(" on ");
      if (p >= 0) {
        return version.substring(0, p);
      }
    }
    return version;
  }

  public static String getInterpreterPath(final String sdkHome) {
    if (isJythonSdkHome(sdkHome)) {
      return getJythonBinaryPath(sdkHome).getPath();
    }
    return getPythonBinaryPath(sdkHome).getPath();
  }

  public static void generateStubs(String sdkPath, final String stubsRoot) {
    new File(stubsRoot).mkdirs();
    try {
      final String text = FileUtil.loadTextAndClose(new InputStreamReader(PythonSdkType.class.getResourceAsStream("generator.py")));
      final File tempFile = FileUtil.createTempFile("gen", "");

      FileWriter out = new FileWriter(tempFile);
      out.write(text);
      out.close();

      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(getInterpreterPath(sdkPath));

      commandLine.addParameter(tempFile.getAbsolutePath());
      commandLine.addParameter(stubsRoot);
      try {
        final OSProcessHandler handler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
        handler.startNotify();
        handler.waitFor();
        handler.destroyProcess();
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

  }

  public static List<Sdk> getAllSdks() {
    return ProjectJdkTable.getInstance().getSdksOfType(getInstance());
  }
}
