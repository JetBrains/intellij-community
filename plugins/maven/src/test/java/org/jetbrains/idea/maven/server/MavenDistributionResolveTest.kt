// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.ZipUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.util.zip.ZipOutputStream
import kotlin.io.path.absolutePathString

class MavenDistributionResolveTest : MavenMultiVersionImportingTestCase() {
  private val myEvents: MutableList<Pair<BuildEvent, Throwable>> = ArrayList()
  private lateinit var mySyncViewManager: SyncViewManager;

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    mySyncViewManager = object : SyncViewManager(myProject) {
      override fun onEvent(buildId: Any,
                           event: BuildEvent) {
        myEvents.add(event to Exception())
      }
    }
    myProject.replaceService(SyncViewManager::class.java, mySyncViewManager, testRootDisposable)
    MavenProjectsManager.getInstance(myProject).setProgressListener(mySyncViewManager);
  }

  @Throws(IOException::class)
  @Test
  fun testShouldUseEmbedMavenIfWrapperIsBad() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    createWrapperProperties("distributionUrl=http://example.org/repo/maven.bin.zip")
    MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.mavenHome = MavenServerManager.WRAPPED_MAVEN
    importProject()
    val connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toFile().canonicalPath, connector.mavenDistribution.mavenHome.toFile().canonicalPath)
    assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.message == "Cannot install wrapped maven, set Bundled Maven" }
  }

  @Throws(IOException::class)
  @Test fun testShouldNotRestartMavenConnectorIfWrapperIsBadButNotChanged() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    createWrapperProperties("distributionUrl=http://example.org/repo/maven.bin.zip")
    MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.mavenHome = MavenServerManager.WRAPPED_MAVEN
    importProject()
    val connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toFile().canonicalPath, connector.mavenDistribution.mavenHome.toFile().canonicalPath)
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>2</version>")
    importProject()
    assertSame(connector, MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.path))
  }

  @Throws(IOException::class)
  @Test fun testShouldShowWarningIfWrapperDownloadedViaUnsecureProtocol() {
    runWithServer { url ->
      createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=$url")
      MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.mavenHome = MavenServerManager.WRAPPED_MAVEN
      importProject()
      val connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.path)
      assertTrue(connector.mavenDistribution.mavenHome.absolutePathString().contains("wrapper"))
      assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.message == "HTTP used to download maven distribution" }
    }
  }

  @Throws(IOException::class)
  @Test fun testShouldNotUseWrapperIfSettingsNotSetToUseIt() {
    runWithServer { url ->
      createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
      createWrapperProperties("distributionUrl=$url")
      MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.mavenHome = MavenServerManager.BUNDLED_MAVEN_3
      importProject()
      val connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.path)
      assertFalse(connector.mavenDistribution.mavenHome.toFile().absolutePath.contains(".wrapper"))
      assertNotContains<BuildEvent> {  it.message == "Running maven wrapper" }
    }
  }

  @Throws(IOException::class)
  @Test fun testShouldUseEmbeddedMavenForUnexistingHome() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>")
    MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.mavenHome = FileUtil.toSystemDependentName("path/to/unexisted/maven/home");
    importProject()
    val connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.path)
    assertEquals(
      MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toFile().canonicalPath, connector.mavenDistribution.mavenHome.toFile().canonicalPath)
    //assertContainsOnce<MessageEvent> { it.kind == MessageEvent.Kind.WARNING && it.description!= null && it.description!!.contains("is not correct maven home, reverting to embedded") }
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

  private inline fun <reified T: BuildEvent> assertContains(predicate: (T) -> Boolean) {
    val filteredList = myEvents.filter { val event = it.first; event is T && predicate(event) }
    assertFalse("Expected event not found", filteredList.isEmpty())
  }


  private inline fun <reified T: BuildEvent> assertContainsOnce(predicate: (T) -> Boolean) {
    val filteredList = myEvents.filter { val event = it.first; event is T && predicate(event) }
    assertFalse("Expected event not found, found ${myEvents.size} events: ${myEvents.map { it.first }}", filteredList.isEmpty())
    assertEquals("Event was received several times: See stacktraces \n ${getStacktraces(filteredList.map { it.second })}", 1, filteredList.size)
  }

  private inline fun <reified T: BuildEvent> assertNotContains(predicate: (T) -> Boolean) {
    val filtered = myEvents.filter {val event = it.first; event is T && predicate(event)}
    assertEmpty("Event found but not expected: stacktraces: ${getStacktraces(filtered.map { it.second })}",filtered)
  }

  private fun getStacktraces(exceptions: List<Throwable>): String {
    return exceptions.asSequence().map(ExceptionUtil::getThrowableText).joinToString("\n-------------------\n");
  }


  @Throws(IOException::class)
  private fun createWrapperProperties(content: String) {
    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties", content)
  }
}