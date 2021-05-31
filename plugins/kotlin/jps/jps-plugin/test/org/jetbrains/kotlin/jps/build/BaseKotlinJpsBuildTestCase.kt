// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.kotlin.compilerRunner.JpsKotlinCompilerRunner
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.AndroidStudioTestUtils

abstract class BaseKotlinJpsBuildTestCase : JpsBuildTestCase() {
    override fun setUp() {
        super.setUp()
        System.setProperty("kotlin.jps.tests", "true")
    }

    override fun shouldRunTest(): Boolean {
        return super.shouldRunTest() && !AndroidStudioTestUtils.skipIncompatibleTestAgainstAndroidStudio()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable {
                System.clearProperty("kotlin.jps.tests")
                myModel = null
                myBuildParams.clear()
            },
            ThrowableRunnable { JpsKotlinCompilerRunner.releaseCompileServiceSession() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun addJdk(name: String, path: String?): JpsSdk<JpsDummyElement> {
        val homePath = System.getProperty("java.home")
        val versionString = System.getProperty("java.version")
        val jdk = myModel.global.addSdk(name, homePath, versionString, JpsJavaSdkType.INSTANCE)
        jdk.addRoot(StandardFileSystems.JRT_PROTOCOL_PREFIX + homePath + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
        return jdk.properties
    }

    private val libraries = mutableMapOf<String, JpsLibrary>()

    protected fun requireLibrary(library: KotlinJpsLibrary) = libraries.getOrPut(library.id) {
        library.create(myProject)
    }
}
