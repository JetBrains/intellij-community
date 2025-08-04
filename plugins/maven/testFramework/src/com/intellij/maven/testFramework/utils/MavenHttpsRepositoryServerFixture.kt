// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.seconds

private const val LOCALHOST = "127.0.0.1"
private const val SERVER_KS_PASSWORD = "password"

class MavenHttpsRepositoryServerFixture(
  val myServerCertificate: X509Certificate,
  val sslHostname: String,
  val myPrivateKey: PrivateKey,
) : AbstractMavenRepositoryServerFixture() {

  override fun url(): String {
    return "https://$LOCALHOST:${myServer.address.port}"
  }

  override fun startServer(): HttpsServer {
    val server = HttpsServer.create()
    server.bind(InetSocketAddress(LOCALHOST, 0), 0)

    val sslContext = createSSLContext()
    server.httpsConfigurator = object : HttpsConfigurator(sslContext) {
      override fun configure(params: HttpsParameters) {
        val engine = sslContext.createSSLEngine()
        params.needClientAuth = false
        params.cipherSuites = engine.enabledCipherSuites
        params.protocols = engine.enabledProtocols
        params.setSSLParameters(sslContext.defaultSSLParameters)
      }
    }

    server.executor = AppExecutorUtil.getAppExecutorService()
    server.start()
    return server
  }

  private fun createSSLContext(): SSLContext {
    // Initialize an in-memory KeyStore
    val keyStore = KeyStore.getInstance("JKS").apply {
      load(null, null)
      setKeyEntry(
        sslHostname,
        myPrivateKey,
        SERVER_KS_PASSWORD.toCharArray(),
        arrayOf(myServerCertificate)
      )
    }

    // KeyManagerFactory for server certificate
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(keyStore, SERVER_KS_PASSWORD.toCharArray())

    // TrustManagerFactory to trust our own cert
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keyStore)

    return SSLContext.getInstance("TLS").apply {
      init(kmf.keyManagers, tmf.trustManagers, SecureRandom())
    }
  }
}