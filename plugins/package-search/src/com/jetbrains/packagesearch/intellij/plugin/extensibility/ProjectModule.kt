package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import kotlinx.serialization.Serializable

/**
 * Class representing a native [Module] enriched with Package Search data.
 *
 * @param name the name of the module.
 * @param nativeModule the native [Module] it refers to.
 * @param parent the parent [Module] of this object.
 * @param buildFile The build file used by this module (e.g. `pom.xml` for Maven, `build.gradle` for Gradle).
 * @param moduleType The additional Package Searcxh related data such as project icons, additional localizations and so on.
 * listed in the Dependency Analyzer tool. At the moment the DA only supports Gradle and Maven.
 * @param availableScopes Scopes available for the build system of this module (e.g. `implementation`, `api` for Gradle;
 * `test`, `compile` for Maven).
 * @param dependencyDeclarationCallback Given a [Dependency], it should return the indexes in the build file where given
 * dependency has been declared.
 */
data class ProjectModule @JvmOverloads constructor(
    @NlsSafe val name: String,
    val nativeModule: Module,
    val parent: ProjectModule?,
    val buildFile: VirtualFile,
    val buildSystemType: BuildSystemType,
    val moduleType: ProjectModuleType,
    val availableScopes: List<String> = emptyList(),
    val dependencyDeclarationCallback: DependencyDeclarationCallback = { _ -> null }
) {

    @Suppress("UNUSED_PARAMETER")
    @Deprecated(
        "Use main constructor",
        ReplaceWith("ProjectModule(name, nativeModule, parent, buildFile, buildSystemType, moduleType)")
    )
    constructor(
        name: String,
        nativeModule: Module,
        parent: ProjectModule,
        buildFile: VirtualFile,
        buildSystemType: BuildSystemType,
        moduleType: ProjectModuleType,
        navigatableDependency: (groupId: String, artifactId: String, version: PackageVersion) -> Navigatable?
    ) : this(name, nativeModule, parent, buildFile, buildSystemType, moduleType)

    @Suppress("UNUSED_PARAMETER")
    @Deprecated(
        "Use main constructor",
        ReplaceWith("ProjectModule(name, nativeModule, parent, buildFile, buildSystemType, moduleType)")
    )
    constructor(
        name: String,
        nativeModule: Module,
        parent: ProjectModule,
        buildFile: VirtualFile,
        buildSystemType: BuildSystemType,
        moduleType: ProjectModuleType,
        navigatableDependency: (groupId: String, artifactId: String, version: PackageVersion) -> Navigatable?,
        availableScopes: List<String>
    ) : this(name, nativeModule, parent, buildFile, buildSystemType, moduleType, availableScopes)

    fun getBuildFileNavigatableAtOffset(offset: Int): Navigatable? =
        PsiManager.getInstance(nativeModule.project).findFile(buildFile)?.let { psiFile ->
            PsiUtil.getElementAtOffset(psiFile, offset).takeIf { it != buildFile } as? Navigatable
        }

    @NlsSafe
    fun getFullName(): String =
        parent?.let { it.getFullName() + ":$name" } ?: name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectModule) return false

        if (name != other.name) return false
        if (!nativeModule.isTheSameAs(other.nativeModule)) return false // This can't be automated
        if (parent != other.parent) return false
        if (buildFile.path != other.buildFile.path) return false
        if (buildSystemType != other.buildSystemType) return false
        if (moduleType != other.moduleType) return false
        // if (navigatableDependency != other.navigatableDependency) return false // Intentionally excluded
        if (availableScopes != other.availableScopes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + nativeModule.hashCodeOrZero()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + buildFile.path.hashCode()
        result = 31 * result + buildSystemType.hashCode()
        result = 31 * result + moduleType.hashCode()
        // result = 31 * result + navigatableDependency.hashCode() // Intentionally excluded
        result = 31 * result + availableScopes.hashCode()
        return result
    }
}

internal fun Module.isTheSameAs(other: Module) =
    runCatching { moduleFilePath == other.moduleFilePath && name == other.name }
        .getOrDefault(false)

private fun Module.hashCodeOrZero() =
    runCatching { moduleFilePath.hashCode() + 31 * name.hashCode() }
        .getOrDefault(0)

typealias DependencyDeclarationCallback = suspend (Dependency) -> DependencyDeclarationIndexes?

/**
 * Container class for declaration coordinates for a dependency in a build file. \
 * Example for Gradle:
 * ```
 *    implementation("io.ktor:ktor-server-cio:2.0.0")
 * // ▲               ▲                       ▲
 * // |               ∟ coordinatesStartIndex |
 * // ∟ wholeDeclarationStartIndex            ∟ versionStartIndex
 * //
 * ```
 * Example for Maven:
 * ```
 *      <dependency>
 * //    ▲ wholeDeclarationStartIndex
 *          <groupId>io.ktor</groupId>
 * //                ▲ coordinatesStartIndex
 *          <artifactId>ktor-server-cio</artifactId>
 *          <version>2.0.0</version>
 * //                ▲ versionStartIndex
 *      </dependency>
 * ```
 * @param wholeDeclarationStartIndex index of the first character where the whole declarations starts.
 *
 */
@Serializable
data class DependencyDeclarationIndexes(
    val wholeDeclarationStartIndex: Int,
    val coordinatesStartIndex: Int,
    val versionStartIndex: Int?
)

data class UnifiedDependencyKey(val scope: String, val groupId: String, val module: String)

val UnifiedDependency.key: UnifiedDependencyKey?
    get() {
        return UnifiedDependencyKey(scope ?: return null, coordinates.groupId!!, coordinates.artifactId ?: return null)
    }

fun UnifiedDependency.asDependency(): Dependency? {
    return Dependency(
        scope = scope ?: return null,
        groupId = coordinates.groupId ?: return null,
        artifactId = coordinates.artifactId ?: return null,
        version = coordinates.version ?: return null
    )
}

data class Dependency(val scope: String, val groupId: String, val artifactId: String, val version: String) {

    override fun toString() = "$scope(\"$groupId:$artifactId:$version\")"
}
