// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.GitCommitChange
import circlet.client.api.GitCommitChangeType
import circlet.client.api.GitFile
import circlet.client.api.isDirectory
import circlet.code.api.ChangeInReview
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.vcsUtil.VcsUtil

internal class SpaceReviewChange(changeInReview: ChangeInReview, val spaceRepoInfo: SpaceRepoInfo?, val unreachable: Boolean) {
  val changeFilePathInfo = getChangeFilePathInfo(changeInReview, spaceRepoInfo)

  val fileStatus: FileStatus = changeInReview.getFileStatus()

  val filePath: FilePath = changeFilePathInfo.actualFilePath()

  val gitCommitChange: GitCommitChange = changeInReview.change

  val repository: String = changeInReview.repository

  val spaceFilePath: String = (gitCommitChange.new ?: gitCommitChange.old)!!.path
}

internal data class ChangeFilePathInfo(val old: FilePath?, val new: FilePath?)

private fun getChangeFilePathInfo(changeInReview: ChangeInReview, spaceRepoInfo: SpaceRepoInfo?): ChangeFilePathInfo =
  when (changeInReview.change.changeType) {
    GitCommitChangeType.ADDED ->
      ChangeFilePathInfo(null, changeInReview.change.new.getFilePath(spaceRepoInfo))
    GitCommitChangeType.MODIFIED ->
      ChangeFilePathInfo(changeInReview.change.old.getFilePath(spaceRepoInfo), changeInReview.change.new.getFilePath(spaceRepoInfo))
    GitCommitChangeType.DELETED ->
      ChangeFilePathInfo(changeInReview.change.old.getFilePath(spaceRepoInfo), null)
  }

private fun GitFile?.getFilePath(spaceRepoInfo: SpaceRepoInfo?): FilePath? {
  this ?: return null
  val path = path.trimStart('/', '\\')
  if (spaceRepoInfo == null) return LocalFilePath(path, isDirectory())

  return VcsUtil.getFilePath(spaceRepoInfo.repository.root, path)
}

private fun ChangeFilePathInfo.actualFilePath(): FilePath = new ?: old!!

private fun ChangeInReview.getFileStatus(): FileStatus = when (change.changeType) {
  GitCommitChangeType.ADDED -> FileStatus.ADDED
  GitCommitChangeType.DELETED -> FileStatus.DELETED
  GitCommitChangeType.MODIFIED -> FileStatus.MODIFIED
}