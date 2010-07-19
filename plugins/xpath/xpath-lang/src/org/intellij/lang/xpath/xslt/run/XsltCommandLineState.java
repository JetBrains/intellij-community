/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.net.NetUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.intellij.lang.xpath.xslt.run.XsltRunConfiguration.isEmpty;

class XsltCommandLineState extends CommandLineState {
    private static final Logger LOG = Logger.getInstance(XsltCommandLineState.class.getName());
    public static final Key<XsltCommandLineState> STATE = Key.create("STATE");

    private final XsltRunConfiguration myXsltRunConfiguration;
    private final boolean myIsDebugger;
    private int myPort;
    private UserDataHolder myExtensionData;

    @SuppressWarnings({ "RawUseOfParameterizedType" })
    public XsltCommandLineState(XsltRunConfiguration xsltRunConfiguration, ExecutionEnvironment env) {
        super(env);
        myXsltRunConfiguration = xsltRunConfiguration;
        final ConfigurationPerRunnerSettings settings = env.getConfigurationSettings();
        myIsDebugger = settings != null && "Debug".equals(settings.getRunnerId());
    }

    protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler osProcessHandler = createJavaParameters().createOSProcessHandler();
        osProcessHandler.putUserData(STATE, this);

        osProcessHandler.addProcessListener(new MyProcessAdapter());

        final List<XsltRunnerExtension> extensions = XsltRunnerExtension.getExtensions(myXsltRunConfiguration, myIsDebugger);
        for (XsltRunnerExtension extension : extensions) {
            osProcessHandler.addProcessListener(extension.createProcessListener(myXsltRunConfiguration.getProject(), myExtensionData));
        }
        return osProcessHandler;
    }

    protected SimpleJavaParameters createJavaParameters() throws ExecutionException {
        final Sdk jdk = myXsltRunConfiguration.getEffectiveJDK();
        if (jdk == null) {
            throw CantRunException.noJdkConfigured();
        }

        final SimpleJavaParameters parameters = new SimpleJavaParameters();
        parameters.setJdk(jdk);

        if (myXsltRunConfiguration.getJdkChoice() == XsltRunConfiguration.JdkChoice.FROM_MODULE) {
            final Module module = myXsltRunConfiguration.getEffectiveModule();
            // relaxed check for valid module: when running XSLTs that don't belong to any module, let's assume it is
            // OK to run as if just a JDK has been selected (a missing JDK would already have been complained about above) 
            if (module != null) {
              OrderEnumerator.orderEntries(module).productionOnly().recursively().classes().collectPaths(parameters.getClassPath());
            }
        }

        final ParametersList vmParameters = parameters.getVMParametersList();
        vmParameters.addParametersString(myXsltRunConfiguration.myVmArguments);
        if (isEmpty(myXsltRunConfiguration.getXsltFile())) {
            throw new CantRunException("No XSLT file selected");
        }
        vmParameters.defineProperty("xslt.file", myXsltRunConfiguration.getXsltFile());
        if (isEmpty(myXsltRunConfiguration.getXmlInputFile())) {
            throw new CantRunException("No XML input file selected");
        }
        vmParameters.defineProperty("xslt.input", myXsltRunConfiguration.getXmlInputFile());

        final XsltRunConfiguration.OutputType outputType = myXsltRunConfiguration.getOutputType();
        if (outputType == XsltRunConfiguration.OutputType.CONSOLE) {
            try {
                myPort = NetUtils.findAvailableSocketPort();
            } catch (IOException e) {
                //noinspection deprecation
                myPort = myXsltRunConfiguration.myRunnerPort;
            }
            vmParameters.defineProperty("xslt.listen-port", String.valueOf(myPort));
        } 
        if (myXsltRunConfiguration.isSaveToFile()) {
            vmParameters.defineProperty("xslt.output", myXsltRunConfiguration.myOutputFile);
        }

        for (Pair<String, String> pair : myXsltRunConfiguration.getParameters()) {
            final String name = pair.getFirst();
            final String value = pair.getSecond();
            if (isEmpty(name) || value == null) continue;
            vmParameters.defineProperty("xslt.param." + name, value);
        }
        vmParameters.defineProperty("xslt.smart-error-handling", String.valueOf(myXsltRunConfiguration.mySmartErrorHandling));

        final PluginId pluginId = PluginManager.getPluginByClassName(getClass().getName());
        assert pluginId != null || System.getProperty("xslt.plugin.path") != null;

        final File pluginPath;
        if (pluginId != null) {
            final IdeaPluginDescriptor descriptor = PluginManager.getPlugin(pluginId);
            assert descriptor != null;
            pluginPath = descriptor.getPath();
        } else {
            pluginPath = new File(System.getProperty("xslt.plugin.path"));
        }

        LOG.debug("Plugin Path = " + pluginPath.getAbsolutePath());

        final char c = File.separatorChar;
        File rtClasspath = new File(pluginPath, "lib" + c + "rt" + c + "xslt-rt.jar");
//        File rtClasspath = new File("C:/Demetra/plugins/xpath/lib/rt/xslt-rt.jar");
        if (!rtClasspath.exists()) {
            LOG.warn("Plugin's Runtime classes not found in " + rtClasspath.getAbsolutePath());
            if (!(rtClasspath = new File(pluginPath, "classes")).exists()) {
                throw new CantRunException("Runtime classes not found");
            }
            parameters.getVMParametersList().prepend("-ea");
        }
        parameters.getClassPath().addTail(rtClasspath.getAbsolutePath());

        parameters.setMainClass("org.intellij.plugins.xslt.run.rt.XSLTRunner");

        parameters.setWorkingDirectory(isEmpty(myXsltRunConfiguration.myWorkingDirectory) ?
                new File(myXsltRunConfiguration.getXsltFile()).getParentFile().getAbsolutePath() :
                myXsltRunConfiguration.myWorkingDirectory);

        myExtensionData = new UserDataHolderBase();
        final List<XsltRunnerExtension> extensions = XsltRunnerExtension.getExtensions(myXsltRunConfiguration, myIsDebugger);
        for (XsltRunnerExtension extension : extensions) {
            extension.patchParameters(parameters, myXsltRunConfiguration, myExtensionData);
        }

        return parameters;
    }

    public int getPort() {
        return myPort;
    }

    private class MyProcessAdapter extends ProcessAdapter {

        public void processTerminated(final ProcessEvent event) {

            if (myXsltRunConfiguration.isSaveToFile()) {
                Runnable runnable = new Runnable() {
                    public void run() {
                        Runnable runnable = new Runnable() {
                            public void run() {
                                if (event.getExitCode() == 0) {
                                    if (myXsltRunConfiguration.myOpenInBrowser) {
                                        BrowserUtil.launchBrowser(myXsltRunConfiguration.myOutputFile);
                                    }
                                    if (myXsltRunConfiguration.myOpenOutputFile) {
                                        final String url = VfsUtil.pathToUrl(myXsltRunConfiguration.myOutputFile);
                                        final VirtualFile fileByUrl = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url.replace(File.separatorChar, '/'));
                                        if (fileByUrl != null) {
                                            fileByUrl.refresh(false, false);
                                            new OpenFileDescriptor(myXsltRunConfiguration.getProject(), fileByUrl).navigate(true);
                                            return;
                                        }
                                    }
                                    VirtualFileManager.getInstance().refresh(true);
                                }
                            }
                        };
                        ApplicationManager.getApplication().runWriteAction(runnable);
                    }
                };
                SwingUtilities.invokeLater(runnable);
            }
        }
    }
}
