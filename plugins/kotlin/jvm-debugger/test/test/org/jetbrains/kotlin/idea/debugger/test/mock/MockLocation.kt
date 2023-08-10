// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test.mock

import com.sun.jdi.*

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
        "Java" -> outputLineNumber
        "Kotlin" -> lineNumber
        "KotlinDebug" -> kotlinDebugLineNumber
        else -> error("Unknown stratum: $stratum")
    }

    override fun sourceName(stratum: String): String = when (stratum) {
        "Java" -> throw AbsentInformationException()
        "Kotlin" -> sourceName
        "KotlinDebug" -> kotlinDebugSourceName ?: throw AbsentInformationException()
        else -> error("Unknown stratum: $stratum")
    }

    override fun virtualMachine(): VirtualMachine = method.virtualMachine()

    override fun sourcePath(): String = sourcePath ?: throw AbsentInformationException()

    override fun sourcePath(stratum: String): String = when (stratum) {
        // JDI would actually compute a path based on the sourceName and the package of the enclosing reference type,
        // but that's not useful for Kotlin anyway.
        "Java" -> throw AbsentInformationException()
        "Kotlin" -> sourcePath ?: throw AbsentInformationException()
        "KotlinDebug" -> kotlinDebugSourcePath ?: throw AbsentInformationException()
        else -> error("Unknown stratum: $stratum")
    }
}
