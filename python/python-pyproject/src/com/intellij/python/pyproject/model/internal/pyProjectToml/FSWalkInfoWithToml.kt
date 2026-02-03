package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.venvReader.Directory
import java.nio.file.Path

// Files with toml content
internal data class FSWalkInfoWithToml(val tomlFiles: Map<Path, PyProjectToml>, val excludeDir: Set<Directory>)


// Files only
data class FsWalkInfoNoToml(val rawTomlFiles: List<Path>, val excludedDirs: List<Directory>)
