// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import com.intellij.util.PathUtil
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.compilerRunner.JpsKotlinCompilerRunner
import org.jetbrains.kotlin.daemon.common.OSKind
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

class SimpleKotlinJpsBuildTest : AbstractKotlinJpsBuildTestCase() {
    override fun setUp() {
        super.setUp()
        workDir = KotlinTestUtils.tmpDirForTest(this)
    }

    fun testLoadingKotlinFromDifferentModules() {
        val aFile = createFile("m1/K.kt",
                               """
                                   package m1;

                                   interface K {
                                   }
                               """)
        createFile("m1/J.java",
                               """
                                   package m1;

                                   public interface J {
                                       K bar();
                                   }
                               """)
        val a = addModule("m1", PathUtil.getParentPath(aFile))

        val bFile = createFile("m2/m2.kt",
                               """
                                    import m1.J;
                                    import m1.K;

                                    interface M2: J {
                                        override fun bar(): K
                                    }
                               """)
        val b = addModule("b", PathUtil.getParentPath(bFile))
        JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(
                b.dependenciesList.addModuleDependency(a)
        ).isExported = false

        addKotlinStdlibDependency()
        rebuildAllModules()
    }

    // TODO: add JS tests
    fun testDaemon() {
        withDaemon {
            withSystemProperty(CompilerSystemProperties.COMPILE_DAEMON_VERBOSE_REPORT_PROPERTY.property, "true") {
                withSystemProperty(JpsKotlinCompilerRunner.FAIL_ON_FALLBACK_PROPERTY, "true") {
                    testLoadingKotlinFromDifferentModules()
                }
            }
        }
    }
}

// copied from CompilerDaemonTest.kt
// TODO: find shared place for this function
// java.util.Logger used in the daemon silently forgets to log into a file specified in the config on Windows,
// if file path is given in windows form (using backslash as a separator); the reason is unknown
// this function makes a path with forward slashed, that works on windows too
internal val File.loggerCompatiblePath: String
    get() =
    if (OSKind.current == OSKind.Windows) absolutePath.replace('\\', '/')
    else absolutePath
