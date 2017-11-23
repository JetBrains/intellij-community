package com.intellij.stats.personalization.impl

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * @author Vitaliy.Bibaev
 */
@State(name = "ApplicationUserFactors", storages = arrayOf(Storage("completion.factors.user.xml")))
class ApplicationUserFactorStorage : ApplicationComponent, UserFactorStorageBase()