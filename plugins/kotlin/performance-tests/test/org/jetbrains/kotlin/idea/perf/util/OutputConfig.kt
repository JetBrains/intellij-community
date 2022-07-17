// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.util

import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.idea.perf.stats.compareBenchmarkWithSample

data class OutputConfig(
    var writeToJsonFile: Boolean = true,
    var uploadToElasticSearch: Boolean = true,
    var validatePropertyNames: Boolean = false,
) {
    fun write(benchmark: Benchmark) {
        if (validatePropertyNames) {
            compareBenchmarkWithSample(benchmark)?.let { errors ->
                throw AssertionFailedError(errors.joinToString("\n"))
            }
        }
        if (writeToJsonFile) {
            benchmark.writeJson()
        }
        if (uploadToElasticSearch) {
            ESUploader.upload(benchmark)
        }
    }
}