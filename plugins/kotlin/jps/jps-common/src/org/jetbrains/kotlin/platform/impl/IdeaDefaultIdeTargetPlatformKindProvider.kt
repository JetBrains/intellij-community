// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.platform.impl

import org.jetbrains.kotlin.platform.DefaultIdeTargetPlatformKindProvider

class IdeaDefaultIdeTargetPlatformKindProvider private constructor() : DefaultIdeTargetPlatformKindProvider {
    override val defaultPlatform = JvmIdePlatformKind.defaultPlatform
}