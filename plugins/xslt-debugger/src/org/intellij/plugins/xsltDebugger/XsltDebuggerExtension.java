/*
 * Copyright 2002-2007 Sascha Weinreuter
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
package org.intellij.plugins.xsltDebugger;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsoleManagerBase;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.AdditionalTabComponentManager;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.RunTab;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.net.NetUtils;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.lang.xpath.xslt.run.XsltRunConfiguration;
import org.intellij.lang.xpath.xslt.run.XsltRunnerExtension;
import org.intellij.plugins.xsltDebugger.ui.OutputTabComponent;
import org.intellij.plugins.xsltDebugger.ui.StructureTabComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Extension for XPathView that hooks into the execution of XSLT-scripts. Manages the creation of the XSLT-Debugger UI
 * and ensures the debugged process is started with the required JVM properties.
 */
public final class XsltDebuggerExtension extends XsltRunnerExtension {
  private static final Logger LOG = Logger.getInstance(XsltDebuggerExtension.class.getName());

  public static final Key<XsltChecker.LanguageLevel> VERSION = Key.create("VERSION");
  private static final Key<Integer> PORT = Key.create("PORT");
  private static final Key<Manifest> MANIFEST = Key.create("MANIFEST");
  private static final Key<String> ACCESS_TOKEN = Key.create("access token");

  @NonNls
  private static final String SAXON_6_JAR = "saxon.jar";

  @NonNls
  private static final String SAXON_9_JAR = "saxon9he.jar";

  @Override
  protected boolean supports(XsltRunConfiguration config, boolean debugger) {
    return (debugger || XsltDebuggerRunner.ACTIVE.get() == Boolean.TRUE) &&
           config.getOutputType() == XsltRunConfiguration.OutputType.CONSOLE; // ?
  }

  @Override
  public ProcessListener createProcessListener(Project project, UserDataHolder extensionData) {
    final Integer port = extensionData.getUserData(PORT);
    assert port != null;
    return new DebugProcessListener(project, port, extensionData.getUserData(ACCESS_TOKEN));
  }

  @Override
  public boolean createTabs(Project project,
                            AdditionalTabComponentManager manager,
                            @NotNull AdditionalTabComponent outputConsole,
                            ProcessHandler process) {
    if (manager instanceof RunTab) {
      LogConsoleManagerBase runTab = ((RunTab)manager).getLogConsoleManager();
      runTab.addAdditionalTabComponent(new OutputTabComponent(outputConsole), "XSLT-Output", AllIcons.Debugger.Console);
      runTab.addAdditionalTabComponent(StructureTabComponent.create(process, outputConsole), "XSLT-Structure", PlatformIcons.FLATTEN_PACKAGES_ICON);
    }
    else {
      manager.addAdditionalTabComponent(new OutputTabComponent(outputConsole), "XSLT-Output");
      manager.addAdditionalTabComponent(StructureTabComponent.create(process, outputConsole), "XSLT-Structure");
    }
    return true;
  }

  @Override
  public void patchParameters(SimpleJavaParameters parameters, XsltRunConfiguration configuration, UserDataHolder extensionData) throws CantRunException {
    final Sdk jdk = configuration.getEffectiveJDK();
    assert jdk != null;
    final JavaVersion version = JavaVersion.tryParse(jdk.getVersionString());
    if (version == null || version.feature < 5) {
      throw new CantRunException(XsltDebuggerBundle.message("dialog.message.xslt.debugger.requires.java.to.run"));
    }

    // TODO: fix and remove
    if (configuration.getOutputType() != XsltRunConfiguration.OutputType.CONSOLE) {
      throw new CantRunException(XsltDebuggerBundle.message("dialog.message.xslt.debugger.requires.output.type.console"));
    }

    try {
      final int port = NetUtils.findAvailableSocketPort();
      parameters.getVMParametersList().defineProperty("xslt.debugger.port", String.valueOf(port));
      extensionData.putUserData(PORT, port);
    } catch (IOException e) {
      LOG.info(e);
      throw new CantRunException(XsltDebuggerBundle.message("dialog.message.unable.to.find.free.network.port"));
    }

    String token = UUID.randomUUID().toString();
    parameters.getVMParametersList().defineProperty("xslt.debugger.token", token);
    extensionData.putUserData(ACCESS_TOKEN, token);

    Path xsltDebuggerClassesRoot = Paths.get(PathUtil.getJarPathForClass(getClass()));
    if (!Files.isDirectory(xsltDebuggerClassesRoot)) {
      Path libDirectory = xsltDebuggerClassesRoot.getParent();
      addPathToClasspath(parameters, libDirectory.resolve("xslt-debugger-rt.jar"));
      addPathToClasspath(parameters, libDirectory.resolve("rmi-stubs.jar"));
      addPathToClasspath(parameters, libDirectory.resolve("rt/xslt-debugger-impl-rt.jar"));
    }
    else {
      //running from sources
      Path outProductionDir = xsltDebuggerClassesRoot.getParent();
      addPathToClasspath(parameters, outProductionDir.resolve("intellij.xslt.debugger.rt"));
      addPathToClasspath(parameters, outProductionDir.resolve("intellij.xslt.debugger.impl.rt"));
      addPathToClasspath(parameters, getPluginEngineDirInSources().resolve("lib/rmi-stubs.jar"));
    }

    String type = parameters.getVMParametersList().getPropertyValue("xslt.transformer.type"); //NON-NLS
    if ("saxon".equalsIgnoreCase(type)) {
      addPathToClasspath(parameters, findSaxonJar(xsltDebuggerClassesRoot, SAXON_6_JAR));
    }
    else if ("saxon9".equalsIgnoreCase(type)) {
      addPathToClasspath(parameters, findSaxonJar(xsltDebuggerClassesRoot, SAXON_9_JAR));
    }
    else if ("xalan".equalsIgnoreCase(type)) {
      final Boolean xalanPresent = isValidXalanPresent(parameters);
      if (xalanPresent == null) {
        addXalan(parameters, xsltDebuggerClassesRoot);
      }
      else if (!xalanPresent) {
        throw new CantRunException(XsltDebuggerBundle.message("dialog.message.unsupported.xalan.version.present.in.classpath"));
      }
    }
    else if (type != null) {
      throw new CantRunException(XsltDebuggerBundle.message("dialog.message.unsupported.transformer.type", type));
    }
    else if (StringUtil.toLowerCase(parameters.getClassPath().getPathsString()).contains("xalan")) {
      if (isValidXalanPresent(parameters) == Boolean.TRUE) {
        parameters.getVMParametersList().defineProperty("xslt.transformer.type", "xalan");
      }
    }

    final VirtualFile xsltFile = configuration.findXsltFile();
    final PsiManager psiManager = PsiManager.getInstance(configuration.getProject());
    final XsltChecker.LanguageLevel level;
    if (xsltFile != null) {
      level = XsltSupport.getXsltLanguageLevel(psiManager.findFile(xsltFile));
    }
    else {
      level = XsltChecker.LanguageLevel.V1;
    }
    extensionData.putUserData(VERSION, level);

    if (!parameters.getVMParametersList().hasProperty("xslt.transformer.type")) {
      // add saxon for backward-compatibility
      if (level == XsltChecker.LanguageLevel.V2) {
        parameters.getVMParametersList().defineProperty("xslt.transformer.type", "saxon9");
        addPathToClasspath(parameters, findSaxonJar(xsltDebuggerClassesRoot, SAXON_9_JAR));
      } else {
        parameters.getVMParametersList().defineProperty("xslt.transformer.type", "saxon");
        addPathToClasspath(parameters, findSaxonJar(xsltDebuggerClassesRoot, SAXON_6_JAR));
      }
    }

    parameters.getVMParametersList().defineProperty("xslt.main", "org.intellij.plugins.xsltDebugger.rt.XSLTDebuggerMain");
  }

  private static void addPathToClasspath(SimpleJavaParameters parameters, Path path) {
    Path absolutePath = path.toAbsolutePath();
    assert Files.exists(absolutePath) : absolutePath.toString();
    parameters.getClassPath().addTail(absolutePath.toString());
  }

  @Nullable
  private static Boolean isValidXalanPresent(SimpleJavaParameters parameters) {
    final List<VirtualFile> files = parameters.getClassPath().getVirtualFiles();
    for (VirtualFile file : files) {
      if (file.getName().matches(".*xalan.*\\.jar")) {
        final VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
        final VirtualFile manifestFile = root != null ? root.findFileByRelativePath("META-INF/MANIFEST.MF") : null;
        if (manifestFile != null) {
          try {
            Manifest manifest = manifestFile.getUserData(MANIFEST);
            if (manifest == null) {
              manifest = new Manifest(manifestFile.getInputStream());
              manifestFile.putUserData(MANIFEST, manifest);
            }
            Attributes attributes = manifest.getAttributes("org/apache/xalan/");
            if (attributes == null) {
              attributes = manifest.getAttributes("org/apache/xalan");
            }
            if (attributes == null) {
              LOG.info("No manifest attributes for 'org/apache/xalan/' in " + manifestFile.getPresentableUrl());
              continue;
            }
            final String version = attributes.getValue("Implementation-Version");
            if (version != null) {
              final String[] parts = version.split("\\.");
              if (parts.length >= 2) {
                if (Integer.parseInt(parts[0]) >= 2 && Integer.parseInt(parts[1]) >= 6) {
                  return true;
                }
              }
              LOG.info("Unsupported Xalan version: " + version);
            } else {
              LOG.info("No Xalan version information in " + file.getPath());
            }
          } catch (IOException e) {
            LOG.warn("Unable to read manifest from " + file.getName(), e);
          }
        } else {
          LOG.info("No manifest file in " + file.getPath());
        }
        return false;
      }
    }

    return null;
  }

  private static void addXalan(SimpleJavaParameters parameters, Path xsltDebuggerClassesRoot) {
    if (!Files.isDirectory(xsltDebuggerClassesRoot)) {
      Path rtDir = xsltDebuggerClassesRoot.getParent().resolve("rt");
      addPathToClasspath(parameters, rtDir.resolve("xalan-2.7.2.jar"));
    }
    else {
      //running from sources
      Path xalanInM2 = Paths.get(SystemProperties.getUserHome(), ".m2", "repository", "xalan");
      addPathToClasspath(parameters, xalanInM2.resolve("xalan/2.7.2/xalan-2.7.2.jar"));
      addPathToClasspath(parameters, xalanInM2.resolve("serializer/2.7.2/serializer-2.7.2.jar"));
    }
  }

  private static Path findSaxonJar(Path xsltDebuggerClassesRoot, String jarFile) {
    Path transformerFile = xsltDebuggerClassesRoot.getParent().resolve("rt").resolve(jarFile);
    if (!Files.exists(transformerFile)) {
      //running from sources
      Path libDir = getPluginEngineDirInSources().resolve("impl/lib");
      transformerFile = libDir.resolve(jarFile);
      assert Files.exists(transformerFile) : transformerFile.toAbsolutePath().toString();
    }
    return transformerFile;
  }

  @NotNull
  private static Path getPluginEngineDirInSources() {
    Path path = PluginPathManager.getPluginHome("xslt-debugger").toPath().resolve("engine");
    assert Files.isDirectory(path) : path.toString();
    return path;
  }
}
