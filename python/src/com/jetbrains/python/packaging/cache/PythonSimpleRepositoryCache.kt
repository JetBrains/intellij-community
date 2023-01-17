// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.withBasicAuthorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

@ApiStatus.Experimental
@Service
class PythonSimpleRepositoryCache : PythonPackageCache<PyPackageRepository> {

  private var cache: Map<PyPackageRepository, List<String>> = emptyMap()

  val repositories: List<PyPackageRepository>
    get() = cache.keys.toList()
  override val packages: List<String>
    get() = cache.values.asSequence().flatten().toList()

  private val userAgent: String
    get() = "${ApplicationNamesInfo.getInstance().productName}/${ApplicationInfo.getInstance().fullVersion}"

  suspend fun refresh() {
    val service = service<PyPackageRepositories>()
    withContext(Dispatchers.IO) {
      val newCache = mutableMapOf<PyPackageRepository, List<String>>()
      service.repositories.forEach {
        try {
          newCache[it] = loadFrom(it)
        }
        catch (ex: Exception) {
          thisLogger().error("could not refresh repository ${it.repositoryUrl}")
          service.markInvalid(it.repositoryUrl!!)
        }
      }
      withContext(Dispatchers.Main) {
        cache = newCache
      }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun loadFrom(repository: PyPackageRepository): List<String> {
    return withContext(Dispatchers.IO) {
      val packages = mutableListOf<String>()
      HttpRequests.request(repository.repositoryUrl!!)
        .userAgent(userAgent)
        .withBasicAuthorization(repository)
        .connect { request ->
          ParserDelegator().parse(request.reader, object : HTMLEditorKit.ParserCallback() {
            var myTag: HTML.Tag? = null
            override fun handleStartTag(tag: HTML.Tag, set: MutableAttributeSet, i: Int) {
              myTag = tag
            }

            override fun handleText(data: CharArray, pos: Int) {
              if ("a" == myTag?.toString()) {
                var packageName = String(data)
                if (packageName.endsWith("/")) {
                  packageName = packageName.substring(0, packageName.indexOf("/"))
                }
                packages.add(packageName)
              }
            }

            override fun handleEndTag(t: HTML.Tag, pos: Int) {
              myTag = null
            }
          }, true)

        }
      packages
    }
  }

  operator fun get(key: PyPackageRepository): List<String>? = cache[key]

  override fun isEmpty(): Boolean = cache.isEmpty()

  override fun contains(key: PyPackageRepository): Boolean = key in cache
}