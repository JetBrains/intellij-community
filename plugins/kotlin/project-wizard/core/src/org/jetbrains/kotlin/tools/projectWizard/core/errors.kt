// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import java.io.IOException
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module

abstract class Error {
    @get:Nls
    abstract val message: String
}

abstract class ExceptionError : Error() {
    abstract val exception: Exception
    override val message: String
        @NlsSafe
        get() = exception::class.simpleName!!.removeSuffix("Exception").splitByWords() +
                exception.message?.let { ": $it" }.orEmpty()

    companion object {
        private val wordRegex = "[A-Z][a-z0-9]+".toRegex()
        private fun String.splitByWords() =
            wordRegex.findAll(this).joinToString(separator = " ") { it.value }
    }
}

data class IOError(override val exception: IOException) : ExceptionError()

data class ExceptionErrorImpl(override val exception: Exception) : ExceptionError()

data class ParseError(@Nls override val message: String) : Error()

data class TemplateNotFoundError(val id: String) : Error() {
    override val message: String
        get() = KotlinNewProjectWizardBundle.message("error.template.not.found", id)
}

data class RequiredSettingsIsNotPresentError(val settingNames: List<String>) : Error() {
    override val message: String
        @Nls
        get() = KotlinNewProjectWizardBundle.message(
            "error.required.settings.are.not.present.0",
            settingNames.joinToString(separator = "\n") { "   $it" }
        )
}

data class CircularTaskDependencyError(@NonNls val taskName: String) : Error() {
    @get:NonNls
    override val message: String
        get() = "$taskName task has circular dependencies"
}

data class BadSettingValueError(@Nls override val message: String) : Error()

data class ConfiguratorNotFoundError(val id: String) : Error() {
    override val message: String
        get() = KotlinNewProjectWizardBundle.message("error.configurator.not.found", id)
}

data class ValidationError(@Nls val validationMessage: String) : Error() {
    override val message: String
        get() = validationMessage.capitalize()
}

data class ProjectImportingError(val kotlinVersion: String, @Nls val reason: String, val details: String) : Error() {
    override val message: String
        get() = KotlinNewProjectWizardBundle.message("error.text.project.importing.error.kotlin.version.0.reason.1", kotlinVersion, reason)
}

data class InvalidModuleDependencyError(val from: String, val to: String, @Nls val reason: String? = null) : Error() {
    constructor(from: Module, to: Module, @Nls reason: String? = null) : this(from.name, to.name, reason)

    override val message: String
        get() = KotlinNewProjectWizardBundle.message("error.invalid.module.dependency", from, to, reason?.let { ": $it" }.orEmpty())
}