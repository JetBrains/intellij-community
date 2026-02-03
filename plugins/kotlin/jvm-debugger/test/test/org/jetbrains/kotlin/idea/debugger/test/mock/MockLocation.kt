// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test.mock

import com.intellij.debugger.engine.DebugProcess.JAVA_STRATUM
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import org.jetbrains.kotlin.codegen.inline.KOTLIN_DEBUG_STRATA_NAME
import org.jetbrains.kotlin.codegen.inline.KOTLIN_STRATA_NAME

class MockLocation(
    private val codeIndex: Int,
    private val lineNumber: Int,
    private val sourceName: String,
    private val sourcePath: String?,
    private val outputLineNumber: Int,
    private val method: Method,
    private val declaringType: ReferenceType? = null,
) : Location {
    internal var kotlinDebugLineNumber: Int = -1
    internal var kotlinDebugSourceName: String? = null
    internal var kotlinDebugSourcePath: String? = null

    constructor(declaringType: ReferenceType, sourceName: String, lineNumber: Int) : this(
        codeIndex = -1,
        lineNumber = lineNumber,
        sourceName = sourceName,
        sourcePath = null,
        outputLineNumber = -1,
        declaringType = declaringType,
        method = MockMethod("", declaringType.virtualMachine())
    )

    fun withCodeIndex(codeIndex: Int) = MockLocation(
        codeIndex,
        lineNumber,
        sourceName,
        sourcePath,
        outputLineNumber,
        method,
        declaringType
    ).also {
        it.kotlinDebugLineNumber = kotlinDebugLineNumber
        it.kotlinDebugSourceName = kotlinDebugSourceName
        it.kotlinDebugSourcePath = kotlinDebugSourcePath
    }

    override fun toString(): String = "$sourceName:$lineNumber@$codeIndex"

    override fun codeIndex() = codeIndex.toLong()
    override fun lineNumber() = lineNumber
    override fun sourceName() = sourceName
    override fun declaringType() = declaringType!!
    override fun method() = method

    // LocationImpl also checks that the underlying methods are the same.
    override fun compareTo(other: Location): Int {
        val diff = codeIndex() - other.codeIndex()
        return if (diff < 0) -1 else if (diff > 0) 1 else 0
    }

    override fun lineNumber(stratum: String): Int = when (stratum) {
        JAVA_STRATUM -> outputLineNumber
        KOTLIN_STRATA_NAME -> lineNumber
        KOTLIN_DEBUG_STRATA_NAME -> kotlinDebugLineNumber
        else -> error("Unknown stratum: $stratum")
    }

    override fun sourceName(stratum: String): String = when (stratum) {
        JAVA_STRATUM -> throw AbsentInformationException()
        KOTLIN_STRATA_NAME -> sourceName
        KOTLIN_DEBUG_STRATA_NAME -> kotlinDebugSourceName ?: throw AbsentInformationException()
        else -> error("Unknown stratum: $stratum")
    }

    override fun virtualMachine(): VirtualMachine = method.virtualMachine()

    override fun sourcePath(): String = sourcePath ?: throw AbsentInformationException()

    override fun sourcePath(stratum: String): String = when (stratum) {
        // JDI would actually compute a path based on the sourceName and the package of the enclosing reference type,
        // but that's not useful for Kotlin anyway.
        JAVA_STRATUM -> throw AbsentInformationException()
        KOTLIN_STRATA_NAME -> sourcePath ?: throw AbsentInformationException()
        KOTLIN_DEBUG_STRATA_NAME -> kotlinDebugSourcePath ?: throw AbsentInformationException()
        else -> error("Unknown stratum: $stratum")
    }
}
