package com.intellij.ide.starter.config

import com.intellij.ide.starter.config.ConfigurationStorage.Companion.instance
import com.intellij.ide.starter.di.di
import org.kodein.di.DirectDI
import org.kodein.di.direct
import org.kodein.di.instance
import java.util.concurrent.ConcurrentHashMap

/**
 * Key-Value storage for parameters to tweak starter behavior
 * This class is not supposed to be instantiated by any code except [DirectDI], so [di] argument here is more like intention marker, not a
 * real argument. So, please, use [instance] method to get the object.
 */
class ConfigurationStorage(di: DirectDI, val defaults: Map<String, String> = emptyMap()) {
  private val _map: ConcurrentHashMap<String, String> = ConcurrentHashMap<String, String>()

  companion object {
    fun instance(): ConfigurationStorage = di.direct.instance<ConfigurationStorage>()
  }

  fun put(key: String, value: String?) {
    _map[key] = value ?: ""
  }

  fun put(key: String, value: Boolean) {
    _map[key] = value.toString()
  }

  fun get(key: String): String? = _map[key]

  fun <T> get(key: String, converter: (String?) -> T): T {
    val value = get(key)
    return converter(value)
  }

  fun getBoolean(key: String): Boolean = get(key).toBoolean()

  fun getOrDefault(key: String, default: String): String = getOrNull(key) ?: default

  fun getOrNull(key: String): String? {
    val value = get(key)
    if (value.isNullOrBlank()) {
      return null
    }
    return value
  }

  /**
   * Reset to default values, that will be performed after each test
   */
  fun resetToDefault() {
    _map.clear()
    _map.putAll(defaults)
  }

  init {
    resetToDefault()
  }

  fun getAll() = _map.toMap()
}