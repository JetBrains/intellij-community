// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*

class MavenCertificateFixture() : IdeaTestFixture {
  private val rootCaKeyPair: KeyPair = generateKeyPair()
  private val rootCaCert: X509Certificate = generateRootCaCertificate()

  override fun setUp() {
  }

  private fun generateKeyPair(): KeyPair {
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048)
    return keyGen.generateKeyPair()
  }

  private fun generateRootCaCertificate(): X509Certificate {
    val issuer = X500Name("CN=Test Root CA")
    val serial = BigInteger.valueOf(System.currentTimeMillis())
    val now = Date()
    val expiry = Date(now.time + 365L * 24 * 60 * 60 * 1000)

    val certBuilder = JcaX509v3CertificateBuilder(
      issuer, serial, now, expiry, issuer, rootCaKeyPair.public
    )
    certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))

    val signer = JcaContentSignerBuilder("SHA256withRSA").build(rootCaKeyPair.private)
    return JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))
  }

  fun createServerCertificate(hostname: String): Pair<X509Certificate, PrivateKey> {
    val keyPair = generateKeyPair()
    val subject = X500Name("CN=$hostname")
    val serial = BigInteger.valueOf(System.currentTimeMillis())
    val now = Date()
    val expiry = Date(now.time + 365L * 24 * 60 * 60 * 1000)

    val certBuilder = JcaX509v3CertificateBuilder(
      X500Name(rootCaCert.subjectX500Principal.name),
      serial, now, expiry, subject, keyPair.public
    )

    val signer = JcaContentSignerBuilder("SHA256withRSA").build(rootCaKeyPair.private)
    return JcaX509CertificateConverter().getCertificate(certBuilder.build(signer)) to keyPair.private
  }

  fun createClientCertificate(cn: String): Pair<X509Certificate, PrivateKey> {
    val keyPair = generateKeyPair()
    val subject = X500Name("CN=$cn")
    val serial = BigInteger.valueOf(System.currentTimeMillis())
    val now = Date()
    val expiry = Date(now.time + 365L * 24 * 60 * 60 * 1000)

    val certBuilder = JcaX509v3CertificateBuilder(
      X500Name(rootCaCert.subjectX500Principal.name),
      serial, now, expiry, subject, keyPair.public
    )

    val signer = JcaContentSignerBuilder("SHA256withRSA").build(rootCaKeyPair.private)
    return JcaX509CertificateConverter().getCertificate(certBuilder.build(signer)) to keyPair.private
  }

  fun checkClientCertificate(cert: X509Certificate) {
    try {
      cert.verify(rootCaCert.publicKey)
      val issuerDN = cert.issuerX500Principal
      val rootDN = rootCaCert.subjectX500Principal
      if (issuerDN != rootDN) {
        throw SecurityException("Certificate was not issued by the root CA")
      }
    }
    catch (e: Exception) {
      throw SecurityException("Invalid client certificate", e)
    }
  }

  fun saveCertificates(cert: X509Certificate, store: Path, password: String, type: String = "pkcs12") {
    val keyStore = KeyStore.getInstance(type)
    keyStore.load(null, null)
    keyStore.setCertificateEntry("cert-${UUID.randomUUID()}", cert)
    saveKeyStore(keyStore, store, password)
  }

  private fun saveKeyStore(keyStore: KeyStore, storePath: Path, password: String) {
    storePath.toFile().outputStream().use { os ->
      keyStore.store(os, password.toCharArray())
    }
  }

  override fun tearDown() {
  }
}