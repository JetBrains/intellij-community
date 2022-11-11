// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.performance.tests.utils

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.measureTimeMillis

val Long.nsToMs get() = (this * 1e-6).toLong()

inline fun gradleMessage(block: () -> String) {
    print("#gradle ${block()}")
}

inline fun logMessage(message: () -> String) {
    println("-- ${message()}")
}

inline fun runAndMeasure(note: String, block: () -> Unit) {
    val openProjectMillis = measureTimeMillis {
        block()
    }
    logMessage { "$note took $openProjectMillis ms" }
}

fun logMessage(t: Throwable, message: () -> String) {
    val writer = StringWriter()
    PrintWriter(writer).use {
        t.printStackTrace(it)
    }
    println("-- ${message()}:\n$writer")
}

object TeamCity {
    inline fun message(block: () -> String) {
        println("##teamcity[${block()}]")
    }

    fun suite(name: String, block: () -> Unit) {
        message { "testSuiteStarted name='$name'" }
        try {
            block()
        } catch (e: Throwable) {
            testFailed(name, e)
        } finally {
            message { "testSuiteFinished name='$name'" }
        }
    }

    fun test(name: String, durationMs: Long? = null, errors: List<Throwable>, block: () -> Unit) {
        test(name, durationMs, errorDetails = if (errors.isNotEmpty()) toDetails(errors) else null, block = block)
    }

    fun test(name: String?, durationMs: Long? = null, includeStats: Boolean = true, errorDetails: String? = null, block: () -> Unit) {
        name?.let { testStarted(it) }
        try {
            block()
        } finally {
            name?.let {
                if (includeStats) statValue(it, durationMs ?: -1)
                if (errorDetails != null) {
                    testFailed(it, errorDetails)
                } else {
                    testFinished(it, durationMs)
                }
            }
        }
    }

    inline fun statValue(name: String, value: Any) {
        message { "buildStatisticValue key='$name' value='$value'" }
    }

    inline fun testStarted(testName: String) {
        message { "testStarted name='$testName' captureStandardOutput='true'" }
    }

    inline fun metadata(testName: String, name: String, value: Number) {
        message { "testMetadata testName='$testName' name='$name' type='number' value='$value'" }
    }

    inline fun artifact(testName: String, name: String, artifactPath: String) {
        message { "testMetadata testName='$testName' name='$name' type='artifact' value='$artifactPath'" }
    }

    inline fun testFinished(testName: String, durationMs: Long? = null) {
        message { "testFinished name='$testName'${durationMs?.let { " duration='$durationMs'" } ?: ""}" }
    }

    fun testFailed(testName: String, error: Throwable) = testFailed(testName, toDetails(listOf(error))!!)

    fun testFailed(testName: String, details: String) {
        message { "testFailed name='$testName' message='Exceptions reported' details='${escape(details)}'" }
    }

    // https://www.jetbrains.com/help/teamcity/service-messages.html#:~:text=The%20text%20is%20limited%20to,if%20the%20limit%20is%20exceeded.
    //  The text is limited to 4000 symbols, and will be truncated if the limit is exceeded.
    private fun escape(s: String): String {
        val s1 = s.replace("|", "||")
            .replace("[", "|[")
            .replace("]", "|]")
            .replace("\r", "|r")
            .replace("\n", "|n")
            .replace("'", "|'")

        var limit = 4000
        if (s1.length < limit) return s1
        while (limit > 0) {
            if (s1[limit - 1] != '|') return s1.take(limit)
            limit--
        }
        return ""
    }

    private fun toDetails(errors: List<Throwable>): String? {
        if (errors.isEmpty()) return null
        val detailsWriter = StringWriter()
        val errorDetailsPrintWriter = PrintWriter(detailsWriter)
        errors.forEach {
            it.printStackTrace(errorDetailsPrintWriter)
            errorDetailsPrintWriter.println()
        }
        errorDetailsPrintWriter.close()
        return detailsWriter.toString()
    }
}
