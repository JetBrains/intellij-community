// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiManager;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService;
import org.jetbrains.kotlin.idea.caches.trackers.KotlinIDEModificationTrackerService;

public class ClearSourceCaches extends AbstractCommand {

    public static final String PREFIX = CMD_PREFIX + "clearSourceCaches";

    public ClearSourceCaches(@NotNull String text, int line) {
        super(text, line);
    }

    @Override
    protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
        final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
        WriteAction.runAndWait(() -> {
            Project project = context.getProject();
            PsiManager.getInstance(project).dropResolveCaches();
            PsiManager.getInstance(project).dropPsiCaches();
            KotlinIDEModificationTrackerService.Companion.invalidateCaches(project);
            if (System.getProperty("idea.kotlin.plugin.use.k2", "false").equals("true")) {
                KotlinGlobalModificationService.Companion.getInstance(project).publishGlobalSourceModuleStateModification();
            }
            actionCallback.setDone();
        });
        return Promises.toPromise(actionCallback);
    }
}
