// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Maps a Kotlin member that a compiler plugin reshaped at the JVM level back to the runtime member,
 * so the debugger can evaluate and show it. Three shapes: a renamed backing field, a dropped getter
 * whose value moved into a field, an erased value-holder type.
 *
 * Example: kotlinx.atomicfu turns `val _state = atomic<Any?>(...)` into the field `_state$volatile` and
 * drops `get_state()`. So `_state` misses at runtime, as a field (direct or reflective) or as a getter.
 *
 */
interface KotlinMangledMemberContributor {
    /**
     * Runtime field names to try for source field [fieldName] when it is missing at runtime.
     * Covers a direct `GETFIELD` miss and a reflective `getDeclaredField` miss. Empty if not applicable.
     */
    fun getMangledFieldNames(fieldName: String): List<String>

    /**
     * Backing field to read when the getter [getterName] returning [returnTypeDescriptor]
     * (e.g. `Lkotlinx/atomicfu/AtomicRef;`) is missing at runtime. `null` if not applicable.
     */
    fun getBackingFieldForGetter(getterName: String, returnTypeDescriptor: String): String?

    /**
     * Source name for a mangled runtime [fieldName] (`_state$volatile` becomes `_state`), or `null`.
     * Best-effort. Feeds the Variables view and the Evaluate prefill. A wrong guess is just a prefill
     * the user can edit, never a correctness gate.
     */
    fun getDisplayName(fieldName: String): String?

    /**
     * Whether [classInternalName] is a value-holder type the plugin erased at runtime
     * (e.g. `kotlinx/atomicfu/AtomicRef`). A `CHECKCAST` to it on the mapped value is a no-op:
     * the runtime value is already unwrapped. `is` / INSTANCEOF against it is unaffected.
     */
    fun isErasedHolderType(classInternalName: String): Boolean

    /**
     * Whether reading member [memberName] on erased holder type [ownerInternalName] returns the holder's
     * own unwrapped value unchanged. Once the evaluator substitutes an atomic's backing value for the
     * atomic itself, that value is already the result of the holder's `.value` read, so a following call
     * in the chain is the identity. `false` if not applicable.
     */
    fun isUnwrappedValueAccessor(ownerInternalName: String, memberName: String): Boolean

    companion object {
        val EP_NAME: ExtensionPointName<KotlinMangledMemberContributor> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.debugger.core.mangledMemberContributor")

        /** Candidates from all contributors, concatenated. All are tried on a miss. */
        fun getMangledFieldNames(fieldName: String): List<String> = buildList {
            EP_NAME.forEachExtensionSafe { addAll(it.getMangledFieldNames(fieldName)) }
        }

        /** First contributor that claims the getter wins, else `null`. */
        fun getBackingFieldForGetter(getterName: String, returnTypeDescriptor: String): String? =
            EP_NAME.computeSafeIfAny { it.getBackingFieldForGetter(getterName, returnTypeDescriptor) }

        /** First contributor that knows the mangled name wins, else `null`. */
        fun getDisplayName(fieldName: String): String? =
            EP_NAME.computeSafeIfAny { it.getDisplayName(fieldName) }

        /** True if any contributor calls the type an erased value holder. */
        fun isErasedHolderType(classInternalName: String): Boolean =
            EP_NAME.findFirstSafe { it.isErasedHolderType(classInternalName) } != null

        /** True if any contributor calls the member an identity read on an erased holder's unwrapped value. */
        fun isUnwrappedValueAccessor(ownerInternalName: String, memberName: String): Boolean =
            EP_NAME.findFirstSafe { it.isUnwrappedValueAccessor(ownerInternalName, memberName) } != null
    }
}
