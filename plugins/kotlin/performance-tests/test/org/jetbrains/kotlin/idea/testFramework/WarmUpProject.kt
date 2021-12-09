// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testFramework

import org.jetbrains.kotlin.idea.perf.live.AbstractPerformanceProjectsTest

/**
 * warm up: open simple `hello world` project
 */
class WarmUpProject(private val stats: Stats) {
    private var warmedUp: Boolean = false

    fun warmUp(test: AbstractPerformanceProjectsTest) {
        if (warmedUp) return
        test.warmUpProject(stats, "src/HelloMain.kt") {
            openProject {
                name("helloWorld")

                module {
                    kotlinStandardLibrary()

                    kotlinFile("HelloMain") {
                        topFunction("main") {
                            param("args", "Array<String>")
                            body("""println("Hello World!")""")
                        }
                    }
                }
            }
        }
        warmedUp = true
    }
}