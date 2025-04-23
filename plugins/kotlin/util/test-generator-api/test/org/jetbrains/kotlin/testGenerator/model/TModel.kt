// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TargetBackend
import java.io.File

typealias ModelMatcher = (String) -> ModelMatchingResult?

infix fun ModelMatcher.or(other: ModelMatcher): ModelMatcher =
    { model -> this(model) ?: other(model )}

class ModelMatchingResult(val methodName: String)

data class TModel(
    val path: String,
    val matcher: ModelMatcher,
    val testClassName: String,
    val testMethodName: String,
    val flatten: Boolean,
    val targetBackend: TargetBackend,
    val excludedDirectories: List<String>,
    val depth: Int,
    val passTestDataPath: Boolean,
    val classPerTest: Boolean,
    val bucketSize: Int?,
    val ignored: Boolean,
    val runWithClass: Class<*>,
    val methodAnnotations: List<TAnnotation>,
    val setUpStatements: List<String>,
)

fun ModelMatcher.withPrecondition(precondition: (String) -> Boolean): ModelMatcher {
    return { name -> if (precondition(name)) this(name) else null }
}

object Patterns {
    fun forRegex(regex: String): ModelMatcher {
        return f@ { name ->
            val result = regex.toRegex().matchEntire(name) ?: return@f null
            return@f ModelMatchingResult(result.groupValues[1])
        }
    }

    private fun forExtension(extension: String): ModelMatcher {
        val escapedExtension = Regex.escapeReplacement(extension)
        return forRegex("^(.+)\\.$escapedExtension\$")
    }

    val DIRECTORY: ModelMatcher = forRegex("^([^\\.]+)$")

    val TEST: ModelMatcher = forExtension("test")
    val KT: ModelMatcher = forExtension("kt")
    val GROOVY: ModelMatcher = forExtension("groovy")
    val TXT: ModelMatcher = forExtension("txt")
    val KTS: ModelMatcher = forExtension("kts")
    val MAIN_KTS: ModelMatcher = forExtension("main.kts")
    val JAVA: ModelMatcher = forExtension("java")
    val WS_KTS: ModelMatcher = forExtension("ws.kts")
    val MD: ModelMatcher = forExtension("md")

    val KT_OR_JAVA: ModelMatcher = forRegex("^(.+)\\.(kt|java)$")
    val KT_OR_KTS: ModelMatcher = forRegex("^(.+)\\.(kt|kts)$")
    val KT_WITHOUT_DOTS: ModelMatcher = forRegex("^([^.]+)\\.kt$")
    val KT_OR_KTS_WITHOUT_DOTS: ModelMatcher = forRegex("^([^.]+)\\.(kt|kts)$")
    val KT_WITHOUT_FIR_PREFIX: ModelMatcher = forRegex("""^(.+)(?<!\.fir)\.kt$""")
    val KT_WITHOUT_DOT_AND_FIR_PREFIX: ModelMatcher = forRegex("""^([^.]+)(?<!\.fir)\.kt$""")
}

fun MutableTSuite.model(
    path: String,
    pattern: ModelMatcher = Patterns.KT,
    isRecursive: Boolean = true,
    isIgnored: Boolean = false,
    testClassName: String = File(path).toJavaIdentifier().capitalize(),
    testMethodName: String = "doTest",
    flatten: Boolean = false,
    targetBackend: TargetBackend = TargetBackend.ANY,
    excludedDirectories: List<String> = emptyList(),
    depth: Int = Int.MAX_VALUE,
    passTestDataPath: Boolean = true,
    classPerTest: Boolean = false,
    splitToBuckets: Boolean = false,
    bucketSize: Int = 20,
    runWithClass: Class<*> = JUnit3RunnerWithInners::class.java,
    methodAnnotations: List<TAnnotation> = emptyList(),
    setUpStatements: List<String> = emptyList()
) {
    methodAnnotations.forEach { imports += it.className }

    models += TModel(
        path = path,
        matcher = pattern,
        testClassName = testClassName,
        testMethodName = testMethodName,
        flatten = flatten,
        targetBackend = targetBackend,
        excludedDirectories = excludedDirectories,
        depth = if (!isRecursive) 0 else depth,
        passTestDataPath = passTestDataPath,
        classPerTest = classPerTest,
        bucketSize = if (!splitToBuckets) null else bucketSize,
        ignored = isIgnored,
        runWithClass = runWithClass,
        methodAnnotations = methodAnnotations,
        setUpStatements = setUpStatements,
    )
}

fun makeJavaIdentifier(text: String): String {
    return buildString {
        for (c in text) {
            append(if (Character.isJavaIdentifierPart(c)) c else "_")
        }
    }
}

fun File.toJavaIdentifier() = makeJavaIdentifier(name)