/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.buildout;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.buildout.config.BuildoutCfgLanguage;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.facet.FacetLibraryConfigurator;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facet for buildout support.
 * Knows which script in bin/ contains paths we want to add.
 * User: dcheryasov
 * Date: Jul 25, 2010 3:23:50 PM
 */
public class BuildoutFacet extends LibraryContributingFacet<BuildoutFacetConfiguration> implements PythonPathContributingFacet {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.buildout.BuildoutFacet");
  @NonNls public static final String BUILDOUT_CFG = "buildout.cfg";
  @NonNls public static final String SCRIPT_SUFFIX = "-script";
  private static final String BUILDOUT_LIB_NAME = "Buildout Eggs";

  public BuildoutFacet(@NotNull final FacetType facetType,
                       @NotNull final Module module,
                       @NotNull final String name,
                       @NotNull final BuildoutFacetConfiguration configuration, Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (Comparing.equal(event.getFile(), getScript())) {
          updatePaths();
          attachLibrary(module);
        }
      }
    }, this);
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
          if (ApplicationManager.getApplication().isDispatchThread() || !ApplicationManager.getApplication().isReadAccessAllowed()) {
            bin.refresh(false, false);
          }
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

  @NotNull
  public static List<VirtualFile> getExtraPathForAllOpenModules() {
    final List<VirtualFile> results = new ArrayList<>();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        final BuildoutFacet buildoutFacet = getInstance(module);
        if (buildoutFacet != null) {
          final List<String> paths = buildoutFacet.getConfiguration().getPaths();
          if (paths != null) {
            for (String path : paths) {
              final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
              if (file != null) {
                results.add(file);
              }
            }
          }
        }
      }
    }
    return results;
  }

  /**
   * Generates a <code>sys.path[0:0] = [...]</code> with paths that buildout script wants.
   *
   * @param additionalPythonPath
   * @param module to get a buildout facet from
   * @return the statement, or null if there's no buildout facet.
   */
  @Nullable
  public String getPathPrependStatement(List<String> additionalPythonPath) {
    StringBuilder sb = new StringBuilder("sys.path[0:0]=[");
    for (String s : additionalPythonPath) {
      sb.append("'").append(s).append("',");
      // NOTE: we assume that quotes and spaces are escaped in paths back in the buildout script we extracted them from.
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public void initFacet() {
    updateLibrary();
  }

  @Override
  public void updateLibrary() {
    updatePaths();
    attachLibrary(getModule());
  }

  @Override
  public void removeLibrary() {
    detachLibrary(getModule());
  }

  public void updatePaths() {
    BuildoutFacetConfiguration config = getConfiguration();
    final VirtualFile script = getScript();
    if (script != null) {
      config.setPaths(extractBuildoutPaths(script));
    }
  }

  @Nullable
  public VirtualFile getScript() {
    return LocalFileSystem.getInstance().findFileByPath(getConfiguration().getScriptName());
  }

  @Nullable
  public static List<String> extractBuildoutPaths(@NotNull VirtualFile script) {
    try {
      List<String> paths = extractFromScript(script);
      if (paths == null) {
        VirtualFile root = script.getParent().getParent();
        String partName = FileUtil.getNameWithoutExtension(script.getName());
        if (SystemInfo.isWindows && partName.endsWith(SCRIPT_SUFFIX)) {
          partName = partName.substring(0, partName.length() - SCRIPT_SUFFIX.length());
        }
        VirtualFile sitePy = root.findFileByRelativePath("parts/" + partName + "/site.py");
        if (sitePy != null) {
          paths = extractFromSitePy(sitePy);
        }
      }
      return paths;
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  /**
   * Extracts paths from given script, assuming sys.path[0:0] assignment.
   *
   * @param script
   * @return extracted paths, or null if extraction fails.
   */
  @Nullable
  public static List<String> extractFromScript(@NotNull VirtualFile script) throws IOException {
    String text = VfsUtil.loadText(script);
    Pattern pat = Pattern.compile("(?:^\\s*(['\"])(.*)(\\1),\\s*$)|(\\])", Pattern.MULTILINE);
    final String bait_string = "sys.path[0:0]";
    int pos = text.indexOf(bait_string);
    List<String> ret = null;
    if (pos >= 0) {
      pos += bait_string.length();
      Matcher scanner = pat.matcher(text);
      while (scanner.find(pos)) {
        String value = scanner.group(2);
        if (value != null) {
          if (ret == null) {
            ret = new ArrayList<>();
          }
          ret.add(value);
          pos = scanner.end();
        }
        else {
          break;
        } // we've matched the ']', it's group(4)
      }
    }
    return ret;
  }

  /**
   * Extracts paths from site.py generated by buildout 1.5+
   *
   * @param vFile path to site.py
   * @return extracted paths
   */
  public static List<String> extractFromSitePy(VirtualFile vFile) throws IOException {
    List<String> result = new ArrayList<>();
    String text = VfsUtil.loadText(vFile);
    String[] lines = LineTokenizer.tokenize(text, false);
    int index = 0;
    while (index < lines.length && !lines[index].startsWith("def addsitepackages(")) {
      index++;
    }
    while (index < lines.length && !lines[index].trim().startsWith("buildout_paths = [")) {
      index++;
    }
    index++;
    while (index < lines.length && !lines[index].trim().equals("]")) {
      String line = lines[index].trim();
      line = StringUtil.trimEnd(line, ",");
      if (line.startsWith("'") && line.endsWith("'")) {
        result.add(StringUtil.unescapeStringCharacters(line.substring(1, line.length() - 1)));
      }
      index++;
    }
    return result;
  }

  @Override
  public List<String> getAdditionalPythonPath() {
    BuildoutFacetConfiguration cfg = getConfiguration();
    return cfg.getPaths();
  }

  @Override
  public boolean acceptRootAsTopLevelPackage() {
    return false;
  }

  @Nullable
  public static BuildoutFacet getInstance(Module module) {
    return FacetManager.getInstance(module).getFacetByType(BuildoutFacetType.ID);
  }

  public void patchCommandLineForBuildout(GeneralCommandLine commandLine) {
    Map<String, String> env = commandLine.getEnvironment();
    ParametersList params = commandLine.getParametersList();
    // alter execution script
    ParamsGroup scriptParams = params.getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert scriptParams != null;
    if (scriptParams.getParameters().size() > 0) {
      String normalScript = scriptParams.getParameters().get(0); // expect DjangoUtil.MANAGE_FILE
      HelperPackage engulfer = PythonHelper.BUILDOUT_ENGULFER;
      env.put("PYCHARM_ENGULF_SCRIPT", getConfiguration().getScriptName());
      scriptParams.getParametersList().replaceOrPrepend(normalScript, engulfer.asParamString());
    }
    // add pycharm helpers to pythonpath so that fixGetpass is importable

    PythonEnvUtil.addToPythonPath(env, PythonHelpersLocator.getHelpersRoot().getAbsolutePath());
    /*
    // set prependable paths
    List<String> paths = facet.getAdditionalPythonPath();
    if (paths != null) {
      path_value = PyUtil.joinWith(File.pathSeparator, paths);
      env.put("PYCHARM_PREPEND_SYSPATH", path_value);
    }
    */
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
    if (cfg != null && cfg.exists()) {
      try {
        // this method is called before the project initialization is complete, so it has to use createFileFromText() instead
        // of PsiManager.findFile()
        String text = FileUtil.loadFile(cfg);
        final PsiFile configFile = PsiFileFactory
          .getInstance(getModule().getProject()).createFileFromText("buildout.cfg",
                                                                    BuildoutCfgLanguage.INSTANCE, text);
        if (configFile != null && configFile instanceof BuildoutCfgFile) {
          return (BuildoutCfgFile)configFile;
        }
      }
      catch (Exception ignored) {
      }
    }
    return null;
  }

  public static List<File> getScripts(@Nullable BuildoutFacet buildoutFacet, final VirtualFile baseDir) {
    File rootPath = null;
    if (buildoutFacet != null) {
      final File configIOFile = buildoutFacet.getConfigFile();
      if (configIOFile != null) {
        rootPath = configIOFile.getParentFile();
      }
    }
    if (rootPath == null || !rootPath.exists()) {
      if (baseDir != null) {
        rootPath = new File(baseDir.getPath());
      }
    }
    if (rootPath != null) {
      final File[] scripts = new File(rootPath, "bin").listFiles((dir, name) -> {
        if (SystemInfo.isWindows) {
          return name.endsWith("-script.py");
        }
        String ext = FileUtilRt.getExtension(name);
        return ext.length() == 0 || FileUtil.namesEqual(ext, "py");
      });
      if (scripts != null) {
        return Arrays.asList(scripts);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static File findScript(@Nullable BuildoutFacet buildoutFacet, String name, final VirtualFile baseDir) {
    String scriptName = SystemInfo.isWindows ? name + SCRIPT_SUFFIX : name;
    final List<File> scripts = getScripts(buildoutFacet, baseDir);
    for (File script : scripts) {
      if (FileUtil.getNameWithoutExtension(script.getName()).equals(scriptName)) {
        return script;
      }
    }
    return null;
  }

  public static void attachLibrary(final Module module) {
    final BuildoutFacet facet = getInstance(module);
    if (facet == null) {
      return;
    }
    final List<String> paths = facet.getConfiguration().getPaths();
    FacetLibraryConfigurator.attachPythonLibrary(module, null, BUILDOUT_LIB_NAME, paths);
  }

  public static void detachLibrary(final Module module) {
    FacetLibraryConfigurator.detachPythonLibrary(module, BUILDOUT_LIB_NAME);
  }
}
