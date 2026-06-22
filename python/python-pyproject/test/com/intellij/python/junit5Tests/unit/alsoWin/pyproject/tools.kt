package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import java.nio.file.FileSystems

internal val SEP: String = FileSystems.getDefault().separator

internal operator fun String.div(other: String): String = "$this${SEP}$other"