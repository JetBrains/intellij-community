// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

/**
 * kotlinx.atomicfu rewrites an atomic property at compile time.
 * `val _state = atomic<Any?>(...)` becomes a `volatile` field `_state$volatile` (erased value type) plus a
 * static updater `_state$volatile$FU`. The getter is dropped.
 *
 * Debug expressions compile against the un-transformed metadata, so `_state` resolves to the now missing
 * `_state` field or `get_state()` getter. Both map here to `_state$volatile`. See IDEA-335346.
 */
internal class AtomicfuMangledMemberContributor : KotlinMangledMemberContributor {
    override fun getMangledFieldNames(fieldName: String): List<String> =
        if (fieldName.endsWith(ATOMICFU_VOLATILE_FIELD_SUFFIX)) emptyList()
        else listOf(fieldName + ATOMICFU_VOLATILE_FIELD_SUFFIX)

    override fun getBackingFieldForGetter(getterName: String, returnTypeDescriptor: String): String? {
        if (!returnTypeDescriptor.startsWith(ATOMICFU_TYPE_DESCRIPTOR_PREFIX)) return null
        val propertyName = getterName.removePrefix(GETTER_PREFIX).replaceFirstChar { it.lowercaseChar() }
        if (propertyName == getterName || propertyName.isEmpty()) return null
        if (propertyName.endsWith(ATOMICFU_VOLATILE_FIELD_SUFFIX)) return null
        return propertyName + ATOMICFU_VOLATILE_FIELD_SUFFIX
    }

    override fun getDisplayName(fieldName: String): String? {
        if (!fieldName.endsWith(ATOMICFU_VOLATILE_FIELD_SUFFIX)) return null
        return fieldName.removeSuffix(ATOMICFU_VOLATILE_FIELD_SUFFIX)
    }

    // atomicfu erases the `Atomic*` wrapper, so the compiler-generated CHECKCAST to it on a property access
    // must pass against the already-unwrapped value. Only the cast is relaxed (see Eval.canCastTo); an
    // `is` / INSTANCEOF test against the holder type still goes through the honest check.
    override fun isErasedHolderType(classInternalName: String): Boolean =
        classInternalName.startsWith(ATOMICFU_TYPE_INTERNAL_NAME_PREFIX)

    // `atomic.value` reads the holder's current value. The evaluator already unwrapped the atomic to that
    // value, so the getter is the identity on the receiver. Mutators (`setValue`, CAS) need the owning
    // object we no longer have, so they stay unsupported.
    override fun isUnwrappedValueAccessor(ownerInternalName: String, memberName: String): Boolean =
        ownerInternalName.startsWith(ATOMICFU_TYPE_INTERNAL_NAME_PREFIX) && memberName == VALUE_GETTER_NAME
}

private const val GETTER_PREFIX = "get"
private const val VALUE_GETTER_NAME = "getValue"
private const val ATOMICFU_VOLATILE_FIELD_SUFFIX = "\$volatile"

// atomicfu value holders live in the kotlinx.atomicfu package (AtomicRef, AtomicInt, AtomicLong, AtomicBoolean).
private const val ATOMICFU_TYPE_INTERNAL_NAME_PREFIX = "kotlinx/atomicfu/Atomic"
private const val ATOMICFU_TYPE_DESCRIPTOR_PREFIX = "L$ATOMICFU_TYPE_INTERNAL_NAME_PREFIX"
