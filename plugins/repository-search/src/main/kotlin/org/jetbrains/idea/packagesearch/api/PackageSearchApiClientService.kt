package org.jetbrains.idea.packagesearch.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager.getSystemDir
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.createParentDirectories
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.document.database.DataStore
import kotlinx.document.database.mvstore.MVDataStore
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.PluginEnvironment
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.idea.reposearch.logTrace
import org.jetbrains.packagesearch.api.PackageSearchApiClientObject
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient.Companion.defaultHttpClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchDefaultEndpoints
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints
import org.jetbrains.packagesearch.api.v3.http.searchPackages
import org.jetbrains.packagesearch.api.v3.search.jvmMavenPackages
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
class PackageSearchApiClientService(val coroutineScope: CoroutineScope) : Disposable, DependencySearchProvider {

  private val httpClient = defaultHttpClient(engine = Java, protobuf = false) {
    install(UserAgent) {
      agent = ApplicationInfo.getInstance().fullVersion
    }
    install(DefaultRequest) {
      headers {
        append("Api-Version", PackageSearchApiClientObject.version)
        append("JB-IDE-Version", PluginEnvironment.ideVersion)
      }
    }
    install(Logging) {
      level = LogLevel.HEADERS
      logger = object : Logger {
        override fun log(message: String) {
          logTrace(message)

        }
      }
    }
    install(ContentEncoding) {
      deflate()
      gzip()
    }
    install(HttpRequestRetry) {
      maxRetries = 5
      retryOnException(retryOnTimeout = true)
    }
    install(HttpTimeout) {
      requestTimeoutMillis = 10.seconds.inWholeMilliseconds
      socketTimeoutMillis = 3.seconds.inWholeMilliseconds
      connectTimeoutMillis = 3.seconds.inWholeMilliseconds
    }
    install(UserAgent) {
      agent = intelliJ()
    }
  }

  private val cacheFilePath
    get() = getSystemDir() / "caches" / "packagesearch" / "${PackageSearchApiClientObject.version}.db"


  private val mvDataStore = MVDataStore.open(
    getCacheFile(),
    DataStore.CommitStrategy.Periodic(5.seconds))

  private val overrideEndpoint = Registry.`is`("packagesearch.config.url.override")
  private val endpoint = if(overrideEndpoint) {
    PackageSearchDefaultEndpoints(
      host = "maven-deps-search.labs.jb.gg",
    )
  }  else PackageSearchEndpoints.PROD
  val client = PackageSearchApiClient(
    dataStore = mvDataStore,
    endpoints = endpoint,
    httpClient = httpClient
  )

  private fun getCacheFile(): Path {
    if (!cacheFilePath.exists()) cacheFilePath.createParentDirectories().absolutePathString()
    return cacheFilePath
  }

  override fun dispose() {
    httpClient.close()
    coroutineScope.launch(Dispatchers.IO) {
      mvDataStore.close()
    }
  }

  @Deprecated("Use directly the client instead")
  @ScheduledForRemoval
  suspend fun searchByString(searchString: String): List<ApiMavenPackage> =
    if (searchString.isEmpty()) {
      emptyList()
    }
    else {
      client.searchPackages {
        searchQuery = searchString
        packagesType {
          jvmMavenPackages()
        }
      }.filterIsInstance<ApiMavenPackage>()
    }


  @Deprecated("Use directly the client instead (PackageSearcgApiClientService)")
  @ScheduledForRemoval
  override suspend fun fulltextSearch(searchString: String): List<RepositoryArtifactData> =
    if (searchString.isEmpty()) {
      emptyList<RepositoryArtifactData>()
    }
    else {
      client.searchPackages {
        searchQuery = searchString
        packagesType {
          jvmMavenPackages()
        }
      }
        .filterIsInstance<ApiMavenPackage>()
        .map { it.repositoryArtifactData() }
    }


  @Deprecated("Use directly the client instead")
  @ScheduledForRemoval
  override suspend fun suggestPrefix(groupId: String, artifactId: String) =
    if (groupId.isEmpty() && artifactId.isEmpty()) {
      emptyList()
    }
    else {
      fulltextSearch("$groupId:$artifactId")
    }

  override fun isLocal() = false

  override val cacheKey: String = "PackageSearchProvider"
}

private fun ApiMavenPackage.repositoryArtifactData(): MavenRepositoryArtifactInfo {
  val versions = versions.all.map {
    MavenDependencyCompletionItem(
      /* groupId = */ groupId,
      /* artifactId = */ artifactId,
      /* version = */ it.normalizedVersion.versionName,
      /* type = */ MavenDependencyCompletionItem.Type.REMOTE
    )
  }
  return MavenRepositoryArtifactInfo(
    /* groupId = */ groupId,
    /* artifactId = */ artifactId,
    /* version = */ versions.toTypedArray()
  )
}

private fun UserAgentConfig.intelliJ(): String {
  val app = ApplicationManager.getApplication()
  if (app != null && !app.isDisposed) {
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    val version = ApplicationInfo.getInstance().build.asStringWithoutProductCode()
    return "$productName/$version"
  }
  else {
    return "IntelliJ"
  }
}

