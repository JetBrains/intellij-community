// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.MergeRequestBranch
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.SpaceReviewDataKeys
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchUiHandlerImpl
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitNewBranchOptions
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nullable

class SpaceReviewCheckoutBranchAction : DumbAwareAction(SpaceBundle.messagePointer("review.actions.checkout.branch")) {
  override fun update(e: AnActionEvent) {
    val detailsVm = getMergeRequestVm(e)

    if (detailsVm == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val repoInfo = detailsVm.repoInfo.value
    if (repoInfo == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val localBranch = detailsVm.mergeRequestBranchInfo.value.localBranch
    if (localBranch != null) {
      if (repoInfo.repository.currentBranchName == localBranch.name) {
        e.presentation.isEnabledAndVisible = false
        return
      }
    }
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val detailsVm = getMergeRequestVm(e) ?: return
    val repoInfo = detailsVm.repoInfo.value ?: return

    val gitRepository = repoInfo.repository
    val gitRepositories = listOf(gitRepository)

    val sourceBranchInfo = detailsVm.sourceBranchInfo.value ?: return

    SpaceStatsCounterCollector.CHECKOUT_BRANCH.log()
    val localBranch = detailsVm.mergeRequestBranchInfo.value.localBranch

    if (localBranch != null) {
      checkoutLocalBranch(project, detailsVm, localBranch, gitRepositories)
    }
    else {
      val remoteBranch = GitStandardRemoteBranch(repoInfo.remote, sourceBranchInfo.displayName)

      val options = GitBranchUtil.getNewBranchNameFromUser(
        project,
        gitRepositories,
        SpaceBundle.message("review.checkout.action.checkout.dialog.title", remoteBranch.name),
        sourceBranchInfo.displayName,
        false) ?: return

      if (options.checkout) {
        checkoutRemoteBranch(project, detailsVm, gitRepository, repoInfo, sourceBranchInfo, options, gitRepositories)
      }
      else {
        object : Task.Backgroundable(project,
                                     SpaceBundle.message("review.checkout.action.progress.title.creating.branch", detailsVm.reviewKey),
                                     true) {
          override fun run(indicator: ProgressIndicator) {
            val git = Git.getInstance()

            GitFetchSupport.fetchSupport(project)
              .fetch(gitRepository, repoInfo.remote)
              .throwExceptionIfFailed()

            GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
              .createBranch(options.name, mapOf(gitRepository to sourceBranchInfo.ref))

            gitRepository.update()
          }

        }.queue()
      }
    }
  }

  private fun checkoutRemoteBranch(project: @Nullable Project,
                                   detailsVm: MergeRequestDetailsVm,
                                   gitRepository: GitRepository,
                                   repoInfo: SpaceRepoInfo,
                                   sourceBranchInfo: MergeRequestBranch,
                                   options: @Nullable GitNewBranchOptions,
                                   gitRepositories: List<GitRepository>) {
    object : Task.Backgroundable(project,
                                 SpaceBundle.message("review.checkout.action.progress.title.checking.out.branch", detailsVm.reviewKey),
                                 true) {
      override fun run(indicator: ProgressIndicator) {
        val git = Git.getInstance()

        GitFetchSupport.fetchSupport(project)
          .fetch(gitRepository, repoInfo.remote)
          .throwExceptionIfFailed()

        val remoteBranch = GitStandardRemoteBranch(repoInfo.remote, sourceBranchInfo.displayName)

        GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
          .checkoutNewBranchStartingFrom(options.name, remoteBranch.name, gitRepositories)

        gitRepository.update()
      }
    }.queue()
  }

  private fun checkoutLocalBranch(project: @Nullable Project,
                                  detailsVm: MergeRequestDetailsVm,
                                  localBranch: GitLocalBranch,
                                  gitRepositories: List<GitRepository>) {
    object : Task.Backgroundable(project,
                                 SpaceBundle.message("review.checkout.action.progress.title.checking.out.branch", detailsVm.reviewKey),
                                 true) {
      override fun run(indicator: ProgressIndicator) {
        val git = Git.getInstance()
        GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
          .checkout(localBranch.name, false, gitRepositories)
      }
    }.queue()
  }
}

class SpaceReviewUpdateBranchAction : DumbAwareAction(SpaceBundle.messagePointer("review.actions.update.branch")) {
  override fun update(e: AnActionEvent) {
    val detailsVm = getMergeRequestVm(e)

    if (detailsVm == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val repoInfo = detailsVm.repoInfo.value
    if (repoInfo == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val mergeRequestBranchInfo = detailsVm.mergeRequestBranchInfo.value

    if (mergeRequestBranchInfo.localBranch != null && mergeRequestBranchInfo.isCurrentBranch) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    e.presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val detailsVm = getMergeRequestVm(e) ?: return

    val gitRepository = detailsVm.repoInfo.value?.repository ?: return
    val localBranch = detailsVm.mergeRequestBranchInfo.value.localBranch ?: return

    SpaceStatsCounterCollector.UPDATE_BRANCH.log()
    GitBranchUtil.updateBranches(project, listOf(gitRepository), listOf(localBranch.name))
  }
}

private fun getMergeRequestVm(e: AnActionEvent): MergeRequestDetailsVm? {
  val detailsVm = e.getData(SpaceReviewDataKeys.REVIEW_DETAILS_VM)

  return if (detailsVm is MergeRequestDetailsVm) detailsVm else null
}