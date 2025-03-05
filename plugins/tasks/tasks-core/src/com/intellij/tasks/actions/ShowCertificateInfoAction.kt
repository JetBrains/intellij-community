// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.CertificateProvider
import com.intellij.util.net.ssl.CertificateWarningDialogProvider
import com.intellij.util.net.ssl.ConfirmingTrustManager
import java.security.KeyStoreException
import java.security.cert.X509Certificate

@Suppress("HardCodedStringLiteral")
class ShowCertificateInfoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    try { 
      val manager = CertificateManager.getInstance()
      manager.cacertsPath
      val certificates = manager.customTrustManager.getCertificates()
      if (certificates.isEmpty()) {
        Messages.showInfoMessage(String.format("Key store '%s' is empty", manager.cacertsPath), "No Certificates Available")
      }
      else {
        val certificate = certificates[0]
        val certHierarchy = mutableListOf<X509Certificate>(certificate)
        getRootCertificate(certificate, manager.customTrustManager, certHierarchy)
        val dialog = CertificateWarningDialogProvider.getInstance()?.createCertificateWarningDialog(certHierarchy, manager.customTrustManager, "test.com", "RSA", CertificateProvider())
        if (dialog == null) {
          LOG.error("Dialog cannot be shown now, dialog provider returns null")
          return
        }
        LOG.debug("Accepted: " + dialog.showAndGet())
      }
    }
    catch (logged: Exception) {
      LOG.error(logged)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
  @Throws(KeyStoreException::class)
  private fun getRootCertificate(endEntityCertificate: X509Certificate, manager: ConfirmingTrustManager.MutableTrustManager, certificates: MutableList<X509Certificate>): X509Certificate? {
    val issuerCertificate: X509Certificate? = findIssuerCertificate(endEntityCertificate, manager)
    if (issuerCertificate != null) {
      certificates.add(issuerCertificate)
      if (isRoot(issuerCertificate)) {
        return issuerCertificate
      }
      else {
        return getRootCertificate(issuerCertificate, manager, certificates)
      }
    }
    return null
  }

  private fun isRoot(certificate: X509Certificate): Boolean {
    try {
      certificate.verify(certificate.publicKey)
      return certificate.keyUsage != null && certificate.keyUsage[5]
    }
    catch (_: Exception) {
      return false
    }
  }

  @Throws(KeyStoreException::class)
  private fun findIssuerCertificate(certificate: X509Certificate, manager: ConfirmingTrustManager.MutableTrustManager): X509Certificate? {
    val aliases = manager.aliases
    val resolveCertificate = { alias: String -> manager.getCertificate(alias) }
    aliases.forEach { alias ->
      val cert = resolveCertificate(alias)
      if (cert is X509Certificate) {
        val x509Cert = cert
        if (x509Cert.getSubjectX500Principal() == certificate.getIssuerX500Principal()) {
          return x509Cert
        }
      }
    }
    return null
  }

  companion object {
    private val LOG = Logger.getInstance(ShowCertificateInfoAction::class.java)
  }
}

