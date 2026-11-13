// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.skills

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.net.URI

/**
 * Adds skill registries to the Skills list of a skill manager, such as Air.
 *
 * A registry is a remote location, for example a GitHub tree, from which the user can install skills.
 * The skill manager lists the skills of each registry next to the installed skills.
 *
 * ### Threading
 *
 * The consumer calls [registryLocations] on `Dispatchers.IO`, so blocking work is allowed.
 * The hook must honor cancellation.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface SkillRegistryProvider {
  /** The registries to show for the working directory of [context]. */
  suspend fun registryLocations(context: SkillWorkspaceContext): List<URI>

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SkillRegistryProvider> = ExtensionPointName("com.intellij.mcpServer.skillRegistryProvider")
  }
}
