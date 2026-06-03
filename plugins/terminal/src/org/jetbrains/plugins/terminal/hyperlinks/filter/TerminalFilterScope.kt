// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.filter

import com.intellij.openapi.project.Project
import com.intellij.psi.search.DelegatingGlobalSearchScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalFilterScope(
  project: Project,
  val filterContext: TerminalHyperlinkFilterContext?
): DelegatingGlobalSearchScope(allScope(project))
