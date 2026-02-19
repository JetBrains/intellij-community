// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.ssl

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenCertificateFixture
import com.intellij.maven.testFramework.utils.MavenHttpsRepositoryServerFixture
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.junit.Test
import java.security.cert.X509Certificate
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile

class MavenSslServerTest : MavenMultiVersionImportingTestCase() {

  private lateinit var httpsServerFixture: MavenHttpsRepositoryServerFixture
  private lateinit var certificateFixture: MavenCertificateFixture

  public override fun setUp() {
    super.setUp()
    certificateFixture = MavenCertificateFixture()
    certificateFixture.setUp()
    val (cert, pKey) = certificateFixture.createServerCertificate("localhost")
    httpsServerFixture = MavenHttpsRepositoryServerFixture(
      cert, "localhost", pKey
    ).also { it.setUp() }
  }

  override fun tearDown() {
    runAll({
             certificateFixture.tearDown()
           }, {
             httpsServerFixture.tearDown()
           }, {
             super.tearDown()
           })
  }

  @Test
  fun testShouldRequestIntellijTrustServiceWhenLoadingUntrustedDomain() = runBlocking {
    var accepted = false
    project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        accepted = chain[0] == httpsServerFixture.myServerCertificate
        return true
      }
    }, asDisposable())
    doImportMavenServerProject()
    assertTrue("Certificate should be accepted", accepted)
  }

  @Test
  fun testShouldNotRequestIntellijTrustServiceWhenTrustStoreIsPassed() = runBlocking {
    val trustStoreLocation = dir.resolve("truststore/mytrustore")
    trustStoreLocation.createParentDirectories()
    certificateFixture.saveCertificate(httpsServerFixture.myServerCertificate,
                                       trustStoreLocation,
                                       "password", "localhost", "pkcs12", true)
    var requsted = false
    projectsManager.importingSettings.vmOptionsForImporter = "-Djavax.net.ssl.trustStore=${trustStoreLocation} -Djavax.net.ssl.trustStorePassword=password"
    project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        requsted = true
        return false
      }
    }, asDisposable())

    doImportMavenServerProject()
    assertFalse("Certificate should be accepted using inbuild truststore manager", requsted)
  }


  @Test
  fun testShouldAllowClientAuthentication() = runBlocking {
    val trustStoreLocationDir = dir.resolve("truststore")
    trustStoreLocationDir.createDirectories()
    val truststore = trustStoreLocationDir.resolve("mytrustore")
    certificateFixture.saveCertificate(httpsServerFixture.myServerCertificate,
                                       truststore,
                                       "password", "localhost", "pkcs12", true)
    var requsted = false
    projectsManager.importingSettings.vmOptionsForImporter = "-Djavax.net.ssl.trustStore=${truststore} -Djavax.net.ssl.trustStorePassword=password"
    project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        requsted = true
        return false
      }
    }, asDisposable())

    doImportMavenServerProject()
    assertFalse("Certificate should be accepted using inbuild truststore manager", requsted)
  }




  private suspend fun doImportMavenServerProject() {
    val helper = MavenCustomRepositoryHelper(dir, "local1", "remote")
    val remoteRepoPath = helper.getTestData("remote")
    val localRepoPath = helper.getTestData("local1")
    httpsServerFixture.startRepositoryFor(remoteRepoPath.toString())
    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
         <settings>
            <localRepository>$localRepoPath</localRepository>
         </settings>
         """.trimIndent())
    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
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
                         <repositories>
                             <repository>
                               <id>my-https-repository</id>
                               <name>my-https-repository</name>
                               <url>${httpsServerFixture.url()}</url>
                             </repository>
                         </repositories>
                         """.trimIndent())
    assertTrue(repositoryPath.resolve("org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
  }
}