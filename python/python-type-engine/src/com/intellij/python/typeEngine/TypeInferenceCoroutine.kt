package com.intellij.python.typeEngine

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * Service to provide a coroutine scope for the project.
 */
@Service(Service.Level.PROJECT)
internal class TypeInferenceCoroutine(val coroutineScope: CoroutineScope)