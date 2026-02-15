// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands.dto

data class MoveFilesData(val files: List<String>, val toDirectory: String, val spanTag: String = "")