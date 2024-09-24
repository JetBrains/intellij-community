// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.google.gson.*
import org.jetbrains.kotlin.idea.base.util.getAsStringList
import org.jetbrains.kotlin.idea.base.util.getNullableString
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import java.util.UUID

interface TestProjectStructure {
    /**
     * A list of *project-level* libraries in the test project structure.
     */
    val libraries: List<TestProjectLibrary>

    val modules: List<TestProjectModule>
}

object TestProjectStructureFields {
    /**
     * Used by [AbstractProjectStructureTest] to check if the test is disabled. Not present in the resulting [TestProjectStructure].
     */
    const val IS_DISABLED_FIELD = "is_disabled"

    const val LIBRARIES_FIELD = "libraries"
    const val MODULES_FIELD = "modules"
}

fun interface TestProjectStructureParser<S : TestProjectStructure> {
    /**
     * Parses a [TestProjectStructure] from already parsed lists of [TestProjectLibrary]s and [TestProjectModule]s, and the original
     * [JsonObject].
     */
    fun parse(libraries: List<TestProjectLibrary>, modules: List<TestProjectModule>, json: JsonObject): S
}

/**
 * A compiled project library called [name] with library roots labeled [roots].
 *
 * The library's [roots] are a list of labels which each refer to a unique JAR library. If there is a directory in the test case's test data
 * with the same name as the root label, the library JAR will be compiled from the sources in that directory. Otherwise, a temporary, empty
 * JAR file is generated instead.
 *
 * The JAR file with a root label `R` is unique across the whole test case. This allows adding the same root `R` to multiple libraries
 * without having to wrangle JAR files in the test data.
 *
 * [roots] is optional in `structure.json`. If absent, the library receives a single, unique root which won't be shared with other
 * libraries.
 */
data class TestProjectLibrary(val name: String, val roots: List<String>)

object TestProjectLibraryParser {
    private const val ROOTS_FIELD = "roots"
    private const val NAME_FIELD = "name"

    fun parse(json: JsonElement): TestProjectLibrary {
        require(json is JsonObject)
        val roots = json.getAsStringList(ROOTS_FIELD) ?: listOf(UUID.randomUUID().toString())
        return TestProjectLibrary(json.getString(NAME_FIELD), roots)
    }
}

/**
 * @param contentRoots A list of content roots of the module. Each content root's path is relative to the module's path in the test data. If
 *  no content roots are specified, the whole module content is regarded as a single production source content root.
 */
data class TestProjectModule(
    val name: String,
    val targetPlatform: TargetPlatform,
    val dependencies: List<Dependency>,
    val contentRoots: List<TestContentRoot>,
)

data class Dependency(val name: String, val kind: DependencyKind, val isExported: Boolean)

enum class DependencyKind {
    REGULAR,
    FRIEND,
    REFINEMENT
}

/**
 * @param path The content root path relative to the module root. If `null`, the path is equal to the module root.
 */
data class TestContentRoot(
    val path: String?,
    val kind: TestContentRootKind,
)

enum class TestContentRootKind {
    PRODUCTION,
    TESTS,
}

/**
 * Simplified platform representation for testing.
 * Shouldn't be used when precise HMPP platforms are important.
 * E.g., defaultCommonPlatform is not an accurate representation for a JVM+JS shared source set.
 */
enum class TestPlatform(val jsonName: String, val targetPlatform: TargetPlatform) {
    JVM("jvm", JvmPlatforms.defaultJvmPlatform),
    JS("js", JsPlatforms.defaultJsPlatform),
    NATIVE("native", NativePlatforms.unspecifiedNativePlatform),
    WASM("wasm", WasmPlatforms.unspecifiedWasmPlatform),
    COMMON("common", CommonPlatforms.defaultCommonPlatform),
}

object TestProjectModuleParser {
    private const val DEPENDENCIES_FIELD = "dependencies"
    private const val CONTENT_ROOTS_FIELD = "content_roots"
    private const val REFINEMENT_DEPENDENCIES_FIELD = "refinement_dependencies"
    private const val FRIEND_DEPENDENCIES_FIELD = "friend_dependencies"
    private const val MODULE_NAME_FIELD = "name"
    private const val PLATFORM_FIELD = "platform"
    private const val CONTENT_ROOT_PATH_FIELD = "path"
    private const val CONTENT_ROOT_KIND_FIELD = "kind"

    fun parse(json: JsonElement): TestProjectModule {
        require(json is JsonObject)

        val platform = json.getNullableString(PLATFORM_FIELD)?.let(::parsePlatform) ?: JvmPlatforms.defaultJvmPlatform
        val dependencies = buildList {
            addAll(parseDependencies(json, DEPENDENCIES_FIELD, DependencyKind.REGULAR))
            addAll(parseDependencies(json, REFINEMENT_DEPENDENCIES_FIELD, DependencyKind.REFINEMENT))
            addAll(parseDependencies(json, FRIEND_DEPENDENCIES_FIELD, DependencyKind.FRIEND))
        }
        val contentRoots = json.getAsJsonArray(CONTENT_ROOTS_FIELD)?.let(::parseContentRoots) ?: emptyList()

        return TestProjectModule(
            json.getString(MODULE_NAME_FIELD),
            platform,
            dependencies,
            contentRoots,
        )
    }

    /**
     * One of the platforms from [TestPlatform].
     */
    private fun parsePlatform(platformString: String): TargetPlatform {
        val platforms = TestPlatform.entries
        return platforms.firstOrNull { it.jsonName == platformString.lowercase() }?.targetPlatform
            ?: error("Unexpected platform '$platformString'. Expected one of the following: ${platforms.joinToString {it.jsonName}}")
    }

    /**
     * Parses a list of dependencies from [json];
     * an array of name strings or objects of kind `{name: string, isExported: boolean}` is expected.
     */
    private fun parseDependencies(json: JsonObject, jsonField: String, dependencyKind: DependencyKind): List<Dependency> =
        json.getAsJsonArray(jsonField)?.toList().orEmpty().map { dependency ->
            when (dependency) {
                is JsonPrimitive -> Dependency(dependency.asString, dependencyKind, isExported = false)
                is JsonObject ->
                    Dependency(dependency.getString("name"), dependencyKind, isExported = dependency.get("exported")?.asBoolean == true)
                else -> error("Unexpected json element type: ${dependency::class.java}")
            }
        }

    private fun parseContentRoots(json: JsonArray): List<TestContentRoot> {
        return json.map { element ->
            require(element is JsonObject)

            val path = element.getString(CONTENT_ROOT_PATH_FIELD)
            val kind = TestContentRootKind.valueOf(element.getString(CONTENT_ROOT_KIND_FIELD))

            TestContentRoot(path, kind)
        }
    }
}
