package org.jetbrains.idea.reposearch

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Consumer

typealias ResultConsumer = (RepositoryArtifactData) -> Unit


@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class DependencySearchService(private val project: Project) : Disposable {
  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("DependencySearch", 2)
  private val cache = CollectionFactory.createConcurrentWeakKeyWeakValueMap<String, CompletableFuture<Collection<RepositoryArtifactData>>>()
  private val deferredCache = CollectionFactory.createConcurrentWeakKeyWeakValueMap<DeferredCacheKey, Deferred<Collection<RepositoryArtifactData>>>()
  private fun remoteProviders() = EP_NAME.extensionList.flatMap { it.getProviders(project) }.filter { !it.isLocal() }
  private fun localProviders() = EP_NAME.extensionList.flatMap { it.getProviders(project) }.filter { it.isLocal() }

  private data class DeferredCacheKey(val provider: DependencySearchProvider, val cacheKey: String)


  override fun dispose() {
  }


  private fun performSearch(cacheKey: String,
                            parameters: SearchParameters,
                            consumer: ResultConsumer,
                            searchMethod: (DependencySearchProvider, ResultConsumer) -> Unit): Promise<Int> {

    if (parameters.useCache()) {
      val cachedValue = foundInCache(cacheKey, consumer)
      if (cachedValue != null) {
        consumer(PoisonedRepositoryArtifactData.INSTANCE)
        return cachedValue
      }
    }

    val thisNewFuture = CompletableFuture<Collection<RepositoryArtifactData>>()
    val existingFuture = cache.putIfAbsent(cacheKey, thisNewFuture)
    if (existingFuture != null && parameters.useCache()) {
      val result = fillResultsFromCache(existingFuture, consumer)
      consumer(PoisonedRepositoryArtifactData.INSTANCE)
      return result
    }


    val localResultSet = RepositoryArtifactDataStorage()
    localProviders().forEach { lp -> searchMethod(lp) { localResultSet.add(it) } }
    localResultSet.getAll().forEach(consumer)

    val remoteProviders = remoteProviders()

    if (parameters.isLocalOnly || remoteProviders.isEmpty()) {
      thisNewFuture.complete(localResultSet.getAll())
      consumer(PoisonedRepositoryArtifactData.INSTANCE)
      return resolvedPromise(0)
    }

    val promises: MutableList<Promise<Void>> = ArrayList(remoteProviders.size)
    val resultSet = RepositoryArtifactDataStorage()
    for (provider in remoteProviders) {
      val promise = AsyncPromise<Void>()
      promises.add(promise)
      val wrapper = ProgressWrapper.wrap(ProgressIndicatorProvider.getInstance().progressIndicator)
      executorService.submit {
        try {
          ProgressManager.getInstance().runProcess({
                                                     searchMethod(provider) {
                                                       resultSet.add(it)
                                                       consumer(it)
                                                     }
                                                     promise.setResult(null)
                                                   }, wrapper)
        }
        catch (e: Exception) {
          logWarn("Exception getting data from provider $provider", e)
          promise.setError(e)
        }
      }
    }

    return promises.all(resultSet, ignoreErrors = true).then {
      consumer(PoisonedRepositoryArtifactData.INSTANCE)
      if (!resultSet.isEmpty() && existingFuture == null) {
        thisNewFuture.complete(resultSet.getAll())
      }
      return@then 1
    }
  }


  @Deprecated("prefer async method", ReplaceWith("suggestPrefixAsync(groupId, artifactId, parameters, consumer) }"))
  fun suggestPrefix(groupId: String, artifactId: String,
                    parameters: SearchParameters,
                    consumer: Consumer<RepositoryArtifactData>) = suggestPrefix(groupId, artifactId, parameters) { consumer.accept(it) }

  @Deprecated("prefer async method", ReplaceWith("suggestPrefixAsync(groupId, artifactId, parameters, consumer) }"))
  fun suggestPrefix(groupId: String, artifactId: String,
                    parameters: SearchParameters,
                    consumer: ResultConsumer): Promise<Int> {
    val cacheKey = "_$groupId:$artifactId"
    return performSearch(cacheKey, parameters, consumer) { p, c ->
      val prefixes = runBlockingMaybeCancellable { p.suggestPrefix(groupId, artifactId) }
      prefixes.forEach(c) // TODO A consumer here is used synchronously...
    }
  }

  private suspend fun performSearchAsync(cacheKey: String,
                                         parameters: SearchParameters,
                                         consumer: ResultConsumer,
                                         searchMethod: suspend (DependencySearchProvider, ResultConsumer) -> Unit) {
    val providers = mutableSetOf<DependencySearchProvider>()
    providers.addAll(localProviders())
    if (!parameters.isLocalOnly) {
      providers.addAll(remoteProviders())
    }

    supervisorScope {
      providers.map {
        launch {
          performSearchAsync(it, cacheKey, parameters, consumer, searchMethod)
        }
      }
    }
  }

    private suspend fun performSearchAsync(provider: DependencySearchProvider,
                                           cacheKey: String,
                                           parameters: SearchParameters,
                                           consumer: ResultConsumer,
                                           searchMethod: suspend (DependencySearchProvider, ResultConsumer) -> Unit) {
    val thisNewDeferred = CompletableDeferred<Collection<RepositoryArtifactData>>()
    val existingDeferred = deferredCache.putIfAbsent(DeferredCacheKey(provider, cacheKey), thisNewDeferred)
    if (existingDeferred != null && parameters.useCache()) {
      fillResultsFromDeferredCache(existingDeferred, consumer)
      return
    }

    val resultSet = RepositoryArtifactDataStorage()

    try {
      searchMethod(provider) {
        resultSet.add(it)
        consumer(it)
      }
    }
    catch (e: Exception) {
      logWarn("Exception getting data from provider $provider", e)
    }

    if (!resultSet.isEmpty() && existingDeferred == null) {
      thisNewDeferred.complete(resultSet.getAll())
    }
  }

  private fun fillResultsFromDeferredCache(deferred: Deferred<Collection<RepositoryArtifactData>>, consumer: ResultConsumer) {
    deferred.invokeOnCompletion {
      BiConsumer { r: Collection<RepositoryArtifactData>, e: Throwable? ->
        if (e != null) {
          logWarn("Exception getting data from cache", e)
        }
        else {
          r.forEach(consumer)
        }
      }
    }
  }

  suspend fun suggestPrefixAsync(groupId: String, artifactId: String,
                                 parameters: SearchParameters,
                                 consumer: Consumer<RepositoryArtifactData>) = suggestPrefixAsync(
    groupId, artifactId, parameters) { consumer.accept(it) }

  suspend fun suggestPrefixAsync(groupId: String, artifactId: String,
                                 parameters: SearchParameters,
                                 consumer: ResultConsumer) {
    val cacheKey = "_$groupId:$artifactId"
    performSearchAsync(cacheKey, parameters, consumer) { p, c ->
      p.suggestPrefix(groupId, artifactId)
        .forEach(c) // TODO A consumer here is used synchronously...
    }
  }


  @Deprecated("prefer async method", ReplaceWith("fulltextSearchAsync(searchString, parameters, consumer) }"))
  fun fulltextSearch(searchString: String,
                     parameters: SearchParameters,
                     consumer: Consumer<RepositoryArtifactData>) = fulltextSearch(searchString, parameters) { consumer.accept(it) }


  @Deprecated("prefer async method", ReplaceWith("fulltextSearchAsync(searchString, parameters, consumer) }"))
  fun fulltextSearch(searchString: String,
                     parameters: SearchParameters,
                     consumer: ResultConsumer): Promise<Int> {
    return performSearch(searchString, parameters, consumer) { p, c ->
      val searchResults = runBlockingMaybeCancellable { p.fulltextSearch(searchString) }
      searchResults.forEach(c) // TODO A consumer here is used synchronously...
    }
  }

  suspend fun fulltextSearchAsync(searchString: String,
                                  parameters: SearchParameters,
                                  consumer: Consumer<RepositoryArtifactData>) = fulltextSearchAsync(searchString, parameters) {
    consumer.accept(it)
  }

  suspend fun fulltextSearchAsync(searchString: String,
                                  parameters: SearchParameters,
                                  consumer: ResultConsumer) {
    performSearchAsync(searchString, parameters, consumer) { p, c ->
      val searchResults = p.fulltextSearch(searchString)
      searchResults.forEach(c) // TODO A consumer here is used synchronously...
    }
  }

  fun getGroupIds(pattern: String?): Set<String> {
    val result = mutableSetOf<String>()
    fulltextSearch(pattern ?: "", SearchParameters(true, true)) {
      if (it is MavenRepositoryArtifactInfo) {
        result.add(it.groupId)
      }
    }
    return result
  }

  fun getArtifactIds(groupId: String): Set<String> {
    ProgressIndicatorProvider.checkCanceled()
    val result = mutableSetOf<String>()
    fulltextSearch("$groupId:", SearchParameters(true, true)) {
      if (it is MavenRepositoryArtifactInfo) {
        if (groupId == it.groupId) {
          result.add(it.artifactId)
        }
      }
    }
    return result
  }

  fun getVersions(groupId: String, artifactId: String): Set<String> {
    ProgressIndicatorProvider.checkCanceled()
    val result = mutableSetOf<String>()
    fulltextSearch("$groupId:$artifactId", SearchParameters(true, true)) {
      if (it is MavenRepositoryArtifactInfo) {
        if (groupId == it.groupId && artifactId == it.artifactId) {
          for (item in it.items) {
            if (item.version != null) result.add(item.version!!)
          }
        }
      }
    }
    return result
  }

  private fun foundInCache(searchString: String, consumer: ResultConsumer): Promise<Int>? {
    val future = cache[searchString]
    if (future != null) {
      return fillResultsFromCache(future, consumer)
    }
    return null
  }

  private fun fillResultsFromCache(future: CompletableFuture<Collection<RepositoryArtifactData>>,
                                   consumer: ResultConsumer): AsyncPromise<Int> {
    val p: AsyncPromise<Int> = AsyncPromise()
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


  companion object {

    @JvmField
    val EP_NAME = ExtensionPointName<DependencySearchProvidersFactory>("org.jetbrains.idea.reposearch.provider")

    @JvmStatic
    fun getInstance(project: Project): DependencySearchService = project.service()
  }

  fun clearCache() {
    cache.clear()
  }


  class RepositoryArtifactDataStorage {
    private val map = HashMap<String, RepositoryArtifactData>()

    @Synchronized
    fun add(data: RepositoryArtifactData) {
      map.merge(data.key, data) { old, new -> old.mergeWith(new) }
    }

    fun getAll() = map.values
    fun isEmpty() = map.isEmpty()
  }
}