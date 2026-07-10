// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import java.nio.file.Path

/**
 * Asset bundle root for `WebViewEngine.loadAsset`.
 *
 * The root describes where bytes come from. Loading still goes through a WebView asset handler and a virtual origin;
 * [fromDirectory] does not navigate to `file:` URLs.
 */
@ApiStatus.Experimental
class WebViewAssetRoot private constructor(
  internal val source: WebViewAssetSource,
  internal val scopedAssetProviders: List<WebViewScopedAssetProvider>,
  internal val iconSets: Map<String, WebViewIconSet>,
  internal val viewId: String?,
) {
  /**
   * Routes requests under [prefix] to [provider] before falling back to this root's regular assets.
   *
   * If [prefix] matches a request, the provider owns the request completely: missing provider results are returned as
   * `404` and are not resolved from the regular asset root.
   */
  fun withScopedAssetProvider(prefix: WebViewAssetPath, provider: WebViewAssetProvider): WebViewAssetRoot {
    return WebViewAssetRoot(source, scopedAssetProviders + WebViewScopedAssetProvider(prefix, provider), iconSets, viewId)
  }

  /**
   * Registers classloader-backed icon namespaces available to this WebView page.
   *
   * Registered ids are used by frontend `IconSet.define(id)` objects. Resource paths requested from the page are resolved
   * from the fixed classloader captured in each [WebViewIconSet]. Duplicate ids are rejected.
   */
  fun withIconSets(vararg iconSets: WebViewIconSet): WebViewAssetRoot {
    if (iconSets.isEmpty()) return this

    val updatedIconSets = LinkedHashMap(this.iconSets)
    for (iconSet in iconSets) {
      require(!updatedIconSets.containsKey(iconSet.id)) { "Duplicate WebView icon set id: ${iconSet.id}" }
      updatedIconSets[iconSet.id] = iconSet
    }
    return WebViewAssetRoot(source, scopedAssetProviders, updatedIconSets.toMap(), viewId)
  }

  companion object {
    @PublishedApi
    internal const val VIEWS_ROOT: String = "webview/views"

    /**
     * Recommended way to create an asset root for a bundled WebView view, anchored to the **calling class**.
     *
     * Resolves assets under `webview/views/<viewId>` from the caller's classloader (the standard layout described in
     * the WebView UI Authoring Guide). In development runs the matching source directory is derived automatically from
     * the caller module's resource roots, so edits are picked up without rebuilding resources.
     *
     * The calling class is captured at the call site. Pass it explicitly via the `forView(Class, String)` overload when
     * the root is created on behalf of a different module (or from Java).
     *
     * [viewsRoot] is the classpath prefix under which views live; it defaults to `webview/views`.
     */
    @JvmSynthetic
    @Suppress("NOTHING_TO_INLINE")
    inline fun forView(viewId: String, viewsRoot: String = VIEWS_ROOT): WebViewAssetRoot {
      return forView(MethodHandles.lookup().lookupClass(), viewId, viewsRoot)
    }

    /**
     * Creates an asset root for a bundled WebView view under `webview/views/<viewId>`, anchored to [owner].
     *
     * Loads assets from [owner]'s classloader and, in development runs, derives the matching source directory from
     * [owner]'s module resource roots. From Kotlin, prefer the no-class `forView(viewId)` overload.
     *
     * [viewsRoot] is the classpath prefix under which views live; it defaults to `webview/views`.
     */
    @JvmStatic
    @JvmOverloads
    fun forView(owner: Class<*>, viewId: String, viewsRoot: String = VIEWS_ROOT): WebViewAssetRoot {
      val trimmedViewId = viewId.trim()
      return WebViewAssetRoot(
        WebViewAssetSource.Classpath(owner, viewAssetPath(viewId, viewsRoot), devSourceRoot = null),
        emptyList(),
        emptyMap(),
        trimmedViewId,
      )
    }

    /**
     * Low-level: uses assets packaged under [root] in [owner]'s classloader. Prefer [forView] for the standard
     * `webview/views/<viewId>` layout; use this only for non-standard asset locations.
     *
     * [devSourceRoot] is an explicit development-only asset directory matching [root]. It is an escape hatch for generated
     * or non-JPS layouts; normal source runs derive this directory automatically from module resource roots when possible.
     */
    @JvmStatic
    @JvmOverloads
    fun fromClasspath(owner: Class<*>, root: WebViewAssetPath, devSourceRoot: Path? = null): WebViewAssetRoot {
      return WebViewAssetRoot(
        WebViewAssetSource.Classpath(owner, root, devSourceRoot?.toAbsolutePath()?.normalize()),
        emptyList(),
        emptyMap(),
        null,
      )
    }

    /**
     * Low-level: uses files under [root] as assets while keeping the same handler-backed loading path as classpath
     * assets. Prefer [forView] for bundled views.
     */
    @JvmStatic
    fun fromDirectory(root: Path): WebViewAssetRoot {
      return WebViewAssetRoot(WebViewAssetSource.Directory(root.toAbsolutePath().normalize()), emptyList(), emptyMap(), null)
    }

    private fun viewAssetPath(viewId: String, viewsRoot: String): WebViewAssetPath {
      val trimmedId = viewId.trim()
      require(trimmedId.isNotEmpty()) { "WebView view id must not be blank" }
      require('/' !in trimmedId && '\\' !in trimmedId) { "WebView view id must be a single path segment: $viewId" }
      val trimmedRoot = viewsRoot.trim().trim('/')
      require(trimmedRoot.isNotEmpty()) { "WebView views root must not be blank" }
      return WebViewAssetPath.of("$trimmedRoot/$trimmedId")
    }
  }
}
