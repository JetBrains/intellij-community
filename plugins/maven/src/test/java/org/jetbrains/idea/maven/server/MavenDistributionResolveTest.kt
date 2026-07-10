// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.utils.MavenHttpProxyServerFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Path
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDistributionResolveTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private val myEvents: MutableList<Pair<BuildEvent, Throwable>> = ArrayList()
  private lateinit var mySyncViewManager: SyncViewManager
  private lateinit var mavenHomeDir: Path

  @BeforeEach
  fun setUp() {
    mySyncViewManager = object : SyncViewManager(maven.project) {
      override fun onEvent(
        buildId: Any,
        event: BuildEvent,
      ) {
        myEvents.add(event to Exception())
      }
    }
    maven.project.replaceService(SyncViewManager::class.java, mySyncViewManager, maven.disposable)
    mavenHomeDir = createTempDirectory()
  }

  @AfterEach
  fun tearDown() {
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
    @OptIn(ExperimentalPathApi::class)
    mavenHomeDir.deleteRecursively()
  }

  @Throws(IOException::class)
  @Test
  fun testShouldUseEmbedMavenIfWrapperIsBad() = runBlocking {
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    createWrapperProperties("distributionUrl=http://example.org/repo/maven.bin.zip")
    MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
    maven.importProjectAsync()
    val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toCanonicalPath(), connector.mavenDistribution.mavenHome.toCanonicalPath())
    assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.message == "Cannot install wrapped maven, set Bundled Maven" }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldNotRestartMavenConnectorIfWrapperIsBadButNotChanged() = runBlocking {
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    createWrapperProperties("distributionUrl=http://example.org/repo/maven.bin.zip")
    MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
    maven.importProjectAsync()
    val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toCanonicalPath(), connector.mavenDistribution.mavenHome.toCanonicalPath())
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>2</version>")
    maven.importProjectAsync()
    assertSame(connector, MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path))
  }

  @Throws(IOException::class)
  @Test
  fun testShouldShowWarningIfWrapperDownloadedViaUnsecureProtocol() = runBlocking {
    runWithServer { url ->
      maven.createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=$url")
      MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
      maven.importProjectAsync()
      val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
      assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
      assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.message == "HTTP used to download maven distribution" }
    }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldNotUseWrapperIfSettingsNotSetToUseIt() = runBlocking {
    runWithServer { url ->
      maven.createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=$url")
      MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = BundledMaven3
      maven.importProjectAsync()
      val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
      assertFalse(connector.mavenDistribution.mavenHome.absolutePathString().contains(".wrapper"))
      assertNotContains<BuildEvent> { it.message == "Running maven wrapper" }
    }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldUseEmbeddedMavenForUnexistingHome() = runBlocking {
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = MavenInSpecificPath(
      "path/to/unexisted/maven/home")
    maven.importProjectAsync()
    val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toCanonicalPath(), connector.mavenDistribution.mavenHome.toCanonicalPath())
    //assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.description!= null && it.description!!.contains("is not correct maven home, reverting to embedded") }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldUseSystemPropertyOverridesWhenDownloadingWrapper() = runBlocking {
    runWithServer { url ->

      val envVariables = mapOf(
        "MVNW_REPOURL" to url.substringBeforeLast("/"),
        "MVNW_USERNAME" to "user_123",
        "MVNW_PASSWORD" to "pass_abc"
      )
      val environment = object : Environment {
        override fun property(name: String): String? {
          throw NotImplementedError()
        }

        override fun variable(name: String): String? {
          return envVariables[name]
        }
      }

      ApplicationManager.getApplication().replaceService(Environment::class.java, environment, maven.disposable)

      maven.createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=https://something.com/org/apache/maven/apache-maven/3.6.3/apache-maven-3.6.3-bin.zip")
      MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
      maven.importProjectAsync()
      val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
      assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
      assertNotContains<BuildEvent> { it.message.contains("something.com") }
      assertContainsOnce<BuildEvent> { it.message == "Downloading Maven wrapper with Basic authentication\n" }
    }
  }


  @Test
  fun testShouldUseProxyWhenDownloadingWrapper() = runBlocking {
    maven.assumeMaven3()
    runWithServer { url ->
      withEnvironment("MAVEN_USER_HOME" to mavenHomeDir.absolutePathString()) {
        val uri = URI.create(url)
        val domain = "domain.available.only.via.proxy.intellij.com"
        val proxy = MavenHttpProxyServerFixture(mapOf(domain to uri.port),
                                                AppExecutorUtil.getAppExecutorService())
        proxy.setUp()
        val wrapperUrl = URI("http://$domain${uri.path}")
        val proxySettings = ProxySettings.getInstance()
        val defaultConfig = proxySettings.getProxyConfiguration()
        try {
          proxySettings.setProxyConfiguration(ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "localhost", proxy.port))
          maven.createProjectPom("<groupId>test</groupId>" +
                           "<artifactId>project</artifactId>" +
                           "<version>1</version>")
          createWrapperProperties("distributionUrl=$wrapperUrl")
          MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
          maven.importProjectAsync()
          val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
          assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
          assertContainsElements(proxy.requestedFiles, "/apache-maven-3.6.3-bin.zip")
        }
        finally {
          proxySettings.setProxyConfiguration(defaultConfig)
          proxy.tearDown()
        }
      }
    }
  }

  private suspend fun withEnvironment(vararg pair: Pair<String, String>, invoke: suspend () -> Unit) {
    val map = pair.toMap()
    val environment = object : Environment {
      override fun property(name: String): String? {
        return null
      }

      override fun variable(name: String): String? {
        return map[name]
      }
    }
    val disposable = Disposer.newDisposable(maven.disposable)
    ApplicationManager.getApplication().replaceService(Environment::class.java, environment, disposable)
    invoke()
    Disposer.dispose(disposable)


  }


  private inline fun runWithServer(test: (String) -> Unit) {
    val server = HttpServer.create()
    try {
      server.bind(InetSocketAddress("127.0.0.1", 0), 1)
      server.start()
      server.createContext("/") { ex: HttpExchange ->
        ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
        ex.responseHeaders.add("Content-Type", "application/zip")
        ZipOutputStream(ex.responseBody).use { zos ->
          ZipUtil.addDirToZipRecursively(zos, null, MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.parent.toFile(),
                                         "", null, null)
        }
        ex.close()
      }
      val url = "http://127.0.0.1:" + server.address.port + "/apache-maven-3.6.3-bin.zip"
      test(url)
    }
    finally {
      server.stop(0)
    }

  }

  private inline fun <reified T : BuildEvent> assertContains(predicate: (T) -> Boolean) {
    val filteredList = myEvents.filter { val event = it.first; event is T && predicate(event) }
    assertFalse(filteredList.isEmpty(), "Expected event not found")
  }


  private inline fun <reified T : BuildEvent> assertContainsOnce(predicate: (T) -> Boolean) {
    val filteredList = myEvents.filter { val event = it.first; event is T && predicate(event) }
    assertFalse(filteredList.isEmpty(), "Expected event not found, found ${myEvents.size} events: ${myEvents.map { it.first }}")
    Assertions.assertEquals(1,
                            filteredList.size,
                            "Event was received several times: See stacktraces \n ${getStacktraces(filteredList.map { it.second })}")
  }

  private inline fun <reified T : BuildEvent> assertNotContains(predicate: (T) -> Boolean) {
    val filtered = myEvents.filter { val event = it.first; event is T && predicate(event) }
    assertEmpty("Event found but not expected: stacktraces: ${getStacktraces(filtered.map { it.second })}", filtered)
  }

  private fun getStacktraces(exceptions: List<Throwable>): String {
    return exceptions.asSequence().map(ExceptionUtil::getThrowableText).joinToString("\n-------------------\n")
  }


  @Throws(IOException::class)
  private fun createWrapperProperties(content: String) {
    maven.createProjectSubFile(".mvn/wrapper/maven-wrapper.properties", content)
  }
}
