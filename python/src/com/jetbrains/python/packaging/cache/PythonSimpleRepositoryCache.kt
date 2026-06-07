// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.withBasicAuthorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.io.IOException
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator
import kotlin.text.lowercase

@ApiStatus.Internal
@Service
internal class PythonSimpleRepositoryCacheService {
  val repositories: List<PyPackageRepository>
    get() = cache.keys.toList()

  private val lock = Mutex()

  @Volatile
  private var cache: Map<PyPackageRepository, PythonSimpleRepositoryCache> = emptyMap()

  fun isEmpty(): Boolean = cache.isEmpty()

  operator fun get(key: PyPackageRepository): PythonSimpleRepositoryCache? = cache[key]

  suspend fun reloadAll(): Result<Unit, IOException> {
    lock.withLock {
      val service = service<PyPackageRepositories>()
      val newCache = mutableMapOf<PyPackageRepository, PythonSimpleRepositoryCache>()

      withContext(Dispatchers.IO) {
        for (repository in service.repositories) {
          val cache = PythonSimpleRepositoryCache(repository)

          cache.reloadCache().getOr { error ->
            thisLogger().error("Failed to refresh repository ${repository.repositoryUrl}")
            service.markInvalid(repository.repositoryUrl!!)
            return@withContext Result.Failure(error)
          }

          newCache[repository] = cache
        }
      }

      cache = newCache
    }

    return Result.Success(Unit)
  }
}

@ApiStatus.Internal
internal class PythonSimpleRepositoryCache(private val repository: PyPackageRepository) : PythonPackageCache {
  override val size: Int
    get() = cache.size

  @Volatile
  private var cache: Set<String> = emptySet()

  override fun contains(name: String): Boolean = name in cache

  override fun search(prefix: String, pageSize: Int): PythonPackageSearchResult {
    val needleLowercase = prefix.lowercase()
    val matches = cache.asSequence().filter { it.lowercase().startsWith(needleLowercase) }.toList()

    return InMemorySearchPage.resultFromMatches(matches, pageSize)
  }

  @CheckReturnValue
  suspend fun reloadCache(): Result<Unit, IOException> {
    return withContext(Dispatchers.IO) {
      val packages = mutableSetOf<String>()

      try {
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
      }
      catch (e: IOException) {
        return@withContext Result.Failure(e)
      }

      Result.Success(Unit)
    }
  }

  fun isEmpty(): Boolean = cache.isEmpty()

  companion object {
    private val userAgent: String
      get() = "${ApplicationNamesInfo.getInstance().productName}/${ApplicationInfo.getInstance().fullVersion}"
  }
}