package com.intellij.mermaid.lang.preview

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class CodeInsightFixtureExtension(private val projectName: String): BeforeEachCallback, AfterEachCallback {
  private var fixtureInstance: CodeInsightTestFixture? = null

  val fixture: CodeInsightTestFixture
    get() = fixtureInstance!!

  override fun beforeEach(context: ExtensionContext?) {
    fixtureInstance = createFixture()
  }

  override fun afterEach(context: ExtensionContext?) {
    fixtureInstance!!.tearDown()
    fixtureInstance = null
  }

  private fun createFixture(): CodeInsightTestFixture {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    // TODO: Investigate why createLightFixtureBuilder is not working correctly with TestApplicationExtension
    val fixtureBuilder = factory.createFixtureBuilder(projectName)
    val testFixture = fixtureBuilder.fixture
    val fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixture)
    fixture.setUp()
    return fixture
  }
}
