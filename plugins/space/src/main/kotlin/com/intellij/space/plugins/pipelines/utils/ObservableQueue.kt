// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.utils

import runtime.reactive.Signal
import runtime.reactive.Source
import java.util.*

interface ObservableQueue<T> : Queue<T> {
  @Suppress("unused")
  sealed class Change<out T>(val index: T) {
    class Add<out T>(val value: T) : Change<T>(value)
  }

  val change: Source<Change<T>>

  companion object {
    fun <T> mutable(): ObservableQueue<T> = ObservableMutableQueue()
  }
}

class ObservableMutableQueue<T> : ObservableQueue<T>, Queue<T> {

  private val storage = ArrayDeque<T>()

  override val change = Signal.create<ObservableQueue.Change<T>>()

  override fun contains(element: T): Boolean {
    return storage.contains(element)
  }

  override fun addAll(elements: Collection<T>): Boolean {
    return storage.addAll(elements)
  }

  override fun clear() {
    TODO("not implemented") //return storage.clear()
  }

  override fun element(): T {
    TODO("not implemented") //return storage.element()
  }

  override fun isEmpty(): Boolean {
    return storage.isEmpty()
  }

  override fun remove(): T {
    TODO("not implemented") //return storage.remove()
  }

  override fun remove(element: T): Boolean {
    TODO("not implemented") //return storage.remove(element)
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    TODO("not implemented") //return storage.containsAll(elements)
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    TODO("not implemented") //return storage.removeAll(elements)
  }

  override fun add(element: T): Boolean {
    storage.add(element)
    change.fire(ObservableQueue.Change.Add(element))
    return true
  }

  override fun offer(e: T): Boolean {
    TODO("not implemented") //return storage.offer(e)
  }

  override fun iterator(): MutableIterator<T> {
    TODO("not implemented") //return storage.iterator()
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    TODO("not implemented") //return storage.retainAll(elements)
  }

  override fun peek(): T {
    TODO("not implemented") //return storage.peek()
  }

  override fun poll(): T {
    TODO("not implemented") //return storage.poll()
  }

  override val size: Int
    get() = storage.size
}
