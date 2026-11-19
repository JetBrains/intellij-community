package com.intellij.ide.starter.process

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Starts a real child JVM and runs the real `jstack`, as the dump before the kill of an IDE does for its Maven server. */
class ChildJavaThreadDumpsTest {
  @Test
  fun `each child Java process gets its own thread dump`(@TempDir tempDir: Path) {
    tempDir.resolve("Sleeper.java").writeText("""
      public class Sleeper {
        public static void main(String[] args) throws Exception {
          System.out.println("ready");
          System.out.flush();
          Thread.sleep(60_000);
        }
      }
    """.trimIndent())
    val javaHome = Path.of(System.getProperty("java.home"))
    val child = ProcessBuilder(javaHome.resolve("bin/java").toString(), tempDir.resolve("Sleeper.java").toString()).start()
    try {
      child.inputReader().readLine() shouldBe "ready"

      runBlocking { collectChildJavaThreadDumps(ProcessHandle.current().pid(), javaHome, null, tempDir, timestamp = 1) }

      tempDir.resolve("$KILL_CHILD_THREAD_DUMP_FILE_PREFIX-1-${child.pid()}.txt").readText() shouldContain "Sleeper.main"
    }
    finally {
      child.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
    }
  }

  @Test
  fun `the timeout analyzer does not take a child dump for the dump of the IDE`() {
    KILL_CHILD_THREAD_DUMP_FILE_PREFIX.startsWith(KILL_THREAD_DUMP_FILE_PREFIX) shouldBe false
  }
}
