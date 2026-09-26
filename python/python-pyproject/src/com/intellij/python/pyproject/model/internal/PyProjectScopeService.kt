package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class PyProjectScopeService(internal val scope: CoroutineScope)