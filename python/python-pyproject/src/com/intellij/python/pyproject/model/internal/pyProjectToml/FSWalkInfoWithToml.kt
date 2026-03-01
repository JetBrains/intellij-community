package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.python.pyproject.PyProjectToml
import java.nio.file.Path

// Files with toml content
@JvmInline
internal value class FSWalkInfoWithToml(val tomlFiles: Map<Path, PyProjectToml>)


// Files only
@JvmInline
value class FsWalkInfoNoToml(val rawTomlFiles: List<Path>)
