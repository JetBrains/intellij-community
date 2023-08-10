// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.performance.tests.utils.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.TestApplicationManager
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.performance.tests.utils.logMessage
import org.jetbrains.kotlin.idea.test.GradleProcessOutputInterceptor

fun initApp(rootDisposable: Disposable): TestApplicationManager {
    val application = TestApplicationManager.getInstance()
    GradleProcessOutputInterceptor.install(rootDisposable)
    return application
}

fun initSdk(rootDisposable: Disposable): Sdk {
    return runWriteAction {
        val roots = mutableSetOf<String>()
        val jdk8Home = System.getenv("JDK_18") ?: System.getenv("JAVA8_HOME")
        jdk8Home?.let { roots += it }

        val jdk11Home = System.getenv("JDK_11") ?: System.getenv("JAVA11_HOME")
        jdk11Home?.let { roots += it }

        val javaHome = System.getenv("JAVA_HOME") ?: error("env JAVA_HOME is not set")
        roots += javaHome
        VfsRootAccess.allowRootAccess(rootDisposable, *roots.toTypedArray())

        val javaSdk = JavaSdk.getInstance()
        val jdk8 = javaSdk.createJdk("1.8", jdk8Home ?: javaHome)
        val jdk11 = javaSdk.createJdk("11", jdk11Home ?: javaHome )
        val internal = javaSdk.createJdk("IDEA jdk", jdk11Home ?: jdk8Home ?: javaHome)
        val gradle = javaSdk.createJdk(GRADLE_JDK_NAME, jdk11Home ?: jdk8Home ?: javaHome)

        runReadAction {
            val jdkTable = ProjectJdkTable.getInstance()
            arrayOf(jdk8, jdk11, internal, gradle).forEach { jdkTable.addJdk(it, rootDisposable) }
            KotlinSdkType.setUpIfNeeded()

            logMessage { jdkTable.allJdks.joinToString("\n") }
        }

        jdk11
    }
}