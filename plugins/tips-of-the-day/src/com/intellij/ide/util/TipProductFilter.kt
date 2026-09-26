// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
class TipProductFilter {
  val tips: List<TipAndTrickBean> = computeTips()

  private fun computeTips(): List<TipAndTrickBean> {
    val all = TipAndTrickBean.EP_NAME.extensionList
    val allowed = allowedProducts()
    val seen = HashSet<String>(all.size)
    return all.filter { tip ->
      if (!seen.add(tip.fileName)) return@filter false
      if (allowed == null) return@filter true
      val products = resolveTipProducts(tip)
      products.isEmpty() || products.any { it in allowed }
    }
  }

  companion object {
    // Matches the product suffix inside a URL like `jar:file:/.../tips-goland-241.63.jar!/tips/Foo.html`
    private val TIP_JAR_REGEX = Regex("/tips-([a-z0-9-]+?)-\\d[^/]*\\.jar!/")

    private fun allowedProducts(): Set<String>? = when (PlatformUtils.getPlatformPrefix()) {
      PlatformUtils.IDEA_PREFIX -> setOf("intellij-idea", "intellij-idea-community", "big-data-tools", "database-plugin")
      PlatformUtils.IDEA_CE_PREFIX,
      PlatformUtils.IDEA_EDU_PREFIX -> setOf("intellij-idea-community")
      PlatformUtils.APPCODE_PREFIX -> setOf("appcode")
      PlatformUtils.CLION_PREFIX -> setOf("clion")
      PlatformUtils.GOIDE_PREFIX -> setOf("goland")
      PlatformUtils.RUBY_PREFIX -> setOf("rubymine")
      PlatformUtils.PHP_PREFIX -> setOf("phpstorm")
      PlatformUtils.WEB_PREFIX -> setOf("webstorm")
      PlatformUtils.DBE_PREFIX -> setOf("datagrip", "database-plugin")
      // PyCharm Pro and DataSpell both use the "Python" platform prefix.
      PlatformUtils.PYCHARM_PREFIX -> setOf("pycharm", "pycharm-community", "dataspell")
      PlatformUtils.PYCHARM_CE_PREFIX,
      PlatformUtils.PYCHARM_EDU_PREFIX -> setOf("pycharm-community")
      else -> null
    }

    private fun resolveTipProducts(tip: TipAndTrickBean): Set<String> {
      val loader = tip.pluginDescriptor?.pluginClassLoader ?: return emptySet()
      val products = HashSet<String>()
      val urls = loader.getResources("tips/${tip.fileName}")
      while (urls.hasMoreElements()) {
        TIP_JAR_REGEX.find(urls.nextElement().toString())?.groupValues?.get(1)?.let { products.add(it) }
      }
      return products
    }

    @JvmStatic
    fun getInstance(): TipProductFilter = service()
  }
}
