package com.intellij.stats.personalization.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.stats.personalization.UserFactorStorage
import com.intellij.util.xmlb.annotations.MapAnnotation
import java.util.HashMap

abstract class UserFactorStorageBase
    : UserFactorStorage, PersistentStateComponent<UserFactorStorageBase.CollectorState> {

    private var state = CollectorState()

    override fun getBoolean(factorId: String): Boolean? = state.booleanFactors[factorId]

    override fun getString(factorId: String): String? = state.stringFactors[factorId]

    override fun getDouble(factorId: String): Double? = state.doubleFactors[factorId]

    override fun setBoolean(factorId: String, value: Boolean) = state.booleanFactors.update(factorId, value)

    override fun setDouble(factorId: String, value: Double) = state.doubleFactors.update(factorId, value)

    override fun setString(factorId: String, value: String) = state.stringFactors.update(factorId, value)

    private fun <T> MutableMap<String, T>.update(factorId: String, value: T) {
        this[factorId] = value
    }

    override fun getState(): CollectorState = state

    override fun loadState(newState: CollectorState) {
        state = newState
    }

    class CollectorState {
        @MapAnnotation(surroundKeyWithTag = false, keyAttributeName = "name", sortBeforeSave = true)
        var booleanFactors: MutableMap<String, Boolean> = HashMap()

        @MapAnnotation(surroundKeyWithTag = false, keyAttributeName = "name", sortBeforeSave = true)
        var doubleFactors: MutableMap<String, Double> = HashMap()

        @MapAnnotation(surroundKeyWithTag = false, keyAttributeName = "name", sortBeforeSave = true)
        var stringFactors: MutableMap<String, String> = HashMap()
    }
}