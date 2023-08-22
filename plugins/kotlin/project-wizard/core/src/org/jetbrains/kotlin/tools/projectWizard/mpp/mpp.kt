// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.mpp

import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.SimpleTargetConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.inContextOfModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectPath
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.JavaPackage
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType
import org.jetbrains.kotlin.tools.projectWizard.templates.FileDescriptor
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplate
import java.nio.file.Path
import java.util.*

@DslMarker
annotation class ExpectFileDSL

@ExpectFileDSL
class MppSources(private val simpleFiles: List<SimpleFiles>) {
    fun getFilesFor(moduleSubType: ModuleSubType): List<SimpleFile> =
        simpleFiles.filter { moduleSubType in it.moduleSubTypes }.flatMap { it.files }

    class Builder(private val javaPackage: JavaPackage?) {
        private val simpleFiles = mutableListOf<SimpleFiles>()

        fun filesFor(vararg moduleSubTypes: ModuleSubType, init: SimpleFiles.Builder.() -> Unit) {
            simpleFiles += SimpleFiles.Builder(moduleSubTypes.toList(), javaPackage).apply(init).build()
        }

        fun build() = MppSources(simpleFiles)
    }
}


data class SimpleFiles(val moduleSubTypes: List<ModuleSubType>, val files: List<SimpleFile>) {
    class Builder(private val moduleSubTypes: List<ModuleSubType>, private val filesPackage: JavaPackage?) {
        private val files = mutableListOf<SimpleFile>()

        fun file(fileDescriptor: FileDescriptor, filename: String, type: SourcesetType, init: SimpleFile.Builder.() -> Unit = {}) {
            files += SimpleFile.Builder(fileDescriptor, filename, type).apply(init).apply {
                javaPackage = filesPackage
            }.build()
        }

        fun build() = SimpleFiles(moduleSubTypes, files)
    }
}

data class SimpleFile(val fileDescriptor: FileDescriptor, val javaPackage: JavaPackage?, val filename: String, val type: SourcesetType) {
    val uniqueIdentifier get() = "${type}/${javaPackage}/${filename}"

    class Builder(private val fileDescriptor: FileDescriptor, private val filename: String, private val type: SourcesetType) {
        var javaPackage: JavaPackage? = null

        fun build() = SimpleFile(fileDescriptor, javaPackage, filename, type)
    }
}


fun mppSources(javaPackage: JavaPackage? = null, init: MppSources.Builder.() -> Unit): MppSources =
    MppSources.Builder(javaPackage).apply(init).build()

fun Writer.applyMppStructure(
    mppSources: List<MppSources>,
    module: Module,
    modulePath: Path,
): TaskResult<Unit> = compute {
    createSimpleFiles(mppSources, module, modulePath).ensure()
}

fun Writer.applyMppStructure(
    mppSources: MppSources,
    module: Module,
    modulePath: Path,
): TaskResult<Unit> = applyMppStructure(listOf(mppSources), module, modulePath)

private fun Writer.createSimpleFiles(
    mppSources: List<MppSources>,
    module: Module,
    modulePath: Path
): TaskResult<Unit> = inContextOfModuleConfigurator(module) {
    val filesWithPaths = module.subModules.flatMap { target ->
        val moduleSubType = //TODO handle for non-simple target configurator
            target.configurator.safeAs<SimpleTargetConfigurator>()?.moduleSubType ?: return@flatMap emptyList()
        mppSources
            .flatMap { it.getFilesFor(moduleSubType) }
            .distinctBy { it.uniqueIdentifier }
            .map { file ->
                val path = projectPath / pathForFileInTarget(modulePath, module, file.javaPackage, file.filename, target, file.type)
                file to path
            }
    }.distinctBy { (_, path) -> path }
    filesWithPaths.mapSequenceIgnore { (file, path) ->
        val fileTemplate = FileTemplate(file.fileDescriptor, path, mapOf("package" to file.javaPackage?.asCodePackage()))
        TemplatesPlugin.fileTemplatesToRender.addValues(fileTemplate)
    }
}

private fun pathForFileInTarget(
    mppModulePath: Path,
    mppModule: Module,
    javaPackage: JavaPackage?,
    filename: String,
    target: Module,
    sourcesetType: SourcesetType,
) = mppModulePath /
        Defaults.SRC_DIR /
        "${target.name}${sourcesetType.name.capitalize(Locale.US)}" /
        mppModule.configurator.kotlinDirectoryName /
        javaPackage?.asPath() /
        filename
