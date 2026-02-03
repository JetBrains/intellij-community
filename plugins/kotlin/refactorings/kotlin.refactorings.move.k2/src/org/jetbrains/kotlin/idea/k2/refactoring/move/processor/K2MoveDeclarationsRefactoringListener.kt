// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor

@ApiStatus.Internal
/**
 * [beforeMove] and [afterMove] are called in a single write action with the same argument.
 */
interface K2MoveDeclarationsRefactoringListener {
    fun beforeMove(moveDescriptor: K2MoveDescriptor.Declarations)
    fun afterMove(moveDescriptor: K2MoveDescriptor.Declarations)

    companion object {
        val TOPIC: Topic<K2MoveDeclarationsRefactoringListener> = Topic.create<K2MoveDeclarationsRefactoringListener>(
            K2MoveDeclarationsRefactoringListener::class.java.simpleName,
            K2MoveDeclarationsRefactoringListener::class.java
        )
    }
}
