package org.jetbrains.idea.packagesearch.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.PluginEnvironment
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.idea.reposearch.logTrace
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient.Companion.defaultHttpClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints
import org.jetbrains.packagesearch.api.v3.http.searchPackages
import org.jetbrains.packagesearch.api.v3.search.jvmMavenPackages
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
class PackageSearchApiClientService : Disposable, DependencySearchProvider {

  private val httpClient = defaultHttpClient(engine = Java, protobuf = false) {
    install(UserAgent) {
      agent = ApplicationInfo.getInstance().fullVersion
    }
    install(DefaultRequest) {
      headers {
        append("Api-Version", "3.1.1")
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
    install(HttpTimeout) {
      requestTimeoutMillis = 10.seconds.inWholeMilliseconds
    }
  }

  val client = PackageSearchApiClient(
    endpoints = PackageSearchEndpoints.PROD,
    httpClient = httpClient
  )

  override fun dispose() {
    httpClient.close()
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
