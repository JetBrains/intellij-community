package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.lambda.testFramework.starter.LambdaIdeSession
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.extension.AfterClassTemplateInvocationCallback
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream

class MonolithAndSplitModeClassTemplateProvider :
  ClassTemplateInvocationContextProvider,
  AfterClassTemplateInvocationCallback {
  override fun supportsClassTemplate(context: ExtensionContext): Boolean {
    return getClassTemplateModesToRun(context).isNotEmpty()
  }

  override fun provideClassTemplateInvocationContexts(context: ExtensionContext): Stream<out ClassTemplateInvocationContext> {
    val modesToRun = getClassTemplateModesToRun(context)
    logOutput("Test class ${context.testClass.getOrNull()} will be run in modes: $modesToRun")

    return modesToRun.asSequence().map { mode -> createClassInvocationContext(mode) }.asStream()
  }

  /**
   * Stops the IDE of this invocation.
   *
   * JUnit runs this before the `afterAll` of the class, so the IDE-side `afterAll` does not reach this IDE. See
   * [com.intellij.ide.starter.driver.reused.ReusedSessionStrategy.afterAllOnIde].
   */
  override fun afterClassTemplateInvocation(context: ExtensionContext) {
    LambdaIdeSession.stop(context)
  }

  private fun createClassInvocationContext(mode: IdeRunMode): ClassTemplateInvocationContext {
    return object : ClassTemplateInvocationContext {
      override fun getDisplayName(invocationIndex: Int): String = mode.name

      override fun prepareInvocation(context: ExtensionContext) {
        putInvocationRunMode(context, mode)
      }
    }
  }
}
