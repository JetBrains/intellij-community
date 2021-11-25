package org.jetbrains.idea.reposearch

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.BiConsumer
import java.util.function.Consumer

typealias ResultConsumer = (RepositoryArtifactData) -> Unit

@ApiStatus.Experimental
class DependencySearchService(private val myProject: Project) {
  private val myExecutorService: ExecutorService
  private val cache: MutableMap<String, CompletableFuture<Collection<RepositoryArtifactData>>> = ContainerUtil.createConcurrentWeakKeyWeakValueMap()

  @Volatile
  private var remoteProviders: MutableList<DependencySearchProvider> = ArrayList()
  @Volatile
  private var localProviders: MutableList<DependencySearchProvider> = ArrayList()

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

  @Synchronized
  fun updateProviders() {
    val newRemoteProviders = mutableListOf<DependencySearchProvider>();
    val newLocalProviders = mutableListOf<DependencySearchProvider>();

    if (myProject.isDisposed) return
    for (f in DependencySearchProvidersFactory.EXTENSION_POINT_NAME.extensionList) {
      if (!f.isApplicable(myProject)) {
        continue
      }

      for (provider in f.getProviders(myProject)) {
        if (provider.isLocal) {
          newLocalProviders.add(provider)
        }
        else {
          newRemoteProviders.add(provider)
        }
      }
    }
    localProviders = newLocalProviders
    remoteProviders = newRemoteProviders
    cache.clear()
  }

  private fun performSearch(cacheKey: String,
                            parameters: SearchParameters,
                            consumer: ResultConsumer,
                            searchMethod: (DependencySearchProvider, ResultConsumer) -> Unit): Promise<Int> {

    if (parameters.useCache()) {
      val cachedValue = foundInCache(cacheKey, consumer)
      if (cachedValue != null) {
        return cachedValue
      }
    }

    val thisNewFuture = CompletableFuture<Collection<RepositoryArtifactData>>()
    val existingFuture = cache.putIfAbsent(cacheKey, thisNewFuture)
    if (existingFuture != null && parameters.useCache()) {
      return fillResultsFromCache(existingFuture, consumer)
    }


    val localResultSet: MutableSet<RepositoryArtifactData> = LinkedHashSet()
    localProviders.forEach { lp -> searchMethod(lp) { localResultSet.add(it) } }
    localResultSet.forEach(consumer)


    if (parameters.isLocalOnly || remoteProviders.size == 0) {
      thisNewFuture.complete(localResultSet)
      return resolvedPromise(0)
    }

    val promises: MutableList<Promise<Void>> = ArrayList(remoteProviders.size)
    val resultSet = Collections.synchronizedSet(localResultSet)
    for (provider in remoteProviders) {
      val promise = AsyncPromise<Void>()
      promises.add(promise)
      val wrapper = ProgressWrapper.wrap(ProgressIndicatorProvider.getInstance().progressIndicator)
      myExecutorService.submit {
        try {
          ProgressManager.getInstance().runProcess({
                                                     searchMethod(provider) { if (resultSet.add(it)) consumer(it) }
                                                     promise.setResult(null)
                                                   }, wrapper);
        }
        catch (e: Exception) {
          promise.setError(e)
        }
      }
    }

    return promises.all(resultSet, ignoreErrors = true).then {
      if (!resultSet.isEmpty() && existingFuture == null) {
        thisNewFuture.complete(resultSet)
      }
      return@then 1
    }
  }


  fun suggestPrefix(groupId: String, artifactId: String,
                    parameters: SearchParameters,
                    consumer: Consumer<RepositoryArtifactData>) = suggestPrefix(groupId, artifactId, parameters) { consumer.accept(it) }

  fun suggestPrefix(groupId: String, artifactId: String,
                    parameters: SearchParameters,
                    consumer: ResultConsumer): Promise<Int> {
    val cacheKey = "_$groupId:$artifactId"
    return performSearch(cacheKey, parameters, consumer) { p, c ->
      p.suggestPrefix(groupId, artifactId, c)
    }
  }


  fun fulltextSearch(searchString: String,
                     parameters: SearchParameters,
                     consumer: Consumer<RepositoryArtifactData>) = fulltextSearch(searchString, parameters) { consumer.accept(it) }


  fun fulltextSearch(searchString: String,
                     parameters: SearchParameters,
                     consumer: ResultConsumer): Promise<Int> {
    return performSearch(searchString, parameters, consumer) { p, c ->
      p.fulltextSearch(searchString, c)
    }
  }

  fun getGroupIds(pattern: String?): Set<String>{
    val result = mutableSetOf<String>()
    fulltextSearch(pattern ?: "", SearchParameters(true, true)) {
      if (it is MavenRepositoryArtifactInfo) {
        result.add(it.groupId);
      }
    }
    return result;
  }

  fun getArtifactIds(groupId: String): Set<String>{
    ProgressIndicatorProvider.checkCanceled()
    val result = mutableSetOf<String>()
    fulltextSearch("$groupId:", SearchParameters(true, true)) {
      if (it is MavenRepositoryArtifactInfo) {
        if (StringUtil.equals(groupId, it.groupId)) {
          result.add(it.artifactId)
        }
      }
    }
    return result;
  }

  fun getVersions(groupId: String, artifactId: String): Set<String>{
    ProgressIndicatorProvider.checkCanceled()
    val result = mutableSetOf<String>()
    fulltextSearch("$groupId:$artifactId", SearchParameters(true, true)) {
      if (it is MavenRepositoryArtifactInfo) {
        if (StringUtil.equals(groupId, it.groupId) && StringUtil.equals(artifactId, it.artifactId)) {
          for (item in it.items) {
            if (item.version != null) result.add(item.version!!)
          }
        }
      }
    }
    return result;
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
    @JvmStatic
    fun getInstance(project: Project): DependencySearchService {
      return project.getService(DependencySearchService::class.java)
    }
  }

  @TestOnly
  fun setProviders(local: List<DependencySearchProvider>, remote: List<DependencySearchProvider>) {
    remoteProviders.clear()
    localProviders.clear()

    remoteProviders.addAll(remote)
    localProviders.addAll(local)
  }

  @TestOnly
  fun clearCache() {
    cache.clear()
  }
}