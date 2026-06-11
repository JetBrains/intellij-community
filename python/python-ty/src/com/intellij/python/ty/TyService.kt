package com.intellij.python.ty

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class TyService(val cs: CoroutineScope)