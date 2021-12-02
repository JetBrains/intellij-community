// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.util.ExternalProject
import org.jetbrains.kotlin.idea.perf.util.OutputConfig
import org.jetbrains.kotlin.idea.perf.util.lastPathSegment
import org.jetbrains.kotlin.idea.testFramework.Fixture
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

data class CursorConfig(
    val fixture: Fixture,
    var marker: String? = null,
    var finalMarker: String? = null
) : AutoCloseable {

    fun select() {
        fixture.selectMarkers(marker, finalMarker)
    }

    override fun close() {
        fixture.close()
    }
}

data class TypingConfig(
    val fixture: Fixture,
    /**
     * Find the place in a file and place cursor at [marker],
     * If [typeAfterMarker] is true cursor is placed after the [marker],
     * otherwise at the beggining of the [marker].
     */
    var marker: String? = null,
    var typeAfterMarker: Boolean = true,
    var insertString: String? = null,
    var surroundItems: String = "\n",
    var note: String = "",
    var delayMs: Long? = null
) : AutoCloseable {
    fun moveCursor() {
        val editor = fixture.editor

        val tasksIdx = marker?.let { marker ->
            fixture.text.indexOf(marker).also {
                check(it > 0) { "marker '$marker' not found in ${fixture.fileName}" }
            }
        } ?: 0
        if (typeAfterMarker || marker == null) {
            editor.caretModel.moveToOffset(tasksIdx + (marker?.let { it.length + 1 } ?: 0))
        } else {
            editor.caretModel.moveToOffset(tasksIdx - 1)
        }

        for (surroundItem in surroundItems) {
            EditorTestUtil.performTypingAction(editor, surroundItem)
        }

        editor.caretModel.moveToOffset(editor.caretModel.offset - if (typeAfterMarker) 1 else 2)

        if (!typeAfterMarker) {
            for (surroundItem in surroundItems) {
                EditorTestUtil.performTypingAction(editor, surroundItem)
            }
            editor.caretModel.moveToOffset(editor.caretModel.offset - 2)
        }
    }

    override fun close() {
        fixture.close()
    }
}

class StatsScopeConfig(
    var name: String? = null,
    var warmup: Int = 2,
    var iterations: Int = 5,
    var fastIterations: Boolean = false,
    var outputConfig: OutputConfig = OutputConfig(),
    var profilerConfig: ProfilerConfig = ProfilerConfig()
)

class ProjectScopeConfig(val path: String, val openWith: ProjectOpenAction, val refresh: Boolean = false, private val name: String? = null) {
    val projectName: String = name ?: path.lastPathSegment()

    constructor(externalProject: ExternalProject, refresh: Boolean) : this(externalProject.path, externalProject.openWith, refresh)
}