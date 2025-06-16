package com.intellij.mcpserver.util

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

fun Path.resolveRel(pathInProject: String): Path {
    return when (pathInProject) {
        "/" -> this
        else -> resolve(pathInProject.removePrefix("/"))
    }
}

fun Path.relativizeByProjectDir(projDir: Path?): String =
    projDir?.relativize(this)?.pathString ?: this.absolutePathString()