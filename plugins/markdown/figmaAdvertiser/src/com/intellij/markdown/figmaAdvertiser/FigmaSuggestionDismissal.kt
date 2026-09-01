package com.intellij.markdown.figmaAdvertiser

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * The key the dismissal is recorded under, on the project's own `PropertiesComponent`.
 *
 * The platform stores nothing per project: `buildSuggestionIfNeeded` reads its `suggestionDismissKey`
 * from the application, so an answer given there would silence the offer everywhere. A user who meets
 * the banner in a project where the design link is somebody else's still gets the offer in the
 * project where the link is theirs.
 */
@ApiStatus.Internal
const val FIGMA_SUGGESTION_DISMISSED_KEY: String = "markdown.figma.connect.suggestion.dismissed"

/** Whether the banner is dismissed for [project]. */
@ApiStatus.Internal
fun isFigmaSuggestionDismissed(project: Project): Boolean =
  PropertiesComponent.getInstance(project).isTrueValue(FIGMA_SUGGESTION_DISMISSED_KEY)

/** Records that the banner is dismissed for [project]. */
@ApiStatus.Internal
fun dismissFigmaSuggestion(project: Project) {
  PropertiesComponent.getInstance(project).setValue(FIGMA_SUGGESTION_DISMISSED_KEY, true)
}
