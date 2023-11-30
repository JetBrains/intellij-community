// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui.changes

import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.openapi.project.Project

class KotlinConfiguratorChangesDiffRequestProcessor(
    project: Project,
    private val onNavigate: () -> Unit
) : CacheDiffRequestProcessor.Simple(project) {

    private var currentProvider: DiffRequestProducer? = null

    fun setProvider(producer: DiffRequestProducer?) {
        currentProvider = producer
        updateRequest()
    }

    override fun getCurrentRequestProvider(): DiffRequestProducer? {
        return currentProvider
    }

    override fun getData(dataId: String): Any? {
        if (OpenInEditorAction.AFTER_NAVIGATE_CALLBACK.`is`(dataId)) {
            return Runnable { onNavigate() }
        }
        return super.getData(dataId)
    }
}