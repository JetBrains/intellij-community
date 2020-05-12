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
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
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
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.net.NetUtils;
import gnu.trove.THashMap;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.lang.xpath.xslt.run.XsltRunConfiguration;
import org.intellij.lang.xpath.xslt.run.XsltRunnerExtension;
import org.intellij.plugins.xsltDebugger.ui.OutputTabComponent;
import org.intellij.plugins.xsltDebugger.ui.StructureTabComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
public class XsltDebuggerExtension extends XsltRunnerExtension {
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
    if (version == null || version.feature < 5 || version.feature > 8) {  // todo: get rid of PortableRemoteObject usages in debugger
      throw new CantRunException("The XSLT Debugger requires Java 1.5 - 1.8 to run");
    }

    // TODO: fix and remove
    if (configuration.getOutputType() != XsltRunConfiguration.OutputType.CONSOLE) {
      throw new CantRunException("XSLT Debugger requires Output Type == CONSOLE");
    }

    try {
      final int port = NetUtils.findAvailableSocketPort();
      parameters.getVMParametersList().defineProperty("xslt.debugger.port", String.valueOf(port));
      extensionData.putUserData(PORT, port);
    } catch (IOException e) {
      LOG.info(e);
      throw new CantRunException("Unable to find a free network port");
    }

    String token = UUID.randomUUID().toString();
    parameters.getVMParametersList().defineProperty("xslt.debugger.token", token);
    extensionData.putUserData(ACCESS_TOKEN, token);

    final PluginId pluginId = PluginManagerCore.getPluginByClassName(getClass().getName());
    assert pluginId != null || System.getProperty("xslt-debugger.plugin.path") != null
      : "PluginId not found - development builds need to specify -Dxslt-debugger.plugin.path=../out/classes/production/intellij.xslt.debugger.engine";

    Path pluginPath;
    if (pluginId != null) {
      IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
      assert descriptor != null;
      pluginPath = descriptor.getPluginPath();
    }
    else {
      pluginPath = Paths.get(System.getProperty("xslt-debugger.plugin.path"));
    }

    Path rtClasspath = pluginPath.resolve("lib/xslt-debugger-engine.jar");
    if (Files.exists(rtClasspath)) {
      parameters.getClassPath().addTail(rtClasspath.toAbsolutePath().toString());

      Path rmiStubs = pluginPath.resolve("lib/rmi-stubs.jar");
      assert Files.exists(rmiStubs) : rmiStubs.toAbsolutePath().toString();
      parameters.getClassPath().addTail(rmiStubs.toAbsolutePath().toString());

      Path engineImpl = pluginPath.resolve("lib/rt/xslt-debugger-engine-impl.jar");
      assert Files.exists(engineImpl) : engineImpl.toAbsolutePath().toString();
      parameters.getClassPath().addTail(engineImpl.toAbsolutePath().toString());
    }
    else {
      rtClasspath = pluginPath.resolve("classes");
      if (!Files.exists(rtClasspath)) {
        if (ApplicationManager.getApplication().isInternal() && Files.exists(pluginPath.resolve("org"))) {
          rtClasspath = pluginPath;
          Path engineImplInternal = pluginPath.getParent().resolve("intellij.xslt.debugger.engine.impl");
          assert Files.exists(engineImplInternal) : engineImplInternal.toAbsolutePath().toString();
          parameters.getClassPath().addTail(engineImplInternal.toAbsolutePath().toString());
        }
        else {
          throw new CantRunException("Runtime classes not found at " + rtClasspath.toAbsolutePath().toString());
        }
      }

      parameters.getClassPath().addTail(rtClasspath.toAbsolutePath().toString());

      Path rmiStubs = rtClasspath.resolve("rmi-stubs.jar");
      assert Files.exists(rmiStubs) : rmiStubs.toAbsolutePath().toString();
      parameters.getClassPath().addTail(rmiStubs.toAbsolutePath().toString());
    }

    File trove4j = new File(PathUtil.getJarPathForClass(THashMap.class));
    parameters.getClassPath().addTail(trove4j.getAbsolutePath());

    String type = parameters.getVMParametersList().getPropertyValue("xslt.transformer.type");
    if ("saxon".equalsIgnoreCase(type)) {
      addSaxon(parameters, pluginPath, SAXON_6_JAR);
    }
    else if ("saxon9".equalsIgnoreCase(type)) {
      addSaxon(parameters, pluginPath, SAXON_9_JAR);
    }
    else if ("xalan".equalsIgnoreCase(type)) {
      final Boolean xalanPresent = isValidXalanPresent(parameters);
      if (xalanPresent == null) {
        addXalan(parameters, pluginPath);
      }
      else if (!xalanPresent) {
        throw new CantRunException("Unsupported Xalan version is present in classpath.");
      }
    }
    else if (type != null) {
      throw new CantRunException("Unsupported Transformer type '" + type + "'");
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
        addSaxon(parameters, pluginPath, SAXON_9_JAR);
      } else {
        parameters.getVMParametersList().defineProperty("xslt.transformer.type", "saxon");
        addSaxon(parameters, pluginPath, SAXON_6_JAR);
      }
    }

    parameters.getVMParametersList().defineProperty("xslt.main", "org.intellij.plugins.xsltDebugger.rt.XSLTDebuggerMain");
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

  private static void addXalan(SimpleJavaParameters parameters, Path pluginPath) {
    Path xalan = findTransformerJar(pluginPath, "xalan-2.7.2.jar").toAbsolutePath();
    parameters.getClassPath().addTail(xalan.toString());
    parameters.getClassPath().addTail(xalan.getParent().resolve("serializer-2.7.2.jar").toString());
  }

  private static void addSaxon(SimpleJavaParameters parameters, Path pluginPath, final String saxonJar) {
    Path saxon = findTransformerJar(pluginPath, saxonJar);
    parameters.getClassPath().addTail(saxon.toAbsolutePath().toString());
  }

  private static Path findTransformerJar(Path pluginPath, String jarFile) {
    Path transformerFile = pluginPath.resolve("lib/rt").resolve(jarFile);
    if (!Files.exists(transformerFile)) {
      transformerFile = pluginPath.resolve("lib").resolve(jarFile);
      if (!Files.exists(transformerFile)) {
        transformerFile = pluginPath.getParent().resolve("intellij.xslt.debugger.engine.impl").resolve(jarFile);
        if (!Files.exists(transformerFile)) {
          transformerFile = pluginPath.resolve(jarFile);
          assert Files.exists(transformerFile) : transformerFile.toAbsolutePath().toString();
        }
      }
    }
    return transformerFile;
  }
}
