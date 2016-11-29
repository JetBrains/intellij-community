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

package org.intellij.lang.xpath.xslt.run;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.AdditionalTabComponentManager;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.06.2007
 */
public abstract class XsltRunnerExtension {
    public static final ExtensionPointName<XsltRunnerExtension> EXTENSION_POINT_NAME = ExtensionPointName.create("XPathView.xsltRunnerExtension");

    
    public abstract ProcessListener createProcessListener(Project project, UserDataHolder extensionData);

    /**
     * Add additional tabs in XSLT runner's run console.
     */
    public abstract boolean createTabs(Project project, AdditionalTabComponentManager manager, AdditionalTabComponent outputConsole, ProcessHandler process);

    public abstract void patchParameters(SimpleJavaParameters parameters, XsltRunConfiguration xsltCommandLineState, UserDataHolder extensionData) throws CantRunException;


    protected abstract boolean supports(XsltRunConfiguration config, boolean debugger);

    @NotNull
    public static List<XsltRunnerExtension> getExtensions(XsltRunConfiguration config, boolean debugger) {
        final XsltRunnerExtension[] extensions = Extensions.getExtensions(EXTENSION_POINT_NAME);
        final ArrayList<XsltRunnerExtension> list = new ArrayList<>(extensions.length);
        for (XsltRunnerExtension extension : extensions) {
            if (extension.supports(config, debugger)) {
                list.add(extension);
            }
        }
        return list;
    }
}
