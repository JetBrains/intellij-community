package com.intellij.ide.starter.bus

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 **/

/**
 * @see FlowBus.dropEvent
 */
inline fun <reified T : Any> FlowBus.dropEvent() = dropEvent(T::class.java)

/**
 * @see FlowBus.getFlow
 */
inline fun <reified T : Any> FlowBus.getFlow() = getFlow(T::class.java)

/**
 * Simplified [EventsReceiver.subscribeTo] for Kotlin.
 * Type of event is automatically inferred from [callback] parameter type.
 *
 * @param skipRetained Skips event already present in the flow. This is `false` by default
 * @param callback The callback function
 * @return This instance of [EventsReceiver] for chaining
 */
inline fun <reified T : Any> EventsReceiver.subscribe(skipRetained: Boolean = false,
                                                      noinline callback: suspend (event: T) -> Unit): EventsReceiver {
  return subscribeTo(T::class.java, skipRetained, callback)
}

/**
 * A variant of [subscribe] that uses an instance of [EventCallback] as callback.
 *
 * @param skipRetained Skips event already present in the flow. This is `false` by default
 * @param callback Interface with implemented callback function
 * @return This instance of [EventsReceiver] for chaining
 * @see [subscribe]
 */
inline fun <reified T : Any> EventsReceiver.subscribe(callback: EventCallback<T>, skipRetained: Boolean = false): EventsReceiver {
  return subscribeTo(T::class.java, callback, skipRetained)
}