// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.suite.TypePosition.*
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

enum class TypePosition {
    AFTER_MARKER,
    IN_FRONT_OF_MARKER
}

data class TypingConfig(
    val fixture: Fixture,
    var marker: String? = null,
    var typePosition: TypePosition = AFTER_MARKER,
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
        if (typePosition == AFTER_MARKER || marker == null) {
            editor.caretModel.moveToOffset(tasksIdx + (marker?.let { it.length + 1 } ?: 0))
        } else {
            editor.caretModel.moveToOffset(tasksIdx - 1)
        }

        for (surroundItem in surroundItems) {
            EditorTestUtil.performTypingAction(editor, surroundItem)
        }

        editor.caretModel.moveToOffset(editor.caretModel.offset - if (typePosition == AFTER_MARKER) 1 else 2)

        if (typePosition == IN_FRONT_OF_MARKER) {
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
    /**
     * results into an error if deviation is more than [stabilityWatermark] percentage,
     * check is disabled if it is <code>null</code>
     */
    var stabilityWatermark: Int? = 20,
    var fastIterations: Boolean = false,
    var outputConfig: OutputConfig = OutputConfig(),
    var profilerConfig: ProfilerConfig = ProfilerConfig()
)

class ProjectScopeConfig(val path: String, val openWith: ProjectOpenAction, val refresh: Boolean = false, name: String? = null) {
    val projectName: String = name ?: path.lastPathSegment()

    constructor(externalProject: ExternalProject, refresh: Boolean) : this(externalProject.path, externalProject.openWith, refresh)
}