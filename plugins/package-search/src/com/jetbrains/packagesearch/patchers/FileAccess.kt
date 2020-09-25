package com.jetbrains.packagesearch.patchers

interface FileAccess {
    fun loadText(): String
    fun saveText(newText: String)
}
