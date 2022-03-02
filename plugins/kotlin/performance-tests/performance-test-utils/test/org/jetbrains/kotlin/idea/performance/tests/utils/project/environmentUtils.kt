// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.performance.tests.utils.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.TestApplicationManager
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.GradleProcessOutputInterceptor
import org.jetbrains.kotlin.idea.util.getProjectJdkTableSafe

fun initApp(rootDisposable: Disposable): TestApplicationManager {
    val application = TestApplicationManager.getInstance()
    GradleProcessOutputInterceptor.install(rootDisposable)
    return application
}

fun initSdk(rootDisposable: Disposable): Sdk {
    return runWriteAction {
        val jdkTableImpl = JavaAwareProjectJdkTableImpl.getInstanceEx()
        val homePath = if (jdkTableImpl.internalJdk.homeDirectory!!.name == "jre") {
            jdkTableImpl.internalJdk.homeDirectory!!.parent.path
        } else {
            jdkTableImpl.internalJdk.homePath!!
        }

        val roots = mutableListOf<String>()
        roots += homePath
        System.getenv("JDK_18")?.let {
            roots += it
        }
        VfsRootAccess.allowRootAccess(rootDisposable, *roots.toTypedArray())

        val javaSdk = JavaSdk.getInstance()
        val jdk = javaSdk.createJdk("1.8", homePath)
        val internal = javaSdk.createJdk("IDEA jdk", homePath)
        val gradle = javaSdk.createJdk(GRADLE_JDK_NAME, homePath)

        val jdkTable = getProjectJdkTableSafe()
        jdkTable.addJdk(jdk, rootDisposable)
        jdkTable.addJdk(internal, rootDisposable)
        jdkTable.addJdk(gradle, rootDisposable)
        KotlinSdkType.setUpIfNeeded()
        jdk
    }
}