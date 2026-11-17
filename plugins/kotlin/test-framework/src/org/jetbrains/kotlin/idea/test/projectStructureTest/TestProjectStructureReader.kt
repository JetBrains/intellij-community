// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.projectStructureTest

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path

internal object TestProjectStructureReader {
    fun readJsonFile(jsonFile: Path): JsonObject {
        @Suppress("DEPRECATION") // AS 4.0
        val json = JsonParser().parse(FileUtil.loadFile(jsonFile.toFile(), /*convertLineSeparators=*/true))
        require(json is JsonObject)
        return json
    }

    fun <S : TestProjectStructure> parseTestStructure(
        json: JsonObject,
        parser: TestProjectStructureParser<S>,
    ): S {
        val libraries = json.getAsJsonArray(TestProjectStructureFields.LIBRARIES_FIELD)?.map(TestProjectLibraryParser::parse).orEmpty()
        val modules = json.getAsJsonArray(TestProjectStructureFields.MODULES_FIELD)?.map(TestProjectModuleParser::parse).orEmpty()
        return parser.parse(libraries, modules, json)
    }
}
