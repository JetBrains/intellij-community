// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.jetbrains.python.PyNames
import org.jetbrains.annotations.ApiStatus

/**
 * The `site-packages` and `dist-packages` directories under [classRoots] that the project excludes. A class root covers
 * its whole tree, so the stdlib root of a virtual environment also covers the packages of its base interpreter, which
 * the environment cannot import. A directory that is a class root of a used interpreter, see [keptClassRoots], is not
 * excluded: the exclusion is the same for the whole project, so it must not hide the packages of another interpreter.
 */
@ApiStatus.Internal
fun sitePackagesToExclude(classRoots: Collection<VirtualFileUrl>, keptClassRoots: Set<VirtualFileUrl>): List<VirtualFileUrl> =
  classRoots.flatMap { root -> listOf(root.append(PyNames.SITE_PACKAGES), root.append(PyNames.DIST_PACKAGES)) }
    .filter { it !in keptClassRoots }
    .distinct()
