package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.venvReader.Directory
import java.nio.file.Path

internal data class FSWalkInfo(val tomlFiles: Map<Path, PyProjectToml>, val excludeDir: Set<Directory>)