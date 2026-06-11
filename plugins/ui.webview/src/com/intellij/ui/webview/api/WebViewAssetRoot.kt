// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Asset bundle root for [WebViewEngine.loadAsset].
 *
 * The root describes where bytes come from. Loading still goes through a WebView asset handler and a virtual origin;
 * [fromDirectory] does not navigate to `file:` URLs.
 */
@ApiStatus.Experimental
class WebViewAssetRoot private constructor(
  internal val source: WebViewAssetSource,
  internal val scopedAssetProviders: List<WebViewScopedAssetProvider>,
) {
  /**
   * Routes requests under [prefix] to [provider] before falling back to this root's regular assets.
   *
   * If [prefix] matches a request, the provider owns the request completely: missing provider results are returned as
   * `404` and are not resolved from the regular asset root.
   */
  fun withScopedAssetProvider(prefix: WebViewAssetPath, provider: WebViewAssetProvider): WebViewAssetRoot {
    return WebViewAssetRoot(source, scopedAssetProviders + WebViewScopedAssetProvider(prefix, provider))
  }

  companion object {
    /**
     * Uses assets packaged under [root] in [owner]'s classloader.
     *
     * [devSourceRoot] is an explicit development-only asset directory matching [root]. It is an escape hatch for generated
     * or non-JPS layouts; normal source runs derive this directory automatically from module resource roots when possible.
     */
    @JvmStatic
    @JvmOverloads
    fun fromClasspath(owner: Class<*>, root: WebViewAssetPath, devSourceRoot: Path? = null): WebViewAssetRoot {
      return WebViewAssetRoot(WebViewAssetSource.Classpath(owner, root, devSourceRoot?.toAbsolutePath()?.normalize()), emptyList())
    }

    /**
     * Uses files under [root] as assets while keeping the same handler-backed loading path as classpath assets.
     */
    @JvmStatic
    fun fromDirectory(root: Path): WebViewAssetRoot {
      return WebViewAssetRoot(WebViewAssetSource.Directory(root.toAbsolutePath().normalize()), emptyList())
    }
  }
}
