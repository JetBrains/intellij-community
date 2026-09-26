package com.intellij.ide.starter.driver.driver.remoteDev

/**
 * Customizes a remote-development join link for the environment where the frontend will run.
 *
 * The only implementation today rewrites `0.0.0.0` to the backend's docker network alias; it is a function in
 * `intellij.ide.starter.dockerized`, bound here as a SAM, since this module can't depend on dockerized support.
 */
fun interface RemoteDevJoinLinkCustomizer {
  fun customizeJoinLink(joinLink: String): String
}
