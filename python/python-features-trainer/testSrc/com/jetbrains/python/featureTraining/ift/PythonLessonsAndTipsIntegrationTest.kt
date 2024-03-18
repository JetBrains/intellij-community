package com.intellij.python.featuresTrainer.ift

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import training.simple.LessonsAndTipsIntegrationTest

@RunWith(JUnit4::class)
class PythonLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
  override val languageId = "Python"
  override val languageSupport = PythonLangSupport()
  override val learningCourse = PythonLearningCourse()
}