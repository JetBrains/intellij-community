// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.utils

import circlet.client.api.*
import circlet.client.api.apps.ES_App
import com.intellij.space.settings.SpaceSettings
import runtime.routing.Location

object SpaceUrls {
  // project
  fun project(key: ProjectKey): String = p(key).toUrl()
  fun projects(): String = Navigator.p.toUrl()

  fun repo(key: ProjectKey, repo: String): String = p(key).repo(repo).toUrl()

  fun commits(key: ProjectKey, repo: String, hash: String) = p(key).commits(repo, "", hash).toUrl()

  fun revision(key: ProjectKey, repo: String, revision: String): String = p(key).revision(repo, revision).toUrl()

  fun reviews(key: ProjectKey): String = p(key).reviews().toUrl()

  fun review(key: ProjectKey, reviewNumber: Int): String = p(key).review(reviewNumber).toUrl()

  fun reviewFiles(key: ProjectKey, reviewNumber: Int, revisions: List<String>): String =
    p(key).reviewFiles(reviewNumber, revisions = revisions).toUrl()

  fun checklists(key: ProjectKey): String = p(key).checklists().toUrl()

  fun issues(key: ProjectKey): String = p(key).issues().toUrl()

  fun fileAnnotate(key: ProjectKey, repo: String, hash: String, relativePath: String, selectedLine: Int? = null): String =
    p(key).fileAnnotate(repo, hash, relativePath, selectedLine).toUrl()

  // member
  fun member(profile: TD_MemberProfile): String = member(profile.username)
  fun member(username: String): String = m().member(username).toUrl()

  fun git(username: String): String = m().member(username).git.toUrl()

  // chats
  fun p2pChat(profile: TD_MemberProfile): String = p2pChat(profile.username)
  fun p2pChat(username: String): String = im().p2pChat(username).toUrl()

  fun message(oldContactKey: String, message: ChannelItemRecord): String = im().message(oldContactKey, message).toUrl()

  private fun p(projectKey: ProjectKey): ProjectLocation = Navigator.p.project(projectKey)
  private fun m(): MembersLocation = Navigator.m
  private fun im(): ChatsLocation = Navigator.im

  // manage
  fun app(app: ES_App): String = Navigator.manage.apps.app(app).href

  private fun Location.toUrl(): String = absoluteHref(server())

  private fun server(): String = SpaceSettings.getInstance().serverSettings.server
}