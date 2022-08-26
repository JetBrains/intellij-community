package com.intellij.ide.starter.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
val simpleScope = CoroutineScope(Job() + Dispatchers.IO)