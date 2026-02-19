// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

interface KotlinRefactoringListener {
    companion object {
        val EVENT_TOPIC: Topic<KotlinRefactoringListener> = Topic.create("KOTLIN_REFACTORING_EVENT_TOPIC", KotlinRefactoringListener::class.java)

        @ApiStatus.Internal
        fun broadcastRefactoringExit(project: Project, refactoringId: String) {
            project.messageBus.syncPublisher(EVENT_TOPIC).onRefactoringExit(refactoringId)
        }
    }

    /**
     * This function is invoked after a refactoring with the [refactoringId] has finished (either successful or otherwise)
     */
    fun onRefactoringExit(refactoringId: String)
}
