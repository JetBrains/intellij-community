package com.intellij.python.processOutput.frontend

import androidx.compose.ui.util.fastMaxOfOrDefault
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import kotlin.enums.enumEntries

internal interface ConsoleTag {
    val text: String
}

internal class ConsoleTagFormatter<TTag> private constructor(private val maxLength: Int)
    where TTag : ConsoleTag, TTag : Enum<TTag> {
    private val bracketPadding = maxLength + 3 // opening bracket, closing bracket, and space
    private val colonPadding = maxLength + 1 // colon

    fun bracketedTagString(value: TTag): String = "[${value.text}] ".padStart(bracketPadding)
    fun colonTagString(value: TTag): String = "${value.text}:".padStart(colonPadding)
    val blankBracketTagString: String = " ".repeat(bracketPadding)

    companion object {
        inline fun <reified TTag> create() where TTag : ConsoleTag, TTag : Enum<TTag> =
            ConsoleTagFormatter<TTag>(enumEntries<TTag>().fastMaxOfOrDefault(0) { it.text.length })
    }
}

internal enum class OutputTag(override val text: String) : ConsoleTag {
    ERROR(message("process.output.output.tag.stderr")),
    OUTPUT(message("process.output.output.tag.stdout")),
    EXIT(message("process.output.output.tag.exit"));

    companion object {
        val formatter = ConsoleTagFormatter.create<OutputTag>()
    }
}

internal enum class InfoTag(override val text: String) : ConsoleTag {
    STARTED(message("process.output.output.sections.info.started")),
    COMMAND(message("process.output.output.sections.info.command")),
    PID(message("process.output.output.sections.info.pid")),
    CWD(message("process.output.output.sections.info.cwd")),
    TARGET(message("process.output.output.sections.info.target")),
    ENV(message("process.output.output.sections.info.env"));

    companion object {
        val formatter = ConsoleTagFormatter.create<InfoTag>()
    }
}
