// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.observable.AbstractInProgressService
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class MavenInProgressService(scope: CoroutineScope) : AbstractInProgressService(scope)