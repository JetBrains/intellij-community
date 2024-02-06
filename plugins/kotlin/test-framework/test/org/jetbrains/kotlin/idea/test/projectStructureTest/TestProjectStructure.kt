// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.google.gson.JsonElement
import com.google.gson.JsonObject
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

data class TestProjectModule(val name: String, val targetPlatform: TargetPlatform, val dependencies: List<Dependency>)
data class Dependency(val name: String, val kind: DependencyKind)

enum class DependencyKind(val jsonName: String) {
    REGULAR("regular"),
    FRIEND("friend"),
    REFINEMENT("refinement")
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
    WASM("wasm", WasmPlatforms.Default),
    COMMON("common", CommonPlatforms.defaultCommonPlatform),
}

object TestProjectModuleParser {
    private const val DEPENDENCIES_FIELD = "dependencies"
    private const val MODULE_NAME_FIELD = "name"
    private const val DEPENDENCY_NAME_FIELD = "name"
    private const val DEPENDENCY_KIND_FIELD = "kind"
    private const val PLATFORM_FIELD = "platform"

    fun parse(json: JsonElement): TestProjectModule {
        require(json is JsonObject)
        val dependencies = json.getAsJsonArray(DEPENDENCIES_FIELD)?.map(::parseDependency).orEmpty()
        val platform = json.getNullableString(PLATFORM_FIELD)?.let(::parsePlatform) ?: JvmPlatforms.defaultJvmPlatform
        return TestProjectModule(
            json.getString(MODULE_NAME_FIELD),
            platform,
            dependencies,
        )
    }

    /**
     * String: only the name, interpreted as a regular dependency.
     * JSON: [DEPENDENCY_NAME_FIELD] defines module name (or library name, if such exists, for regular dependencies);
     * [DEPENDENCY_KIND_FIELD] defines the dependency kind, one of [DependencyKind].
     */
    private fun parseDependency(dependencyStringOrObject: JsonElement): Dependency {
        return when {
            dependencyStringOrObject.isJsonObject -> parseDependencyObject(dependencyStringOrObject.asJsonObject)
            else -> Dependency(dependencyStringOrObject.asString, DependencyKind.REGULAR)
        }
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
     * The name is mandatory, the kind is optional.
     * If the kind is absent, the dependency is considered regular.
     */
    private fun parseDependencyObject(dependencyObject: JsonObject): Dependency {
        val name = dependencyObject.getString(DEPENDENCY_NAME_FIELD)
        val kind = dependencyObject.getNullableString(DEPENDENCY_KIND_FIELD)?.let { kind ->
            DependencyKind.entries.firstOrNull { it.jsonName == kind }
                ?: error("Unexpected kind '$kind'. Expected one of the following: " +
                                 DependencyKind.entries.joinToString { it.jsonName })
        } ?: DependencyKind.REGULAR
        return Dependency(name, kind)
    }
}
