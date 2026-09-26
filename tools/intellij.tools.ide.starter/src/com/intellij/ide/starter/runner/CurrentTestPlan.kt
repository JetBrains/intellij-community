package com.intellij.ide.starter.runner

/**
 * The JUnit test plan this JVM is running, counted.
 *
 * A test plan is the lifetime per-test bookkeeping may assume: one plan activates each of its test methods once,
 * and activating an earlier one again is a lifecycle error. A JVM that runs several plans is not: a harness that
 * keeps one warm IDE across plans — the AIR UI daemon runs every lane iteration in the JVM it holds — replays the
 * same method ids in the next plan, and each of those runs deserves reporting of its own rather than a failure.
 *
 * Bumped by [com.intellij.ide.starter.junit5.CurrentTestMethodProvider] when the platform announces a plan, and
 * read by whatever remembers test methods, so "again" can mean "in this plan".
 */
object CurrentTestPlan {
  @Volatile
  var generation: Int = 0
    private set

  @Synchronized
  fun beginNew() {
    generation++
  }
}
