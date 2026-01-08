package com.intellij.lambda.sampleTestsWithFixtures.fixtures

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.Disposable
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureImpl

/** Inits legacy test fixtures from IntelliJ test framework */
context(context: LambdaIdeContext)
inline fun <reified T : IdeaTestFixture> newTestFixture(fixtureSetup: (Disposable) -> T): T {
  // if the fixture already exists, return it
  if (context.testFixtures.get<T>().isNotEmpty()) return context.testFixtures.first()

  frameworkLogger.info("Initializing test fixture '${T::class.simpleName}'")
  val fixture = fixtureSetup(context.globalDisposable)

  context.addAfterEachCleanup {
    frameworkLogger.info("Disposing test fixture '${T::class.simpleName}'")
    fixture.tearDown()
  }

  context.testFixtures.add(fixture)
  fixture.setUp()
  return fixture
}

/** Inits test fixture, used in JUnit5 IntelliJ test framework */
context(context: LambdaIdeContext)
inline fun <reified T : TestFixtureImpl<T>> newTestFixture(fixtureSetup: (Disposable) -> T): T {
  // if the fixture already exists, return it
  if (context.testFixtures.get<T>().isNotEmpty()) return context.testFixtures.first()

  val fixture = fixtureSetup(context.globalDisposable)

  // TODO: add fixture disposal callback

  context.testFixtures.add(fixture)
  TODO("JUnit5 fixture initialization isn't implemented yet")
  //fixture.init(testScope = context, )
  return fixture
}
