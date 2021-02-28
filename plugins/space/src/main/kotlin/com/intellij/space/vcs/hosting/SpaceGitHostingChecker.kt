// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.hosting

import com.intellij.util.hosting.GitHostingUrlUtil
import git4idea.repo.GitRemote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CancellationException

internal class SpaceGitHostingChecker {
  companion object {
    private const val SPACE_HOSTING_RESPONSE_CONTENT = "JetBrains Space - VCS hosting"
  }

  private val httpClient: HttpClient = HttpClient()

  suspend fun check(remotes: Set<GitRemote>): Boolean {
    for (remote in remotes) {
      try {
        val url = remote.firstUrl ?: continue
        val hosting = GitHostingUrlUtil.getUriFromRemoteUrl(url) ?: continue
        val port = hosting.port.takeIf { it != -1 } ?: URLProtocol.HTTPS.defaultPort
        val urlToCheck = URLBuilder(protocol = URLProtocol.HTTPS, host = hosting.host, port = port).build()
        val isSpaceRepo = httpClient.get<String>(urlToCheck).contains(SPACE_HOSTING_RESPONSE_CONTENT)
        if (isSpaceRepo) {
          return true
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (th: Throwable) {
        // continue checking
      }
    }
    return false
  }
}