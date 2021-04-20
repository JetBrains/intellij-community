/*
 * Copyright 2010-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile

abstract class J2kConverterExtension {
    abstract val isNewJ2k: Boolean

    abstract fun createJavaToKotlinConverter(
        project: Project,
        targetModule: Module?,
        settings: ConverterSettings,
        services: JavaToKotlinConverterServices
    ): JavaToKotlinConverter

    abstract fun createPostProcessor(formatCode: Boolean): PostProcessor

    open fun doCheckBeforeConversion(project: Project, module: Module): Boolean =
        true

    abstract fun createWithProgressProcessor(
        progress: ProgressIndicator?,
        files: List<PsiJavaFile>?,
        phasesCount: Int
    ): WithProgressProcessor

    companion object {
        val EP_NAME = ExtensionPointName<J2kConverterExtension>("org.jetbrains.kotlin.j2kConverterExtension")

        fun extension(useNewJ2k: Boolean): J2kConverterExtension = EP_NAME.extensionList.first { it.isNewJ2k == useNewJ2k }
    }
}
