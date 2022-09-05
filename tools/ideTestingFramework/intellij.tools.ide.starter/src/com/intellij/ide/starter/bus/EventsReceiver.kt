package com.intellij.ide.starter.bus

import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 * Class for receiving events posted to [FlowBus]
 *
 * @param bus [FlowBus] instance to subscribe to. If not set, [StarterBus] will be used
 */
open class EventsReceiver @JvmOverloads constructor(
  private val bus: FlowBus = StarterBus
) {

  private val jobs = mutableMapOf<Class<*>, Job>()

  private var returnDispatcher: CoroutineDispatcher = Dispatchers.IO

  /**
   * Set the `CoroutineDispatcher` which will be used to launch your callbacks.
   *
   * If this [EventsReceiver] was created on the main thread the default dispatcher will be [Dispatchers.Main].
   * In any other case [Dispatchers.IO] will be used.
   */
  fun returnOn(dispatcher: CoroutineDispatcher): EventsReceiver {
    returnDispatcher = dispatcher
    return this
  }

  /**
   * Subscribe to events that are type of [clazz] with the given [callback] function.
   * The [callback] can be called immediately if event of type [clazz] is present in the flow.
   *
   * @param clazz Type of event to subscribe to
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback The callback function
   * @return This instance of [EventsReceiver] for chaining
   */
  @JvmOverloads
  fun <T : Any> subscribeTo(
    clazz: Class<T>,
    skipRetained: Boolean = false,
    callback: suspend (event: T) -> Unit
  ): EventsReceiver {

    if (jobs.containsKey(clazz))
      throw IllegalArgumentException("Already subscribed for event type: $clazz")

    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      throw throwable
    }

    val job = CoroutineScope(Job() + Dispatchers.IO + exceptionHandler).launch {
      bus.forEvent(clazz)
        .drop(if (skipRetained) 1 else 0)
        .filterNotNull()
        .collect {
          catchAll {
            withContext(returnDispatcher) { callback(it) }
          }
        }
    }

    jobs[clazz] = job
    return this
  }

  /**
   * A variant of [subscribeTo] that uses an instance of [EventCallback] as callback.
   *
   * @param clazz Type of event to subscribe to
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback Interface with implemented callback function
   * @return This instance of [EventsReceiver] for chaining
   * @see [subscribeTo]
   */
  @JvmOverloads
  fun <T : Any> subscribeTo(
    clazz: Class<T>,
    callback: EventCallback<T>,
    skipRetained: Boolean = false
  ): EventsReceiver = subscribeTo(clazz, skipRetained) { callback.onEvent(it) }

  /**
   * Unsubscribe from events type of [clazz]
   */
  fun <T : Any> unsubscribe(clazz: Class<T>) {
    jobs.remove(clazz)?.cancel()
  }

  /**
   * Unsubscribe from all events
   */
  fun unsubscribe() {
    runBlocking {
      jobs.values.forEach { it.cancelAndJoin() }
    }

    jobs.clear()
  }
}
