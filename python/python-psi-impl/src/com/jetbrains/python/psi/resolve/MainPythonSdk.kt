// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel

/**
 * The interpreter of the module at the project root, for a file that belongs to no module, such as a scratch file.
 *
 * Replaces `ProjectRootManager.getProjectSdk()`, which reads `project-jdk-name` from `.idea/misc.xml`. That attribute
 * is missing or stale in the projects PY-89831 reports.
 *
 * Internal to resolve, which cannot suspend. It answers `null` until the first structure lands, and nothing
 * invalidates what was resolved in that window, so it is not an answer to hand out. Everything else asks
 * [com.intellij.python.pyproject.model.evolution.findMainPythonSdk], which waits.
 */
internal fun Project.mainPythonSdk(): Sdk? = service<EvoPyProjectModel>().snapshotOrNull()?.main?.sdk
