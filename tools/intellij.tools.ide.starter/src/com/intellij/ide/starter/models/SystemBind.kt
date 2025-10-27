package com.intellij.ide.starter.models


data class SystemBind(val source: String, val target: String, val mode: BindMode) {
  companion object {
    fun string(bind: SystemBind): String {
      return "${bind.source}:${bind.target}:${bind.mode}"
    }

    fun bindFromString(str: String): SystemBind? {
      try {
        return str.split(":").let { SystemBind(it[0], it[1], BindMode.valueOf(it[2])) }
      } catch (ignored: Exception) {
        return null
      }
    }

    fun string(bind: Set<SystemBind>): String {
      return bind.joinToString(";") { string(it) }
    }

    fun setFromString(str: String): Set<SystemBind> {
      return str.split(";").mapNotNull { bindFromString(it) }.toSet()
    }
  }
}

enum class BindMode {
  READ_ONLY, READ_WRITE
}