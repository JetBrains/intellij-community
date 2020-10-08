// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.util.registry.Registry

internal fun isSpaceCodeReviewEnabled(): Boolean = Registry.`is`("space.code.review.enabled", false)