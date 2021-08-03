package com.intellij.space.jps

import org.jetbrains.jps.incremental.dependencies.DependencyAuthenticationDataProvider

class SpaceDependencyAuthenticationDataProvider : DependencyAuthenticationDataProvider() {
  override fun provideAuthenticationData(url: String): AuthenticationData? {
    if (PROVIDED_HOSTS.none { url.contains(it) }) {
      return null
    }

    val userName = System.getProperty("jps.auth.spaceUsername")
    val password = System.getProperty("jps.auth.spacePassword")
    if (userName != null && password != null) {
      return AuthenticationData(userName, password)
    }
    return null
  }

  companion object {
    private val PROVIDED_HOSTS = listOf(
      "jetbrains.team",
      "jetbrains.space"
    )
  }
}