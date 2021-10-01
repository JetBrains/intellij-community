package com.jetbrains.packagesearch.intellij.plugin.fus

object FUSGroupIds {
    const val GROUP_ID = "packagesearch"

    // FIELDS
    const val COORDINATES = "coordinates"
    const val SCOPE = "scope"
    const val BUILD_SYSTEM = "build_system"
    const val REPOSITORY_ID = "repository_id"
    const val REPOSITORY_URL = "repository_url"
    const val PACKAGE_IS_INSTALLED = "package_is_installed"
    const val PREFERENCES_GRADLE_SCOPES = "preferences_gradle_scopes"
    const val PREFERENCES_UPDATE_SCOPES_ON_USAGE = "preferences_update_scopes_on_usage"
    const val PREFERENCES_DEFAULT_GRADLE_SCOPE = "preferences_default_gradle_scope"
    const val PREFERENCES_DEFAULT_MAVEN_SCOPE = "preferences_default_maven_scope"
    const val TARGET_MODULE_NAME = "target_module_name"
    const val QUICK_FIX_TYPE = "quick_fix_type"
    const val FILE_TYPE = "file_type"
    const val DETAILS_LINK_LABEL = "details_link_label"
    const val DETAILS_LINK_URL = "details_link_url"
    const val DETAILS_VISIBLE = "details_visible"
    const val SEARCH_QUERY = "search_query"

    // ENUMS
    enum class QuickFixTypes { DependencyUpdate, UnresolvedReference }
    enum class DetailsLinkTypes { PackageUsages, GitHub, Documentation, License, ProjectWebsite, Readme }
    enum class ToggleTypes { PackageDetails, OnlyStable, OnlyKotlinMp }

    // EVENTS
    const val TOOL_WINDOW_FOCUSED = "tool_window_focused"
    const val PACKAGE_INSTALLED = "package_installed"
    const val PACKAGE_REMOVED = "package_removed"
    const val PACKAGE_UPDATED = "package_updated"
    const val REPOSITORY_ADDED = "repository_added"
    const val REPOSITORY_REMOVED = "repository_removed"
    const val PREFERENCES_CHANGED = "preferences_changed"
    const val PREFERENCES_RESET = "preferences_reset"
    const val PACKAGE_SELECTED = "package_selected"
    const val MODULE_SELECTED = "module_selected"
    const val RUN_QUICK_FIX = "run_quick_fix"
    const val DETAILS_LINK_CLICK = "details_link_click"
    const val TOGGLE = "toggle"
    const val SEARCH_REQUEST = "search_request"
    const val SEARCH_QUERY_CLEAR = "search_query_clear"
    const val UPGRADE_ALL = "upgrade_all_event"
}
