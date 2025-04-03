// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpProxyServerFixture
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.junit.Test
import java.net.URI
import kotlin.io.path.isRegularFile

class ProxyImportingTest : MavenMultiVersionImportingTestCase() {
  lateinit var myRepositoryFixture: MavenHttpRepositoryServerFixture;
  lateinit var myProxyFixture: MavenHttpProxyServerFixture;
  lateinit var myHelper: MavenCustomRepositoryHelper;

  override fun setUp() {
    super.setUp()
    myRepositoryFixture = MavenHttpRepositoryServerFixture()
    myRepositoryFixture.setUp()
    val port = URI(myRepositoryFixture.url()).port
    val proxyPort = NetUtils.findAvailableSocketPort()
    myProxyFixture = MavenHttpProxyServerFixture(mapOf("mavendummycentral.jetbrains.com" to port), proxyPort, AppExecutorUtil.getAppExecutorService())
    myProxyFixture.setUp()

    myHelper = MavenCustomRepositoryHelper(dir, "local1", "remote")
    val remoteRepoPath = myHelper.getTestData("remote")
    myRepositoryFixture.startRepositoryFor(remoteRepoPath.toString())
    val localRepoPath = myHelper.getTestData("local1")
    repositoryPath = localRepoPath

    runBlocking {
      val settingsXml = createProjectSubFile(
        "settings.xml",
        """
          <settings>
            <localRepository>$localRepoPath</localRepository>
            <mirrors>
              <mirror>
                <id>central-mirror</id>
                <name>my-mirror</name>
                <url>http://mavendummycentral.jetbrains.com/</url>
                <mirrorOf>central</mirrorOf>
              </mirror>
            </mirrors>
            <proxies>
              <proxy>
                <id>my</id>
                <active>true</active>
                <protocol>http</protocol>
                <host>127.0.0.1</host>
                <port>${myProxyFixture.port}</port>
              </proxy>
            </proxies>
          </settings>
          """.trimIndent())
      mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    }
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable {
        myProxyFixture.tearDown()
      },
      ThrowableRunnable {
        myRepositoryFixture.tearDown()
      },
      ThrowableRunnable { super.tearDown() }).run()
  }

  @Test
  fun testDownloadDependencyUsingProxy() = runBlocking {
    removeFromLocalRepository("org/mytest/myartifact/")
    importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>org.mytest</groupId>
                           <artifactId>myartifact</artifactId>
                           <version>1.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent()
    )
    assertTrue("File should be downloaded", myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
  }
}