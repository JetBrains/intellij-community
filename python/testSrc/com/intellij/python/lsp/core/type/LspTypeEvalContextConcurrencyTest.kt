package com.intellij.python.lsp.core.type

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shows that many threads can fill one element cache of an [LspTypeEvalContext] at the same time.
 *
 * Before PY-90488 that cache was a plain `WeakHashMap`. Two threads that put a value while the map
 * grows can make a bucket chain cyclic. A later `get` on that bucket then loops forever. The test
 * therefore fails by a timeout, and not by an assertion on a type.
 *
 * The test drives `loadSingleType`. That path locks the element, so two threads on two different
 * elements put a value into the one map with no common lock. `loadAllTypes` walks the PSI and
 * therefore needs a read action. A worker that holds a read action and then loops forever blocks
 * the write action in the fixture teardown. The run then hangs, and the test does not fail.
 */
@TestFor(issues = ["PY-90488"])
class LspTypeEvalContextConcurrencyTest : PyCodeInsightTestCase() {

  /**
   * Answers every request without an LSP server, so the test measures only the element cache.
   * A per-element string type keeps the threads off one stripe of the string-type lock.
   */
  private class FakeTypeEvalContext(psiFile: PsiFile) : LspTypeEvalContext(psiFile) {
    override fun requestTypes(pyTypedElements: Collection<PyTypedElement>): List<String?> =
      pyTypedElements.map { "T${System.identityHashCode(it)}" }

    override fun resolveStringType(element: PyTypedElement, stringType: String): Ref<PyType?> = Ref.create(null)
  }

  @Test
  fun `many threads share one element cache`() {
    myFixture.configureByText("a.py", (1..ELEMENT_COUNT).joinToString("\n") { "v$it = $it" })
    val psiFile = myFixture.file
    val elements = runReadActionBlocking { LspTypeEvalContext.collectElementsForCalculation(psiFile) }
    assertTrue(elements.size >= ELEMENT_COUNT, "The test file gave only ${elements.size} typed elements")

    val finishedRounds = AtomicInteger(0)
    // An unsafe cache makes a worker loop forever. The worker takes no read action, so it cannot
    // block the write action in the fixture teardown, and the daemon thread dies with the JVM.
    val pool = Executors.newFixedThreadPool(THREAD_COUNT, ThreadFactory { task ->
      Thread(task, "LspTypeEvalContext cache stress").apply { isDaemon = true }
    })
    // The map breaks only while it grows, so each round fills a new cache. One round alone finds
    // the defect only sometimes.
    repeat(ROUNDS) {
      val context = FakeTypeEvalContext(psiFile)
      val roundDone = AtomicInteger(0)
      repeat(THREAD_COUNT) {
        // Each thread takes the elements in its own order, so the per-element lock holds no two
        // threads apart. They put different keys into the one map together.
        val shuffled = elements.shuffled()
        pool.execute {
          for (element in shuffled) {
            context.provideType(element, isUserInitiated = true)
          }
          if (roundDone.incrementAndGet() == THREAD_COUNT) {
            finishedRounds.incrementAndGet()
          }
        }
      }
    }
    pool.shutdown()

    try {
      assertTrue(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                 "Only ${finishedRounds.get()} of $ROUNDS rounds finished in ${TIMEOUT_SECONDS}s. The element cache is corrupt.")
    }
    finally {
      pool.shutdownNow()
    }
  }

  private companion object {
    private const val ELEMENT_COUNT: Int = 400
    private const val THREAD_COUNT: Int = 8
    private const val ROUNDS: Int = 40
    private const val TIMEOUT_SECONDS: Long = 60
  }
}
