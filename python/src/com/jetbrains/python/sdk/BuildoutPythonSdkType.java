package com.jetbrains.python.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Buildout-based pseudo-SDK. It takes interpreter path from SDK settings and root paths from current module settings,
 * which module must reside in a buildout directory structure.
 * See <a href="http://www.buildout.org/">buildout.org</a>.
 * <br/>
 * User: dcheryasov
 * Date: Jul 16, 2010 7:50:50 PM
 */
public class BuildoutPythonSdkType extends PythonSdkType {

  private String myBaseDir;

  public BuildoutPythonSdkType() {
    super("Python SDK for buildout");
  }

  public static BuildoutPythonSdkType getInstance(@NotNull String baseDir) {
    final BuildoutPythonSdkType me = SdkType.findInstance(BuildoutPythonSdkType.class);
    me.myBaseDir = baseDir;
    return me;
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return shortenDirName(myBaseDir) + " buildout + " + super.suggestSdkName(currentSdkName, sdkHome);
  }

  @Override
  protected List<String> getSysPathsFromScript(String bin_path) {
    // make buildout config contain our path-finding egg
    String peeker_file = PythonHelpersLocator.getHelperPath("install_buildout_peeker.py");
    ProcessOutput run_result = SdkUtil.getProcessOutput(
      new File(peeker_file).getParent(),
      new String[]{bin_path, peeker_file, myBaseDir}, 
      RUN_TIMEOUT
    );
    if (! checkSuccess(run_result)) return null;
    List<String> lines = run_result.getStdoutLines();
    if (lines.size() > 0 && "changed".equals(lines.get(0))) {
      // install peeker
      String buildout_exe;
      if (SystemInfo.isWindows) buildout_exe = "buildout.exe";
      else buildout_exe = "buildout";
      run_result = SdkUtil.getProcessOutput(
        myBaseDir,
        new String[]{myBaseDir + File.separator + "bin" + File.separator + buildout_exe, "install", "jetbrains_path_peeker"},
        RUN_TIMEOUT
      );
      if (! checkSuccess(run_result)) return null;
    }
    // run peeker from the bin dir
    String peeker_exe;
    if (SystemInfo.isWindows) peeker_exe = "jetbrains_path_peeker.exe";
    else peeker_exe = "jetbrains_path_peeker";
    run_result = SdkUtil.getProcessOutput(
      myBaseDir,
      new String[]{myBaseDir + File.separator + "bin" + File.separator + peeker_exe},
      RUN_TIMEOUT
    );
    if (! checkSuccess(run_result)) return null;
    return run_result.getStdoutLines();
  }

  @Override
  protected void addHardcodedPaths(SdkModificator sdkModificator) {
    // nothing
  }
}
