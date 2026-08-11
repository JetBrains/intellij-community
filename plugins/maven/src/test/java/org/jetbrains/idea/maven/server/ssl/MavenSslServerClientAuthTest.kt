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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Files
import java.security.cert.X509Certificate
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSslServerClientAuthTest(mavenVersion: String, modelVersion: String) {

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
      cert, "localhost", pKey,
      object : MavenTLSCertificateChecker {
        override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
          return true
        }

      }, certificateFixture.rootCaCert
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
  fun testShouldNotUseDelegateWithClientAuthentication() = runBlocking {
    var requested = false
    maven.project.replaceService(MavenTLSCertificateChecker::class.java, object : MavenTLSCertificateChecker {
      override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
        requested = true
        return false
      }
    }, asDisposable())

    val keyStoreLocation = maven.dir.resolve("keystore/mykeystore")
    keyStoreLocation.createParentDirectories()

    val truststore = maven.dir.resolve("keystore/mytrusttore")
    truststore.createParentDirectories()
    certificateFixture.saveCertificate(httpsServerFixture.myServerCertificate,
                                       truststore,
                                       "password", "localhost", "pkcs12", true)

    val (cert, pkey) = certificateFixture.createClientCertificate("User")
    certificateFixture.savePrivateKey(cert, pkey, keyStoreLocation, "password", "pkcs12")

    maven.projectsManager.importingSettings.vmOptionsForImporter =
      "-Djavax.net.ssl.keyStore=${keyStoreLocation} " +
      "-Djavax.net.ssl.keyStorePassword=password " +
      "-Djavax.net.ssl.trustStore=$truststore " +
      "-Djavax.net.ssl.trustStorePassword=password"


    doImportMavenServerProject()
    assertFalse(requested, "Certificate trust should not be requested")
    if (!maven.repositoryPath.resolve("org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile()) {
      fail("Cannnot resolve dependency:" + Files.readString(maven.repositoryPath.resolve("org/mytest/myartifact/1.0/myartifact-1.0.pom.lastUpdated")))
    }
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

  }
}