// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.util.getProjectJdkTableSafe

class KotlinSdkCreationChecker {

    private val sdksBefore: Array<out Sdk> = getProjectJdkTableSafe().allJdks

    fun getKotlinSdks() = getProjectJdkTableSafe().allJdks.filter { it.sdkType is KotlinSdkType }

    private fun getCreatedKotlinSdks() =
        getProjectJdkTableSafe().allJdks.filter { !sdksBefore.contains(it) && it.sdkType is KotlinSdkType }

    fun isKotlinSdkCreated() = getCreatedKotlinSdks().isNotEmpty()

    fun removeNewKotlinSdk() {
        val jdkTable = getProjectJdkTableSafe()
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction {
                getCreatedKotlinSdks().forEach { jdkTable.removeJdk(it) }
            }
        }
    }
}