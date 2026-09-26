// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.ssl

import com.intellij.maven.testFramework.utils.MavenCertificateFixture
import com.intellij.maven.testFramework.utils.MavenHttpsRepositoryServerFixture
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.security.cert.X509Certificate
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSslServerTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  private lateinit var httpsServerFixture: MavenHttpsRepositoryServerFixture
  private lateinit var certificateFixture: MavenCertificateFixture

  @BeforeEach
  fun setUp() {
    certificateFixture = MavenCertificateFixture()
    certificateFixture.setUp()
    val (cert, pKey) = certificateFixture.createServerCertificate("localhost")
    httpsServerFixture = MavenHttpsRepositoryServerFixture(
      cert, "localhost", pKey
    ).also { it.setUp() }
  }

  @AfterEach
  fun tearDown() {
    runAll({
             certificateFixture.tearDown()
           }, {
             httpsServerFixture.tearDown()
           })
  }

  @Test
  fun testShouldRequestIntellijTrustServiceWhenLoadingUntrustedDomain() = runBlocking {
    var accepted = false
    maven.project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        accepted = chain[0] == httpsServerFixture.myServerCertificate
        return true
      }
    }, asDisposable())
    doImportMavenServerProject()
    assertTrue(accepted, "Certificate should be accepted")
  }

  @Test
  fun testShouldNotRequestIntellijTrustServiceWhenTrustStoreIsPassed() = runBlocking {
    val trustStoreLocation = maven.dir.resolve("truststore/mytrustore")
    trustStoreLocation.createParentDirectories()
    certificateFixture.saveCertificate(httpsServerFixture.myServerCertificate,
                                       trustStoreLocation,
                                       "password", "localhost", "pkcs12", true)
    var requsted = false
    maven.projectsManager.importingSettings.vmOptionsForImporter = "-Djavax.net.ssl.trustStore=${trustStoreLocation} -Djavax.net.ssl.trustStorePassword=password"
    maven.project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        requsted = true
        return false
      }
    }, asDisposable())

    doImportMavenServerProject()
    assertFalse(requsted, "Certificate should be accepted using inbuild truststore manager")
  }


  @Test
  fun testShouldAllowClientAuthentication() = runBlocking {
    val trustStoreLocationDir = maven.dir.resolve("truststore")
    trustStoreLocationDir.createDirectories()
    val truststore = trustStoreLocationDir.resolve("mytrustore")
    certificateFixture.saveCertificate(httpsServerFixture.myServerCertificate,
                                       truststore,
                                       "password", "localhost", "pkcs12", true)
    var requsted = false
    maven.projectsManager.importingSettings.vmOptionsForImporter = "-Djavax.net.ssl.trustStore=${truststore} -Djavax.net.ssl.trustStorePassword=password"
    maven.project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        requsted = true
        return false
      }
    }, asDisposable())

    doImportMavenServerProject()
    assertFalse(requsted, "Certificate should be accepted using inbuild truststore manager")
  }




  private suspend fun doImportMavenServerProject() {
    val helper = MavenCustomRepositoryHelper(maven.dir, "local1", "remote")
    val remoteRepoPath = helper.getTestData("remote")
    val localRepoPath = helper.getTestData("local1")
    httpsServerFixture.startRepositoryFor(remoteRepoPath.toString())
    maven.repositoryPath = localRepoPath
    val settingsXml = maven.createProjectSubFile(
      "settings.xml",
      """
         <settings>
            <localRepository>$localRepoPath</localRepository>
         </settings>
         """.trimIndent())
    maven.projectsManager.generalSettings.setUserSettingsFile(settingsXml.canonicalPath)
    maven.removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
    maven.importProjectAsync("""
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
    assertTrue(maven.repositoryPath.resolve("org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
  }
}