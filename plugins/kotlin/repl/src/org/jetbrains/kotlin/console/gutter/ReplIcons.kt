// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console.gutter

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

data class IconWithTooltip(val icon: Icon, @NlsContexts.Tooltip val tooltip: String?)

object ReplIcons {
    val BUILD_WARNING_INDICATOR: IconWithTooltip = IconWithTooltip(AllIcons.General.Warning, null)
    val HISTORY_INDICATOR: IconWithTooltip = IconWithTooltip(
        AllIcons.Vcs.History,
        KotlinIdeaReplBundle.message("icon.tool.tip.history.of.executed.commands")
    )

    val EDITOR_INDICATOR: IconWithTooltip = IconWithTooltip(
        KotlinIcons.LAUNCH,
        KotlinIdeaReplBundle.message("icon.tool.tip.write.your.commands.here")
    )

    val EDITOR_READLINE_INDICATOR: IconWithTooltip = IconWithTooltip(
        AllIcons.General.Balloon,
        KotlinIdeaReplBundle.message("icon.tool.tip.waiting.for.input")
    )

    val COMMAND_MARKER: IconWithTooltip = IconWithTooltip(
        AllIcons.RunConfigurations.TestState.Run,
        KotlinIdeaReplBundle.message("icon.tool.tip.executed.command")
    )

    val READLINE_MARKER: IconWithTooltip = IconWithTooltip(
        AllIcons.Debugger.PromptInput,
        KotlinIdeaReplBundle.message("icon.tool.tip.input.line")
    )

    // command result icons
    val SYSTEM_HELP: IconWithTooltip = IconWithTooltip(AllIcons.Actions.Help, KotlinIdeaReplBundle.message("icon.tool.tip.system.help"))
    val RESULT: IconWithTooltip = IconWithTooltip(AllIcons.Vcs.Equal, KotlinIdeaReplBundle.message("icon.tool.tip.result"))
    val COMPILER_ERROR: Icon = AllIcons.General.Error
    val RUNTIME_EXCEPTION: IconWithTooltip = IconWithTooltip(
        AllIcons.General.BalloonWarning,
        KotlinIdeaReplBundle.message("icon.tool.tip.runtime.exception")
    )
}