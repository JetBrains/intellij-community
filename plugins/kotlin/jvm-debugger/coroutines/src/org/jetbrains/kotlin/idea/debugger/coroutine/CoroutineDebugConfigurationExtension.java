// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine;

import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CoroutineDebugConfigurationExtension extends RunConfigurationExtension {
    private static final Logger log = Logger.getInstance(CoroutineDebugConfigurationExtension.class);

    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(
            @NotNull T configuration,
            @NotNull JavaParameters params,
            RunnerSettings runnerSettings
    ) {
        Project project = configuration.getProject();
        DebuggerListener listener = project.getService(DebuggerListener.class);
        if (listener != null) {
            listener.registerDebuggerConnection(configuration, params, runnerSettings);
        } else {
            log.error("DebuggerListener service is not found in project " + project.getName());
        }
    }

    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return true;
    }
}
