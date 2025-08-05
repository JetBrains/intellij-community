// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.ssl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.idea.maven.server.security.ssl.SslIDEConfirmingTrustStore
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*


class SslDelegateHandlerConfirmingTrustManager(project: Project)
  : SslDelegateHandlerStateMachine(project.service<MavenTLSCertificateChecker>())


open class SslDelegateHandlerStateMachine(val checker: MavenTLSCertificateChecker) {
  private var currentState: State
  lateinit var output: OutputStream

  init {
    currentState = Idle(this)
  }

  fun addLine(text: @NlsSafe String) {
    if (!this::output.isInitialized) return
    currentState = currentState.addLine(text.trim('\n', '\r', '\t', ' '))
  }
}

sealed class State {
  abstract fun addLine(text: @NlsSafe String): State
}

class Idle(val machine: SslDelegateHandlerStateMachine) : State() {
  override fun addLine(text: String): State {
    if (text == SslIDEConfirmingTrustStore.IDE_DELEGATE_TRUST_MANAGER) return WaitForMethod(machine)
    return this
  }
}

class WaitForMethod(val machine: SslDelegateHandlerStateMachine) : State() {
  override fun addLine(text: String): State {
    if (text == SslIDEConfirmingTrustStore.CHECK_SERVER_TRUSTED) return WaitForKey(machine)
    else return Idle(machine)
  }
}

class WaitForKey(val machine: SslDelegateHandlerStateMachine) : State() {
  override fun addLine(text: String): State {
    val key = text.toIntOrNull()
    if (key != null) return WaitForChainLen(machine, key)
    return Idle(machine)
  }
}

class WaitForChainLen(val machine: SslDelegateHandlerStateMachine, val key: Int) : State() {
  override fun addLine(text: String): State {
    val len = text.toIntOrNull()
    if (len != null) return WaitForAuthType(machine, key, len)
    return Idle(machine)
  }

}

class WaitForAuthType(val machine: SslDelegateHandlerStateMachine, val key: Int, val len: Int) : State() {
  override fun addLine(text: String): State {
    return ReadForCertificates(machine, key, len, len, text, ArrayList<X509Certificate>())
  }

}

class ReadForCertificates(val machine: SslDelegateHandlerStateMachine, val key: Int, val len: Int, val toRead: Int, val authType: String, val certificates: ArrayList<X509Certificate>) : State() {
  override fun addLine(text: String): State {
    val begin = text
    if (begin == SslIDEConfirmingTrustStore.BEGIN_CERTIFICATE) {
      return ReadNextCertificate(machine, key, len, toRead, authType, certificates, StringBuilder())
    }
    return Idle(machine)
  }


}

class ReadNextCertificate(
  val machine: SslDelegateHandlerStateMachine,
  val key: Int,
  val len: Int,
  val toRead: Int,
  val authType: String,
  val certificates: ArrayList<X509Certificate>,
  val stringBuilder: StringBuilder,
) : State() {
  override fun addLine(text: String): State {
    if (text == SslIDEConfirmingTrustStore.END_CERTIFICATE) {

      val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
      val certificateBuffer: ByteArray = Base64.getMimeDecoder().decode(stringBuilder.toString())
      val certificate: X509Certificate = certificateFactory.generateCertificate(certificateBuffer.inputStream()) as X509Certificate
      certificates.add(certificate)
      if (toRead == 1) {
        return WaitEndAndExecute(machine, key, authType, certificates)
      }
      else {
        return ReadForCertificates(machine, key, len, toRead - 1, authType, certificates)
      }

    }
    stringBuilder.append(text)
    return this
  }

}

class WaitEndAndExecute(val machine: SslDelegateHandlerStateMachine, val key: Int, val authType: String, val certificates: ArrayList<X509Certificate>) : State() {
  override fun addLine(text: String): State {
    if (text == SslIDEConfirmingTrustStore.CHECK_SERVER_TRUSTED) {
      val trusted = machine.checker.checkCertificates(certificates.toTypedArray(), authType)
      val os = ByteArrayOutputStream()
      PrintStream(os, true, "UTF-8").use { ps ->
        ps.println(SslIDEConfirmingTrustStore.IDE_DELEGATE_TRUST_MANAGER)
        ps.println(SslIDEConfirmingTrustStore.DELEGATE_RESPONSE)
        ps.println(key)
        if (trusted) {
          ps.println(SslIDEConfirmingTrustStore.DELEGATE_RESPONSE_OK)
        }
        else {
          ps.println(SslIDEConfirmingTrustStore.DELEGATE_RESPONSE_ERROR)
        }
        synchronized(machine) {
          machine.output.write(os.toByteArray())
          machine.output.flush()
        }
      }
    }
    return Idle(machine)
  }

}
