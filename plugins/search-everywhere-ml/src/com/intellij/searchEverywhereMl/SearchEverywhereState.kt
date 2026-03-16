package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereState {
  /**
   * Project associated with the current state. It may be null.
   */
  val project: Project?

  /**
   * Index of the current state
   */
  val index: Int

  /**
   * Currently opened tab
   */
  val tab: SearchEverywhereTab

  /**
   * Search Scope descriptor if a single-contributor tab supports it. Otherwise null.
   *
   * @see [com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.getSelectedSearchScope]
   */
  val searchScope: ScopeDescriptor?

  /**
   * Returns true if the search has global scope.
   *
   * @see [com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction.isEverywhere]
   */
  val isSearchEverywhere: Boolean

  /**
   * Time in ms when the search session has started
   */
  val sessionStartTime: Long

  /**
   * Time in ms when the state has started
   */
  val stateStartTime: Long

  /**
   * [com.intellij.ide.actions.searcheverywhere.SearchRestartReason] that caused the transition to this state
   */
  val searchRestartReason: SearchRestartReason

  /**
   * Current experiment group that the user is in
   */
  val experimentGroup: Int

  /**
   * Total number of keys typed at the time of this state.
   * This is not equivalent to the length of the search query.
   */
  val keysTyped: Int

  /**
   * Total number of backspaces typed at the time of this state
   */
  val backspacesTyped: Int

  /**
   * Current search query
   */
  val query: String
}
