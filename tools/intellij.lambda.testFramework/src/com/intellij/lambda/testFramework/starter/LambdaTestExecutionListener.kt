package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.coroutine.CommonScope
import com.intellij.ide.starter.di.di
import org.junit.platform.launcher.TestExecutionListener
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.net.URI

/**
 * Installs the DI bindings a lambda lane runs on, before its first test does.
 *
 * Registered through `META-INF/services` so that the `ServiceLoader` loads — and therefore initialises — this
 * class while the launcher is still assembling its listener list. That is why the bindings sit in a companion
 * `init` rather than in a callback: they have to be in place before anything asks for [CIServer], and the
 * reused-run recovery reporting asks as soon as it has something to report.
 *
 * The lambda modules live in community and do not depend on `intellij.ide.starter.extended`, so the
 * `ExtendedDIProvider` that binds these for the ultimate suites never runs for them. Without this, a lambda
 * lane on CI resolves the default non-TeamCity [CIServer] and every `reportTestFailure` it makes — including
 * the recovery-restart and .vmoptions reports — is dropped instead of failing the build.
 *
 * Stopping the IDE is deliberately *not* done here, and this listener has no plan-end callback at all. A reused
 * session is owned by the JUnit extension store and closed with it, so a plan-end teardown would be a second
 * owner for one IDE -- and under a runner that ends many test plans in one JVM, such as AIR's UI daemon, it would
 * close an IDE that the next plan is about to reuse. Having no callback is why that daemon does not have to
 * suppress this listener the way it suppresses the ones that do own suite-lifetime state.
 */
class LambdaTestExecutionListener : TestExecutionListener {
  companion object {
    init {
      CommonScope.perSuiteScopeForIdeActivities()

      di = DI {
        extend(di)

        bindSingleton(tag = "teamcity.uri", overrides = true) { URI("https://buildserver.labs.intellij.net").normalize() }
        bindSingleton<CIServer>(overrides = true) { TeamCityCIServer() }
      }
    }
  }
}
