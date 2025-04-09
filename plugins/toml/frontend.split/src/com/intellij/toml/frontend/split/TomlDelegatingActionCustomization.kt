package com.intellij.toml.frontend.split

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.rdclient.actions.ActionCallStrategy
import com.jetbrains.rdclient.actions.base.BackendDelegatingActionCustomization
import org.toml.lang.TomlLanguage

class TomlDelegatingActionCustomization : BackendDelegatingActionCustomization() {
    override fun getActionCallStrategy(dataContext: DataContext, frontendActionId: String): ActionCallStrategy? {
        if (dataContext.getData(CommonDataKeys.LANGUAGE) != TomlLanguage) return null

        return ActionCallStrategy.FrontendSpeculativeWithBackendCheck
    }
}