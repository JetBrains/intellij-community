package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.AlreadyDisposedException
import kotlin.reflect.KProperty

class DisposableValueHolderDelegate<T: Disposable>(value: T): Disposable {
  private var instance: T? = value

  init {
    Disposer.register(value, this)
  }

  operator fun getValue(self: Any?, property: KProperty<*>): T {
    return instance ?: throw AlreadyDisposedException("Can't access value after disposal")
  }

  override fun dispose() {
    instance = null
  }
}

fun <T: Disposable> disposableHolder(value: T): DisposableValueHolderDelegate<T> {
  return DisposableValueHolderDelegate(value)
}
