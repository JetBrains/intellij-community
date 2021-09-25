// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental

import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollectorImpl
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.ProtoData
import org.jetbrains.kotlin.incremental.getProtoData
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumer
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumerImpl
import org.jetbrains.kotlin.incremental.utils.TestMessageCollector
import org.jetbrains.kotlin.name.ClassId
import org.junit.Assert
import java.io.File

abstract class AbstractJsProtoComparisonTest : AbstractProtoComparisonTest<ProtoData>() {
    override fun expectedOutputFile(testDir: File): File =
        File(testDir, "result-js.out")
                .takeIf { it.exists() }
                ?: super.expectedOutputFile(testDir)

    override fun compileAndGetClasses(sourceDir: File, outputDir: File): Map<ClassId, ProtoData> {
        val incrementalResults = IncrementalResultsConsumerImpl()
        val services = Services.Builder().run {
            register(IncrementalResultsConsumer::class.java, incrementalResults)
            build()
        }

        val ktFiles = sourceDir.walkMatching { it.name.endsWith(".kt") }.map { it.canonicalPath }.toList()
        val messageCollector = TestMessageCollector()
        val outputItemsCollector = OutputItemsCollectorImpl()
        val args = K2JSCompilerArguments().apply {
            outputFile = File(outputDir, "out.js").canonicalPath
            metaInfo = true
            main = K2JsArgumentConstants.NO_CALL
            freeArgs = ktFiles
        }

        val env = createTestingCompilerEnvironment(messageCollector, outputItemsCollector, services)
        runJSCompiler(args, env).let { exitCode ->
            val expectedOutput = "OK"
            val actualOutput = (listOf(exitCode?.name) + messageCollector.errors).joinToString("\n")
            Assert.assertEquals(expectedOutput, actualOutput)
        }

        val classes = hashMapOf<ClassId, ProtoData>()

        for ((sourceFile, translationResult) in incrementalResults.packageParts) {
            classes.putAll(getProtoData(sourceFile, translationResult.metadata))
        }

        return classes
    }

    override fun ProtoData.toProtoData(): ProtoData? = this
}
