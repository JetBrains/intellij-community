package com.voltvoodoo.saplo4j.model

import java.io.Serializable

class Language(protected var code: String?) : Serializable {
    fun equals(other: Language): Boolean {
        return other.toString() == this.toString()
    }

    companion object {
        var ENGLISH: Language = com.voltvoodoo.saplo4j.model.Language("en")
        var SWEDISH: Language = com.voltvoodoo.saplo4j.model.Language("sv")

        private val serialVersionUID = -2442762969929206780L
    }
}
