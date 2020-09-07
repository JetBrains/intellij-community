package com.intellij.space.vcs.review

import com.intellij.openapi.util.registry.Registry

internal fun isSpaceCodeReviewEnabled(): Boolean = Registry.`is`("space.code.review.enabled", false)