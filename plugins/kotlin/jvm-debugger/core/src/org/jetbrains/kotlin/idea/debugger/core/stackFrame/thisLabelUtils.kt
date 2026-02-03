// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import org.jetbrains.kotlin.codegen.AsmUtil

fun getThisName(label: String): String {
    return AsmUtil.THIS + " (@" + label + ")"
}

fun getThisValueLabel(thisValue: ObjectReference): String? {
    val thisType = thisValue.referenceType()
    val unsafeLabel = generateThisLabelUnsafe(thisType) ?: return null
    return checkLabel(unsafeLabel)
}

fun generateThisLabelUnsafe(type: Type?): String? {
    val referenceType = type as? ReferenceType ?: return null
    return referenceType.name().substringAfterLast('.').substringAfterLast('$')
}

fun generateThisLabel(type: Type?): String? {
    return checkLabel(generateThisLabelUnsafe(type) ?: return null)
}

private fun checkLabel(label: String): String? {
    if (label.isEmpty() || label.all { it.isDigit() }) {
        return null
    }

    return label
}