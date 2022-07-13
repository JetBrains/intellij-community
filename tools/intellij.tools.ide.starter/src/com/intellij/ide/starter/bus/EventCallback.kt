package com.intellij.ide.starter.bus

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 * */
interface EventCallback<T> {

  /**
   * This function will be called for received event
   */
  fun onEvent(event: T)
}

