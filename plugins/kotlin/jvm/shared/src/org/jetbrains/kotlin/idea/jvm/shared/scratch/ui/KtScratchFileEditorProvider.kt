// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.ui

import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorPolicy

abstract class KtScratchFileEditorProvider : AsyncFileEditorProvider {
    private val KTS_SCRATCH_EDITOR_PROVIDER: String = "KtsScratchFileEditorProvider"

    override fun getEditorTypeId(): String = KTS_SCRATCH_EDITOR_PROVIDER
    override fun acceptRequiresReadAction(): Boolean = false
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
