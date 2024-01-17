// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
