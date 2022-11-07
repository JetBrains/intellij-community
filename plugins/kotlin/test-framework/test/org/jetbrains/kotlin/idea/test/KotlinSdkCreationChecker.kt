// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.idea.framework.KotlinSdkType

class KotlinSdkCreationChecker {
    private val projectJdkTable: ProjectJdkTable
        get() = runReadAction { ProjectJdkTable.getInstance() }

    private val sdksBefore: Array<out Sdk> = projectJdkTable.allJdks

    fun getKotlinSdks() = projectJdkTable.allJdks.filter { it.sdkType is KotlinSdkType }

    private fun getCreatedKotlinSdks() =
        projectJdkTable.allJdks.filter { !sdksBefore.contains(it) && it.sdkType is KotlinSdkType }

    fun isKotlinSdkCreated() = getCreatedKotlinSdks().isNotEmpty()

    fun removeNewKotlinSdk() {
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction {
                getCreatedKotlinSdks().forEach { projectJdkTable.removeJdk(it) }
            }
        }
    }
}