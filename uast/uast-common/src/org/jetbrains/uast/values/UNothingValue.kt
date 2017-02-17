/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.uast.values

import org.jetbrains.uast.*

// Something that never can be reached / created
internal class UNothingValue private constructor(
        val containingLoopOrSwitch: UExpression?,
        val kind: JumpKind
) : UValueBase() {

    constructor(jump: UJumpExpression) : this(jump.containingLoopOrSwitch(), jump.kind())

    constructor() : this(null, JumpKind.OTHER)

    enum class JumpKind {
        BREAK,
        CONTINUE,
        OTHER;
    }

    override val reachable = false

    override fun merge(other: UValue) = when (other) {
        is UNothingValue -> {
            val mergedLoopOrSwitch =
                    if (containingLoopOrSwitch == other.containingLoopOrSwitch) containingLoopOrSwitch
                    else null
            val mergedKind = if (mergedLoopOrSwitch == null || kind != other.kind) JumpKind.OTHER else kind
            UNothingValue(mergedLoopOrSwitch, mergedKind)
        }
        else -> super.merge(other)
    }

    override fun toString() = "Nothing" + when (kind) {
        JumpKind.BREAK -> "(break)"
        JumpKind.CONTINUE -> "(continue)"
        else -> ""
    }

    companion object {
        private fun UJumpExpression.containingLoopOrSwitch(): UExpression? {
            var containingElement = uastParent
            while (containingElement != null) {
                if (this is UBreakExpression && label == null && containingElement is USwitchExpression) {
                    return containingElement
                }
                if (containingElement is ULoopExpression) {
                    val containingLabeled = containingElement.uastParent as? ULabeledExpression
                    if (label == null || label == containingLabeled?.label) {
                        return containingElement
                    }
                }
                containingElement = containingElement.uastParent
            }
            return null
        }

        private fun UExpression.kind(): JumpKind = when (this) {
            is UBreakExpression -> JumpKind.BREAK
            is UContinueExpression -> JumpKind.CONTINUE
            else -> JumpKind.OTHER
        }
    }
}
