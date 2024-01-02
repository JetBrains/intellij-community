// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.jetbrains.kotlin.idea.base.util.getAsStringList
import org.jetbrains.kotlin.idea.base.util.getString
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
 * @param roots A list of labels for the library's roots. The test infrastructure generates a unique, temporary JAR file for each such
 *              label. This allows adding the same root to multiple libraries without having to wrangle JAR files in the test data.
 *              The property is optional in `structure.json`. If absent, the library receives a single, unique root which won't be shared
 *              with other libraries.
 */
data class TestProjectLibrary(val name: String, val roots: List<String>)

object TestProjectLibraryParser {
    private const val ROOTS_FIELD = "roots"

    fun parse(json: JsonElement): TestProjectLibrary {
        require(json is JsonObject)
        val roots = json.getAsStringList(ROOTS_FIELD) ?: listOf(UUID.randomUUID().toString())
        return TestProjectLibrary(json.getString("name"), roots)
    }
}

data class TestProjectModule(val name: String, val dependsOnModules: List<String>)

object TestProjectModuleParser {
    private const val DEPENDS_ON_FIELD = "dependsOn"

    fun parse(json: JsonElement): TestProjectModule {
        require(json is JsonObject)
        val dependencies = json.getAsStringList(DEPENDS_ON_FIELD) ?: emptyList()
        return TestProjectModule(
            json.getString("name"),
            dependencies
        )
    }
}
