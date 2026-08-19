package com.intellij.ide.starter.runner

import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

data class TestMethod(
  val name: String,
  val displayName: String,
  val testClass: Class<*>,
  val startTime: LocalDateTime = LocalDateTime.now(),
  var arguments: List<Any> = emptyList(),
  val id: String = "${testClass.name}#$name/$displayName",
) {
  val clazzSimpleName: String = testClass.simpleName
  val clazz: String = testClass.name

  fun argsString(): String = arguments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "(", postfix = ")", separator = " ") ?: ""

  fun fullName(): String {
    return "$clazz.$name${argsString()}"
  }
}

/**
 * Container that contains the current test method reference.
 * Method is provided by [com.intellij.ide.starter.junit5.CurrentTestMethodProvider]
 */
object CurrentTestMethod {
  @Volatile
  private var testMethod: TestMethod? = null

  private val onChangeListeners = CopyOnWriteArrayList<(TestMethod?) -> Unit>()

  /** Remembers the method that is about to run. Listeners are announced separately, by [publishToListeners]. */
  fun set(method: TestMethod?) {
    testMethod = method
  }

  /**
   * Announces the remembered method to listeners.
   *
   * Called once the test runner has opened the test on the CI side, so that listeners may report metadata that attaches
   * to the current test. Setting the method is deliberately not enough: the provider learns about a test before the
   * runner has reported it to TeamCity.
   *
   * Every listener is announced to even when an earlier one throws, and the failures are reported together afterwards.
   * A listener detaches itself when it is announced to and finds its own subject gone — [removeOnChangeListener] from
   * inside the callback is the only removal there is — so a listener that throws in front of the others would keep
   * them from ever running *and* keep itself registered, turning one dead listener into a permanently failing
   * announcement that outlives whatever it was watching.
   */
  fun publishToListeners() {
    var failure: Throwable? = null
    for (listener in onChangeListeners) {
      try {
        listener(testMethod)
      }
      catch (e: Throwable) {
        if (failure == null) failure = e else failure.addSuppressed(e)
      }
    }
    if (failure != null) throw failure
  }

  fun get(): TestMethod? {
    return testMethod
  }

  fun addOnChangeListener(listener: (TestMethod?) -> Unit) {
    onChangeListeners.add(listener)
    listener(get())
  }

  fun removeOnChangeListener(listener: (TestMethod?) -> Unit) {
    onChangeListeners.remove(listener)
  }
}