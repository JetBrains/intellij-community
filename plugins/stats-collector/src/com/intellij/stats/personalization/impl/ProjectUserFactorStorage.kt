package com.intellij.stats.personalization.impl

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * @author Vitaliy.Bibaev
 */
@State(name = "ProjectUserFactors", storages = arrayOf(Storage("completion.factors.user.xml")))
class ProjectUserFactorStorage : ProjectComponent, UserFactorStorageBase()