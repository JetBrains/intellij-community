package com.intellij.ide.starter.bus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 *
 * This class holds all shared flows and handles event posting.
 * You can use [StarterBus] that is just plain instance of this class or create your own implementation.
 */
open class FlowBus {

  private val flows = mutableMapOf<Class<*>, MutableSharedFlow<*>>()

  /**
   * Gets a MutableSharedFlow for events of the given type. Creates new if one doesn't exist.
   * @return MutableSharedFlow for events that are instances of clazz
   */
  internal fun <T : Any> forEvent(clazz: Class<T>): MutableSharedFlow<T?> {
    return flows.getOrPut(clazz) {
      MutableSharedFlow<T?>(extraBufferCapacity = 5000)
    } as MutableSharedFlow<T?>
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
    return forEvent(clazz).filterNotNull()
  }

  /**
   * Posts new event to SharedFlow of the [event] type.
   * @param retain If the [event] should be retained in the flow for future subscribers. This is true by default.
   */
  @JvmOverloads
  fun <T : Any> post(event: T, retain: Boolean = true) {
    val flow = forEvent(event.javaClass)

    flow.tryEmit(event).also {
      if (!it)
        throw IllegalStateException("SharedFlow cannot take element, this should never happen")
    }
    if (!retain) {
      // without starting a coroutine here, the event is dropped immediately
      // and not delivered to subscribers
      CoroutineScope(Job() + Dispatchers.IO).launch {
        dropEvent(event.javaClass)
      }
    }
  }

  /**
   *  Removes retained event of type [clazz]
   */
  fun <T> dropEvent(clazz: Class<T>) {
    if (!flows.contains(clazz)) return
    val channel = flows[clazz] as MutableSharedFlow<T?>
    channel.tryEmit(null)
  }

  /**
   *  Removes all retained events
   */
  fun dropAll() {
    flows.values.forEach {
      (it as MutableSharedFlow<Any?>).tryEmit(null)
    }
  }
}