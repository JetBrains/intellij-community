// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpProxyServerFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.replaceService
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.net.NetUtils
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

class MavenDistributionResolveTest : MavenMultiVersionImportingTestCase() {
  private val myEvents: MutableList<Pair<BuildEvent, Throwable>> = ArrayList()
  private lateinit var mySyncViewManager: SyncViewManager

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    mySyncViewManager = object : SyncViewManager(project) {
      override fun onEvent(
        buildId: Any,
        event: BuildEvent,
      ) {
        myEvents.add(event to Exception())
      }
    }
    project.replaceService(SyncViewManager::class.java, mySyncViewManager, testRootDisposable)
  }

  @Throws(IOException::class)
  @Test
  fun testShouldUseEmbedMavenIfWrapperIsBad() = runBlocking {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    createWrapperProperties("distributionUrl=http://example.org/repo/maven.bin.zip")
    MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
    importProjectAsync()
    val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toCanonicalPath(), connector.mavenDistribution.mavenHome.toCanonicalPath())
    assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.message == "Cannot install wrapped maven, set Bundled Maven" }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldNotRestartMavenConnectorIfWrapperIsBadButNotChanged() = runBlocking {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    createWrapperProperties("distributionUrl=http://example.org/repo/maven.bin.zip")
    MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
    importProjectAsync()
    val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toCanonicalPath(), connector.mavenDistribution.mavenHome.toCanonicalPath())
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>2</version>")
    importProjectAsync()
    assertSame(connector, MavenServerManager.getInstance().getConnector(project, projectRoot.path))
  }

  @Throws(IOException::class)
  @Test
  fun testShouldShowWarningIfWrapperDownloadedViaUnsecureProtocol() = runBlocking {
    runWithServer { url ->
      createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=$url")
      MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
      importProjectAsync()
      val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
      assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
      assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.message == "HTTP used to download maven distribution" }
    }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldNotUseWrapperIfSettingsNotSetToUseIt() = runBlocking {
    runWithServer { url ->
      createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=$url")
      MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = BundledMaven3
      importProjectAsync()
      val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
      assertFalse(connector.mavenDistribution.mavenHome.absolutePathString().contains(".wrapper"))
      assertNotContains<BuildEvent> { it.message == "Running maven wrapper" }
    }
  }

  @Throws(IOException::class)
  @Test
  fun testShouldUseEmbeddedMavenForUnexistingHome() = runBlocking {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = MavenInSpecificPath(
      "path/to/unexisted/maven/home")
    importProjectAsync()
    val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
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

      ApplicationManager.getApplication().replaceService(Environment::class.java, environment, testRootDisposable)

      createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=https://something.com/org/apache/maven/apache-maven/3.6.3/apache-maven-3.6.3-bin.zip")
      MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
      importProjectAsync()
      val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
      assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
      assertNotContains<BuildEvent> { it.message.contains("something.com") }
      assertContainsOnce<BuildEvent> { it.message == "Downloading Maven wrapper with Basic authentication\n" }
    }
  }


  @Test
  fun testShouldUseProxyWhenDownloadingWrapper() = runBlocking {
    assumeMaven3()
    runWithServer { url ->
      val mavenHomeDir = createTempDirectory()
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
          createProjectPom("<groupId>test</groupId>" +
                           "<artifactId>project</artifactId>" +
                           "<version>1</version>")
          createWrapperProperties("distributionUrl=$wrapperUrl")
          MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings().mavenHomeType = MavenWrapper
          importProjectAsync()
          val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
          assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
          assertContainsElements(proxy.requestedFiles, "/apache-maven-3.6.3-bin.zip")
        }
        finally {
          proxySettings.setProxyConfiguration(defaultConfig)
          proxy.tearDown()
          @OptIn(ExperimentalPathApi::class)
          mavenHomeDir.deleteRecursively()
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
    val disposable = Disposer.newDisposable(testRootDisposable)
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
    assertFalse("Expected event not found", filteredList.isEmpty())
  }


  private inline fun <reified T : BuildEvent> assertContainsOnce(predicate: (T) -> Boolean) {
    val filteredList = myEvents.filter { val event = it.first; event is T && predicate(event) }
    assertFalse("Expected event not found, found ${myEvents.size} events: ${myEvents.map { it.first }}", filteredList.isEmpty())
    assertEquals("Event was received several times: See stacktraces \n ${getStacktraces(filteredList.map { it.second })}", 1, filteredList.size)
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
    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties", content)
  }
}
