// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.tools.projectWizard.GeneratedIdentificator
import org.jetbrains.kotlin.tools.projectWizard.Identificator
import org.jetbrains.kotlin.tools.projectWizard.IdentificatorOwner
import org.jetbrains.kotlin.tools.projectWizard.core.*

import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildFileIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import java.nio.file.Paths

@JvmInline
value class ModulePath(val parts: List<String>) {
    constructor(path: String) : this(path.trim().split('.'))

    fun asString(separator: String = ".") = parts.joinToString(separator)
    fun asPath() = Paths.get(parts.first(), *parts.subList(1, parts.size).toTypedArray())

    override fun toString(): String = asString()

    companion object {
        val parser = valueParser { value, path ->
            val (stringPath) = value.parseAs<String>(path)
            ModulePath(stringPath)
        }
    }
}

fun ModulePath.considerSingleRootModuleMode(isSingleRootMode: Boolean) = when {
    isSingleRootMode && parts.size > 1 -> ModulePath(parts.subList(1, parts.size))
    else -> this
}


sealed class SourcesetDependency
data class ModuleBasedSourcesetDependency(val module: Module) : SourcesetDependency()
data class PathBasedSourcesetDependency(val path: ModulePath) : SourcesetDependency() {
    companion object {
        val parser = valueParser { value, path ->
            val (stringPath) = value.parseAs<String>(path)
            PathBasedSourcesetDependency(ModulePath(stringPath.split('.')))
        }
    }
}


// A `main` or `test` sourceset for single or multiplatform projects
class Sourceset(
    val sourcesetType: SourcesetType,
    var dependencies: List<SourcesetDependency> = emptyList(),
    var parent: Module? = null,
    override val identificator: Identificator = GeneratedIdentificator(sourcesetType.name),
    val createDirectory: Boolean = true,
    /**
     * Here you can configure dependencies between Kotlin sourceSets in Gradle module, for example:
     * iosSimulatorArm64Main.dependsOn(iosMain)
     */
    val dependsOnModules: List<Module> = emptyList(),
) : DisplayableSettingItem, IdentificatorOwner {
    override val text: String
        @NlsSafe
        get() = sourcesetType.name
    override val greyText: String?
        get() = null

    companion object {
        fun parser() = mapParser { map, path ->
            val (sourcesetType) = map.parseValue<SourcesetType>(this, path, "type", enumParser())
            val identificator = GeneratedIdentificator(sourcesetType.name)
            val (dependencies) = map.parseValue(
                this,
                path,
                "dependencies",
                listParser(PathBasedSourcesetDependency.parser)
            ) { emptyList() }

            Sourceset(sourcesetType, dependencies, identificator = identificator)
        }
    }
}

@Suppress("EnumEntryName")
enum class SourcesetType : DisplayableSettingItem {
    main, test;

    override val text: String
        @NlsSafe
        get() = name

    companion object {
        val ALL = values().toSet()
    }
}


fun Writer.updateBuildFiles(action: (BuildFileIR) -> TaskResult<BuildFileIR>): TaskResult<Unit> =
    BuildSystemPlugin.buildFiles.update { buildFiles ->
        buildFiles.mapSequence(action)
    }

fun Writer.updateModules(action: (ModuleIR) -> TaskResult<ModuleIR>): TaskResult<Unit> =
    updateBuildFiles { buildFile ->
        buildFile.withModulesUpdated { action(it) }
    }

fun Writer.forEachModule(action: (ModuleIR) -> TaskResult<Unit>): TaskResult<Unit> =
    updateModules { moduleIR -> action(moduleIR).map { moduleIR } }
