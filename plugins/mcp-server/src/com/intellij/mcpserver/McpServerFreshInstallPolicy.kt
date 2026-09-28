package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Lets a product start the MCP server on a fresh install, before the user agreed to it.
 *
 * The plugin is shared, and it does not know which product it runs in. So a product that wants this contributes the
 * policy from its own code, and every other product keeps the server off until the user turns it on.
 *
 * A product that opts in also gets the first-call consent request of `McpServerConsentGate`, because the server then
 * runs without an answer from the user.
 *
 * A product can also enable the server for an existing settings store. The product implementation must limit this
 * migration when it must run only once.
 */
@ApiStatus.Internal
interface McpServerFreshInstallPolicy {
  /** Whether a fresh install of this product starts the MCP server. */
  fun isServerEnabledOnFreshInstall(): Boolean

  /** Whether this product starts the MCP server for an existing settings store. */
  fun isServerEnabledOnExistingInstall(): Boolean = false

  companion object {
    val EP: ExtensionPointName<McpServerFreshInstallPolicy> =
      ExtensionPointName.create("com.intellij.mcpServer.freshInstallPolicy")
  }
}
