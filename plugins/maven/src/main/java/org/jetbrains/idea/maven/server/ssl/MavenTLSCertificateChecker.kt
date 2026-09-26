// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.ssl

import com.intellij.openapi.project.Project
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.ConfirmingTrustManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.utils.MavenLog
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

@ApiStatus.Internal
interface MavenTLSCertificateChecker {
  fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean
}

@ApiStatus.Internal
class IdeCertificateManagerMavenTLSCertificateChecker(val project: Project) : MavenTLSCertificateChecker {
  override fun checkCertificates(chain: Array<X509Certificate>, authType: String): Boolean {
    try {
      val confirmationParameters = ConfirmingTrustManager.CertificateConfirmationParameters
        .askConfirmation(false,
                         MavenProjectBundle.message("maven.server.ask.trust"),
                         null)
      CertificateManager
        .getInstance()
        .trustManager
        .checkServerTrusted(chain, authType, confirmationParameters)
      return true
    }
    catch (e: CertificateException) {
      MavenLog.LOG.warn(e)
      return false
    }
  }
}