package com.intellij.ide.starter.bus

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 *
 * This class holds all state flows and handles event posting.
 * You can use [GlobalBus] that is just plain instance of this class or create your own implementation.
 */
open class FlowBus {

  private val flows = mutableMapOf<Class<*>, MutableStateFlow<*>>()

  /**
   * Gets a MutableStateFlow for events of the given type. Creates new if one doesn't exist.
   * @return MutableStateFlow for events that are instances of clazz
   */
  internal fun <T : Any> forEvent(clazz: Class<T>): MutableStateFlow<T?> {
    return flows.getOrPut(clazz) { MutableStateFlow<T?>(null) } as MutableStateFlow<T?>
  }

  /**
   * Gets a Flow for events of the given type.
   *
   * **This flow never completes.**
   *
   * The returned Flow is _hot_ as it is based on a [SharedFlow]. This means a call to [collect] never completes normally, calling [toList] will suspend forever, etc.
   *
   * You are entirely responsible to cancel this flow. To cancel this flow, the scope in which the coroutine is running needs to be cancelled.
   * @see [SharedFlow]
   */
  fun <T : Any> getFlow(clazz: Class<T>): Flow<T> {
    return forEvent(clazz).asStateFlow().filterNotNull()
  }

  /**
   * Posts new event to StateFlow of the [event] type.
   * @param retain If the [event] should be retained in the flow for future subscribers. This is true by default.
   */
  @JvmOverloads
  fun <T : Any> post(event: T, retain: Boolean = true) {
    val flow = forEvent(event.javaClass)
    flow.tryEmit(event).also {
      if (!it)
        throw IllegalStateException("StateFlow cannot take element, this should never happen")
    }
    if (!retain) {
      // without starting a coroutine here, the event is dropped immediately
      // and not delivered to subscribers
      CoroutineScope(Job() + Dispatchers.Unconfined).launch {
        dropEvent(event.javaClass)
      }
    }
  }

  /**
   * Returns last posted event that was instance of [clazz] or `null` if no event of the given type is retained.
   * @return Retained event that is instance of [clazz]
   */
  fun <T : Any> getLastEvent(clazz: Class<T>): T? {
    return flows.getOrElse(clazz) { null }?.value as T?
  }

  /**
   *  Removes retained event of type [clazz]
   */
  fun <T> dropEvent(clazz: Class<T>) {
    if (!flows.contains(clazz)) return
    val channel = flows[clazz] as MutableStateFlow<T?>
    channel.tryEmit(null)
  }

  /**
   *  Removes all retained events
   */
  fun dropAll() {
    flows.values.forEach {
      (it as MutableStateFlow<Any?>).tryEmit(null)
    }
  }
}