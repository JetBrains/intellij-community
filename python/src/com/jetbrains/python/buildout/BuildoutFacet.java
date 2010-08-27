package com.jetbrains.python.buildout;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facet for buildout support.
 * Knows which script in bin/ contains paths we want to add.
 * User: dcheryasov
 * Date: Jul 25, 2010 3:23:50 PM
 */
public class BuildoutFacet extends Facet<BuildoutFacetConfiguration> implements PythonPathContributingFacet {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.buildout.BuildoutFacet");
  @NonNls private static final String BUILDOUT_CFG = "buildout.cfg";

  public BuildoutFacet(@NotNull final FacetType facetType,
                       @NotNull final Module module,
                       @NotNull final String name,
                       @NotNull final BuildoutFacetConfiguration configuration, Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
  }

  @Nullable
  public static VirtualFile getRunner(VirtualFile baseDir) {
    if (baseDir == null) return null;
    final VirtualFile cfg = baseDir.findChild(BUILDOUT_CFG);
    if (cfg != null && !cfg.isDirectory()) {
      VirtualFile eggs = baseDir.findChild("eggs");
      if (eggs != null && eggs.isDirectory()) {
        VirtualFile bin = baseDir.findChild("bin");
        if (bin != null && bin.isDirectory()) {
          final String exe;
          if (SystemInfo.isWindows || SystemInfo.isOS2) {
            exe = "buildout.exe";
          }
          else {
            exe = "buildout";
          }
          VirtualFile runner = bin.findChild(exe);
          if (runner != null && !runner.isDirectory()) {
            return runner;
          }
        }
      }
    }
    return null;
  }

  /**
   * Generates a <code>sys.path[0:0] = [...]</code> with paths that buildout script wants.
   * @param module to get a buildout facet from
   * @return the statement, or null if there's no buildout facet.
   */
  @Nullable
  public String getPathPrependStatement() {
    BuildoutFacet buildout_facet = this;
    StringBuilder sb = new StringBuilder("sys.path[0:0]=[");
    for (String s : buildout_facet.getAdditionalPythonPath()) {
      sb.append("'").append(s).append("',");
      // NOTE: we assume that quotes and spaces are escaped in paths back in the buildout script we extracted them from.
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public void initFacet() {
    BuildoutFacetConfiguration config = getConfiguration();
    config.setPaths(extractFromScript(LocalFileSystem.getInstance().findFileByPath(config.getScriptName())));
  }

  /**
   * Extracts paths from given script, assuming sys.path[0:0] assignment.
   *
   * @param script
   * @return extracted paths, or null if extraction fails.
   */
  @Nullable
  public static List<String> extractFromScript(VirtualFile script) {
    assert script != null;
    List<String> ret = new LinkedList<String>();
    try {
      String text = VfsUtil.loadText(script);
      Pattern pat = Pattern.compile("(?:^\\s*(['\"])(.*)(\\1),\\s*$)|(\\])", Pattern.MULTILINE);
      final String bait_string = "sys.path[0:0]";
      int pos = text.indexOf(bait_string);
      if (pos >= 0) {
        pos += bait_string.length();
        Matcher scanner = pat.matcher(text);
        boolean did_nothing = true;
        while (scanner.find(pos)) {
          did_nothing = false;
          String value = scanner.group(2);
          if (value != null) {
            ret.add(value);
            pos = scanner.end();
          }
          else {
            break;
          } // we've matched the ']', it's group(4)
        }
        if (did_nothing) return null;
      }
    }
    catch (IOException e) {
      LOG.error("Failed to read script", e);
      return null;
    }
    return ret;
  }

  @Override
  public List<String> getAdditionalPythonPath() {
    BuildoutFacetConfiguration cfg = getConfiguration();
    return cfg.getPaths();
  }


  @Nullable
  public static BuildoutFacet getInstance(Module module) {
    return FacetManager.getInstance(module).getFacetByType(BuildoutFacetType.ID);
  }

  public void patchCommandLineForBuildout(GeneralCommandLine commandLine) {
    Map<String, String> new_env = PythonEnvUtil.cloneEnv(commandLine.getEnvParams()); // we need a copy lest we change config's map.
    ParametersList params = commandLine.getParametersList();
    // alter execution script
    ParamsGroup script_params = params.getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert script_params != null;
    String normal_script = script_params.getParameters().get(0); // expect DjangoUtil.MANAGE_FILE
    String engulfer_path = PythonHelpersLocator.getHelperPath("pycharm/buildout_engulfer.py");
    new_env.put("PYCHARM_ENGULF_SCRIPT", getConfiguration().getScriptName());
    script_params.getParametersList().replaceOrPrepend(normal_script, engulfer_path);
    // add pycharm helpers to pythonpath so that fixGetpass is importable
    String PYTHONPATH = "PYTHONPATH";
    new_env.put(PYTHONPATH, GeneralCommandLine.appendToPathEnvVar(new_env.get(PYTHONPATH), PythonHelpersLocator.getHelpersRoot().getAbsolutePath()));
    /*
    // set prependable paths
    List<String> paths = facet.getAdditionalPythonPath();
    if (paths != null) {
      path_value = PyUtil.joinWith(File.pathSeparator, paths);
      new_env.put("PYCHARM_PREPEND_SYSPATH", path_value);
    }
    */
    commandLine.setEnvParams(new_env);
  }


  @Nullable
  public File getConfigFile() {
    final String scriptName = getConfiguration().getScriptName();
    if (!StringUtil.isEmpty(scriptName)) {
      return new File(new File(scriptName).getParentFile().getParentFile(), BUILDOUT_CFG);
    }
    return null;
  }

  @Nullable
  public BuildoutCfgFile getConfigPsiFile() {
    File cfg = getConfigFile();
    if (cfg.exists()) {
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(cfg);
      if (vFile != null) {
        final PsiFile psiFile = PsiManager.getInstance(getModule().getProject()).findFile(vFile);
        if (psiFile instanceof BuildoutCfgFile) {
          return (BuildoutCfgFile)psiFile;
        }
      }

    }
    return null;
  }

}
