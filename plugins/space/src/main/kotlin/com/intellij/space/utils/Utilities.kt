package com.intellij.space.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.xml.util.XmlStringUtil
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.Lifetimed
import platform.common.ProductName

val application: Application
  get() = ApplicationManager.getApplication()

interface LifetimedDisposable : Disposable, Lifetimed

class LifetimedDisposableImpl : Lifetimed, LifetimedDisposable {

  private val lifetimeSource = LifetimeSource()

  override val lifetime: Lifetime get() = lifetimeSource

  override fun dispose() {
    lifetimeSource.terminate()
  }
}

inline fun <reified T : Any> ComponentManager.getComponent(): T =
  getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

inline fun <reified T : Any> Project.getService(): T = service<T>().checkService(this)

inline fun <reified T : Any> T?.checkService(container: Any): T =
  this ?: throw Error("Service ${T::class.java} not found in container $container")

inline fun <T : Any, C : ComponentManager> C.computeSafe(crossinline compute: C.() -> T?): T? =
  application.runReadAction(Computable {
    if (isDisposed) null else compute()
  })

fun notify(text: String, actions: List<AnAction> = listOf()) {
  val notification = Notification(
    ProductName,
    ProductName,
    XmlStringUtil.wrapInHtml(text),
    NotificationType.INFORMATION
  )
  if (actions.isNotEmpty()) {
    notification.addActions(actions)
  }
  notification.notify(null)
}
