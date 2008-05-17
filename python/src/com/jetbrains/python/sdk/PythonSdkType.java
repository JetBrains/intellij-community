package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
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
    if (SystemInfo.isLinux) {
      // on Linux, Python SDK home points to the /lib directory of a particular Python version
      File f_re = new File(path, "re.py");
      File f_future = new File(path, "__future__.py");
      File f_site = new File(path, "site-packages");
      return (
        f_re.exists() &&
        f_future.exists() &&
        f_site.exists() &&  f_site.isDirectory()
      );
    }
    else {
      File f = getPythonBinaryPath(path);
      return f != null && f.exists();
    }
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
    String sdk_path = sdk.getHomePath();
    String bin_path = getInterpreterPath(sdk_path);
    final String stubs_path =
        PathManager.getSystemPath() + File.separator + "python_stubs" + File.separator + sdk_path.hashCode() + File.separator;
    // we have a number of lib dirs, those listed in pyton's sys.path
    String script = // a script printing sys.path
      "import sys\n"+
      "for x in sys.path:\n"+
      "  sys.stdout.write(x+chr(10))"
    ;
    final List<String> paths = SdkUtil.getProcessOutput(sdk_path, new String[] {bin_path, "-c", script}).getStdout();
    if ((paths != null) && paths.size() > 0) {
      // add every path as root.
      for (String path: paths) {
        if (path.indexOf(File.separator) < 0) continue; // TODO: interpret 'specail' paths reasonably
        VirtualFile child = LocalFileSystem.getInstance().findFileByPath(path);
        if (child != null) {
          // NOTE: maybe handle .zip / .egg files specially?
          sdkModificator.addRoot(child, OrderRootType.SOURCES);
          sdkModificator.addRoot(child, OrderRootType.CLASSES);
        }
        else LOG.info("Bogus sys.path entry "+path);
      }
      generateStubs(sdk_path, stubs_path);
      sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(stubs_path), OrderRootType.SOURCES);
    }
    
    sdkModificator.commitChanges();
  }

  @Nullable
  public String getVersionString(final String sdkHome) {
    String binaryPath = getInterpreterPath(sdkHome);
    final boolean isJython = isJythonSdkHome(sdkHome);
    String version_regexp, version_opt;
    if (isJython) {
      version_regexp = "(Jython \\S+) on .*";
      version_opt = "--version";
    }
    else { // CPython
      version_regexp = "(Python \\S+).*";
      version_opt = "-V";
    }
    Pattern pattern = Pattern.compile(version_regexp);
    String version = SdkUtil.getFirstMatch(SdkUtil.getProcessOutput(sdkHome, new String[] {binaryPath, version_opt}).getStderr(), pattern);
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
