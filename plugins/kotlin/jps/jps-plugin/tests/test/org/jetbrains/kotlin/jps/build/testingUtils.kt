// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.compilerRunner.JpsKotlinCompilerRunner

inline fun withSystemProperty(property: String, newValue: String?, fn: ()->Unit) {
    val backup = System.getProperty(property)
    setOrClearSysProperty(property, newValue)

    try {
        fn()
    }
    finally {
        setOrClearSysProperty(property, backup)
    }
}


@Suppress("NOTHING_TO_INLINE")
inline fun setOrClearSysProperty(property: String, newValue: String?) {
    if (newValue != null) {
        System.setProperty(property, newValue)
    }
    else {
        System.clearProperty(property)
    }
}

fun withDaemon(fn: () -> Unit) {
    val daemonHome = FileUtil.createTempDirectory("daemon-home", "testJpsDaemonIC")

    withSystemProperty(CompilerSystemProperties.COMPILE_DAEMON_CUSTOM_RUN_FILES_PATH_FOR_TESTS.property, daemonHome.absolutePath) {
        withSystemProperty(CompilerSystemProperties.COMPILE_DAEMON_ENABLED_PROPERTY.property, "true") {
            try {
                fn()
            } finally {
                JpsKotlinCompilerRunner.shutdownDaemon()

                // Try to force directory deletion to prevent test failure later in tearDown().
                // Working Daemon can prevent folder deletion on Windows, because Daemon shutdown
                // is asynchronous.
                var attempts = 0
                daemonHome.deleteRecursively()
                while (daemonHome.exists() && attempts < 100) {
                    daemonHome.deleteRecursively()
                    attempts++
                    Thread.sleep(50)
                }

                if (daemonHome.exists()) {
                    error("Couldn't delete Daemon home directory")
                }
            }
        }
    }
}