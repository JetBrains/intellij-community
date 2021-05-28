// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.stats

import circlet.code.api.CodeReviewParticipantRole
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.vcs.review.list.ReviewListQuickFilter

internal class SpaceStatsCounterCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    @JvmField
    val GROUP = EventLogGroup("space", 1)


    // --------------------
    // General
    // --------------------

    @JvmField
    val LOGIN_STATUS = EventFields.Enum<LoginState>("login_status")

    @JvmField
    val OPEN_MAIN_TOOLBAR_POPUP = GROUP.registerEvent("open_main_toolbar_popup", LOGIN_STATUS)

    @JvmField
    val OPEN_GIT_SETTINGS_IN_SPACE = GROUP.registerEvent("open_git_settings_in_space")

    // --------------------
    // Advertisement
    // --------------------

    @JvmField
    val EXPLORE_PLACE = EventFields.Enum<ExplorePlace>("adv_explore_place")

    @JvmField
    val OVERVIEW_PLACE = EventFields.Enum<OverviewPlace>("adv_overview_place")

    @JvmField
    val EXPLORE_SPACE = GROUP.registerEvent("adv_explore_space", EXPLORE_PLACE)

    @JvmField
    val WATCH_OVERVIEW = GROUP.registerEvent("adv_watch_overview", OVERVIEW_PLACE)

    @JvmField
    val ADV_LOG_IN_LINK = GROUP.registerEvent("adv_log_in_link")

    @JvmField
    val SIGN_UP_LINK = GROUP.registerEvent("adv_sign_up_link")

    // --------------------
    // Clone
    // --------------------

    @JvmField
    val OPEN_SPACE_CLONE_TAB = GROUP.registerEvent("open_space_clone_tab", LOGIN_STATUS)

    @JvmField
    val CLONE_REPO = GROUP.registerEvent("clone_repo")

    // --------------------
    // Share project
    // --------------------

    @JvmField
    val OPEN_SHARE_PROJECT = GROUP.registerEvent("open_share_project", LOGIN_STATUS)

    @JvmField
    val START_CREATING_NEW_PROJECT = GROUP.registerEvent("start_creating_new_project")

    @JvmField
    val CREATE_NEW_PROJECT = GROUP.registerEvent("create_new_project")

    @JvmField
    val SHARE_PROJECT = GROUP.registerEvent("share_project")

    // --------------------
    // Chat
    // --------------------

    @JvmField
    val SEND_MESSAGE_PLACE = EventFields.Enum<SendMessagePlace>("new_message_place")

    @JvmField
    val SEND_MESSAGE_IS_PENDING = EventFields.Boolean("new_message_is_pending")

    @JvmField
    val EDIT_MESSAGE_IS_EMPTY = EventFields.Boolean("edit_message_is_empty")

    @JvmField
    val SEND_MESSAGE = GROUP.registerEvent("chat_send_message", SEND_MESSAGE_PLACE, SEND_MESSAGE_IS_PENDING)

    @JvmField
    val DISCARD_SEND_MESSAGE = GROUP.registerEvent("chat_discard_send_message", SEND_MESSAGE_PLACE)

    @JvmField
    val START_EDIT_MESSAGE = GROUP.registerEvent("chat_start_edit_message")

    @JvmField
    val DISCARD_EDIT_MESSAGE = GROUP.registerEvent("chat_discard_edit_message")

    @JvmField
    val SEND_EDIT_MESSAGE = GROUP.registerEvent("chat_send_edit_message", EDIT_MESSAGE_IS_EMPTY)

    @JvmField
    val OPEN_THREAD = GROUP.registerEvent("chat_open_thread")

    @JvmField
    val DELETE_MESSAGE = GROUP.registerEvent("chat_delete_message")

    @JvmField
    val EXPAND_DISCUSSION = GROUP.registerEvent("chat_expand_discussion")

    @JvmField
    val COLLAPSE_DISCUSSION = GROUP.registerEvent("chat_collapse_discussion")

    @JvmField
    val RESOLVE_DISCUSSION = GROUP.registerEvent("chat_resolve_discussion")

    @JvmField
    val REOPEN_DISCUSSION = GROUP.registerEvent("chat_reopen_discussion")

    // --------------------
    // Login
    // --------------------

    @JvmField
    val LOGIN_PLACE = EventFields.Enum<LoginPlace>("login_place")

    @JvmField
    val LOGOUT_PLACE = EventFields.Enum<LogoutPlace>("logout_place")

    @JvmField
    val LOG_IN = GROUP.registerEvent("button_log_in", LOGIN_PLACE)

    @JvmField
    val LOG_OUT = GROUP.registerEvent("button_log_out", LOGOUT_PLACE)

    @JvmField
    val CANCEL_LOGIN = GROUP.registerEvent("cancel_login", LOGIN_PLACE)

    // --------------------
    // ReviewsList
    // --------------------

    @JvmField
    val QUICK_FILTER = EventFields.Enum<ReviewListQuickFilter>("quick_filter", transform = { it.name })

    @JvmField
    val OPEN_REVIEW_TYPE = EventFields.Enum<OpenReviewActionType>("open_review_type")

    @JvmField
    val FILTER_TEXT_EMPTY = EventFields.Boolean("filter_text_empty")

    @JvmField
    val REFRESH_REVIEWS_PLACE = EventFields.Enum<RefreshReviewsPlace>("refresh_reviews_place")

    @JvmField
    val REVIEWS_LOG_IN_LINK = GROUP.registerEvent("reviews_list_log_in_link")

    @JvmField
    val CHANGE_QUICK_FILTER = GROUP.registerEvent("reviews_list_change_quick_filter", QUICK_FILTER)

    @JvmField
    val CHANGE_TEXT_FILTER = GROUP.registerEvent("reviews_list_change_text_filter", FILTER_TEXT_EMPTY)

    @JvmField
    val OPEN_REVIEW = GROUP.registerEvent("reviews_list_open_review", OPEN_REVIEW_TYPE)

    @JvmField
    val REFRESH_REVIEWS_ACTION = GROUP.registerEvent("reviews_list_refresh_action", REFRESH_REVIEWS_PLACE)

    // --------------------
    // ReviewDetails
    // --------------------

    @JvmField
    val PARTICIPANT_ROLE = EventFields.Enum<CodeReviewParticipantRole>("participant_role", transform = { it.name })

    @JvmField
    val PARTICIPANT_EDIT_TYPE = EventFields.Enum<ParticipantEditType>("participant_edit_type")

    @JvmField
    val COMMITS_SELECTION_TYPE = EventFields.Enum<CommitsSelectionType>("commits_selection_type")

    @JvmField
    val DETAILS_TAB_TYPE = EventFields.Enum<DetailsTabType>("details_tab_type")

    @JvmField
    val REVIEW_DIFF_PLACE = EventFields.Enum<ReviewDiffPlace>("review_diff_place")

    @JvmField
    val EDIT_PARTICIPANT = GROUP.registerEvent("review_details_edit_participant", PARTICIPANT_ROLE, PARTICIPANT_EDIT_TYPE)

    @JvmField
    val EDIT_PARTICIPANT_ICON = GROUP.registerEvent("review_details_add_participant_icon", PARTICIPANT_ROLE)

    @JvmField
    val SHOW_TIMELINE = GROUP.registerEvent("review_details_show_timeline")

    @JvmField
    val OPEN_PROJECT_IN_SPACE = GROUP.registerEvent("review_details_open_project_in_space")

    @JvmField
    val OPEN_REVIEW_IN_SPACE = GROUP.registerEvent("review_details_open_review_in_space")

    @JvmField
    val BACK_TO_LIST = GROUP.registerEvent("review_details_back_to_list", DETAILS_TAB_TYPE)

    @JvmField
    val CHANGE_COMMITS_SELECTION = GROUP.registerEvent("review_details_change_commits_selection", COMMITS_SELECTION_TYPE)

    @JvmField
    val SELECT_DETAILS_TAB = GROUP.registerEvent("review_details_select_details_tab", DETAILS_TAB_TYPE)

    @JvmField
    val OPEN_REVIEW_DIFF = GROUP.registerEvent("review_details_open_review_diff", REVIEW_DIFF_PLACE)

    @JvmField
    val CHECKOUT_BRANCH = GROUP.registerEvent("review_details_checkout_branch")

    @JvmField
    val UPDATE_BRANCH = GROUP.registerEvent("review_details_update_branch")

    @JvmField
    val ACCEPT_CHANGES = GROUP.registerEvent("review_details_accept_changes")

    @JvmField
    val WAIT_FOR_RESPONSE = GROUP.registerEvent("review_details_wait_for_response")

    @JvmField
    val RESUME_REVIEW = GROUP.registerEvent("review_details_resume_review")

    // --------------------
    // ReviewDiff
    // --------------------

    @JvmField
    val LOADER_TYPE = EventFields.Enum<LoaderType>("loader_type")

    @JvmField
    val LEAVE_COMMENT = GROUP.registerEvent("review_diff_leave_comment")

    @JvmField
    val CLOSE_LEAVE_COMMENT = GROUP.registerEvent("review_diff_close_leave_comment")

    @JvmField
    val DIFF_LOADED = GROUP.registerEvent("review_diff_loaded", LOADER_TYPE)
  }


  // helper classes should be outside the companion to get rid of Companion class in imports and usages

  enum class LoginState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED;

    companion object {
      fun convert(state: SpaceLoginState) = when (state) {
        is SpaceLoginState.Connected -> CONNECTED
        is SpaceLoginState.Connecting -> CONNECTING
        is SpaceLoginState.Disconnected -> DISCONNECTED
      }
    }
  }

  enum class ExplorePlace {
    MAIN_TOOLBAR,
    SETTINGS,
    SHARE,
    CLONE
  }

  enum class OverviewPlace {
    MAIN_TOOLBAR,
    SETTINGS,
    CLONE
  }

  enum class SendMessagePlace {
    MAIN_CHAT,
    THREAD,
    DIFF,
    NEW_THREAD,
    FIRST_DISCUSSION_ANSWER,
    NEW_DISCUSSION
  }

  enum class LoginPlace {
    MAIN_TOOLBAR,
    SETTINGS,
    SHARE,
    CLONE
  }

  enum class LogoutPlace {
    ACTION,
    SETTINGS,
    MAIN_TOOLBAR,
    CLONE,
    AUTH_FAIL,
  }

  enum class OpenReviewActionType {
    ENTER,
    DOUBLE_CLICK,
    ARROW
  }

  enum class RefreshReviewsPlace {
    EMPTY_LIST,
    CONTEXT_MENU
  }

  enum class ParticipantEditType {
    ADD,
    REMOVE
  }

  enum class CommitsSelectionType {
    SINGLE,
    ALL,
    SUBSET_CONNECTED,
    SUBSET_SPLIT;

    companion object {
      fun calculateSelectionType(newSelection: List<Int>, commitsCount: Int): CommitsSelectionType =
        when (newSelection.size) {
          commitsCount -> ALL
          1 -> SINGLE
          else -> calculateSubsetType(newSelection)
        }

      private fun calculateSubsetType(newSelection: List<Int>): CommitsSelectionType {
        for (i in 1 until newSelection.size) {
          if (newSelection[i - 1] + 1 != newSelection[i]) {
            return SUBSET_SPLIT
          }
        }
        return SUBSET_CONNECTED
      }
    }
  }

  enum class DetailsTabType {
    DETAILS,
    COMMITS
  }

  enum class ReviewDiffPlace {
    EDITOR,
    DIALOG
  }

  enum class LoaderType {
    GIT,
    SPACE
  }
}