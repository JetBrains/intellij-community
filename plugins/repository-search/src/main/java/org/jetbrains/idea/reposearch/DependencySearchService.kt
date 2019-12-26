package org.jetbrains.idea.reposearch

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.function.BiConsumer

typealias ResultConsumer = (RepositoryArtifactData) -> Unit

class DependencySearchService(private val myProject: Project) {
  private val myExecutorService: ExecutorService
  private val cache: MutableMap<String, CompletableFuture<Collection<RepositoryArtifactData>>> = ContainerUtil.createConcurrentWeakKeyWeakValueMap()
  private val remoteProviders: MutableList<DependencySearchProvider> = ArrayList()
  private val localProviders: MutableList<DependencySearchProvider> = ArrayList()

  fun updateProviders() {
    remoteProviders.clear()
    localProviders.clear()
    DependencySearchProvidersFactory.EXTENSION_POINT_NAME.extensionList.forEach { f ->
      f.getProviders(myProject).forEach { provider ->
        if (provider.isLocal) {
          localProviders.add(provider)
        }
        else {
          remoteProviders.add(provider)
        }
      }
    }
  }

  private fun performSearch(cacheKey: String,
                            parameters: SearchParameters,
                            consumer: ResultConsumer,
                            searchMethod: (DependencySearchProvider, ResultConsumer) -> Unit): Promise<*> {
    if (parameters.useCache()) {
      val cachedValue = foundInCache(cacheKey, parameters, consumer)
      if (cachedValue != null) {
        return cachedValue
      }
    }

    val resultSet: MutableSet<RepositoryArtifactData> = ConcurrentHashMap.newKeySet()
    localProviders.forEach { lp -> searchMethod(lp) { resultSet.add(it) } }
    val promises: MutableList<Promise<Void>> = ArrayList(remoteProviders.size)

    for (provider in remoteProviders) {
      val promise = AsyncPromise<Void>()
      promises.add(promise)
      myExecutorService.execute {
        try {
          searchMethod(provider) { if (resultSet.add(it)) consumer(it) }
          promise.setResult(null)
        }
        catch (e: Exception) {
          promise.setError(e)
        }
      }
    }
    return promises.all(promises.size, true).onSuccess {
      if (!resultSet.isEmpty()) {
        cache[cacheKey] = completedFuture<Collection<RepositoryArtifactData>>(resultSet)
      }
    }
  }

  fun suggestPrefix(groupId: String, artifactId: String,
                    parameters: SearchParameters,
                    consumer: ResultConsumer): Promise<*> {
    val cacheKey = "_$groupId:$artifactId"
    return performSearch(cacheKey, parameters, consumer) { p, c ->
      p.suggestPrefix(groupId, artifactId, consumer)
    }
  }

  fun fulltextSearch(searchString: String,
                     parameters: SearchParameters,
                     consumer: ResultConsumer): Promise<*> {
    return performSearch(searchString, parameters, consumer) { p, c ->
      p.fulltextSearch(searchString, consumer)
    }
  }

  private fun foundInCache(searchString: String,
                           parameters: SearchParameters,
                           consumer: ResultConsumer): Promise<*>? {
    val future = cache[searchString]
    if (future != null) {
      val p: AsyncPromise<*> = AsyncPromise<Any>()
      future.whenComplete(
        BiConsumer { r: Collection<RepositoryArtifactData>, e: Throwable? ->
          if (e != null) {
            p.setError(e)
          }
          else {
            r.forEach(consumer)
            p.setResult(null)
          }
        })
      return p
    }
    return null
  }


  companion object {
    fun getInstance(project: Project): DependencySearchService {
      return project.getService(DependencySearchService::class.java)
    }
  }

  init {
    myExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("DependencySearch", 2)
    DependencySearchProvidersFactory.EXTENSION_POINT_NAME.addExtensionPointListener(
      object : ExtensionPointListener<DependencySearchProvidersFactory?> {
        override fun extensionAdded(extension: DependencySearchProvidersFactory,
                                    pluginDescriptor: PluginDescriptor) {
          updateProviders()
        }

        override fun extensionRemoved(extension: DependencySearchProvidersFactory,
                                      pluginDescriptor: PluginDescriptor) {
          updateProviders()
        }
      }, myProject)
    updateProviders()
  }
}