package com.intellij.python.featuresTrainer.ift

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.newProject.DeprecatedUtils
import com.jetbrains.python.sdk.findBaseSdks
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import training.dsl.LessonContext
import training.lang.LangSupport
import training.ui.LearningUiManager
import training.ui.shouldCollectFeedbackResults
import training.util.LessonEndInfo
import training.util.OnboardingFeedbackData
import java.util.concurrent.CompletableFuture

object PythonLessonsUtil {
  fun isPython3Installed(project: Project): Boolean {
    val sdk = project.pythonSdk ?: return false
    return PythonSdkFlavor.getFlavor(sdk)?.getLanguageLevel(sdk)?.isPy3K == true
  }

  fun LessonContext.showWarningIfPython3NotFound() {
    task {
      val callbackId = LearningUiManager.addCallback {
        PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, project.modules.first())
      }
      stateCheck { isPython3Installed(project) }
      showWarning(PythonLessonsBundle.message("python.3.required.warning.message", callbackId)) {
        !isPython3Installed(project)
      }
    }
  }

  fun prepareFeedbackDataForOnboardingLesson(project: Project,
                                             configPropertyName: String,
                                             reportTitle: String,
                                             feedbackReportId: String,
                                             primaryLanguage: LangSupport,
                                             lessonEndInfo: LessonEndInfo,
                                             usedInterpreterAtStart: String) {
    if (!shouldCollectFeedbackResults()) {
      return
    }

    if (PropertiesComponent.getInstance().getBoolean(configPropertyName, false)) {
      return
    }

    val allExistingSdks = listOf(*PyConfigurableInterpreterList.getInstance(null).model.sdks)
    val existingSdks = DeprecatedUtils.getValidPythonSdks(allExistingSdks)

    val interpreterVersions = CompletableFuture<List<String>>()
    ApplicationManager.getApplication().executeOnPooledThread {
      val context = UserDataHolderBase()
      val baseSdks = findBaseSdks(existingSdks, null, context)
      interpreterVersions.complete(baseSdks.mapNotNull { it.sdkType.getVersionString(it) }.sorted().distinct())
    }

    val usedInterpreter = project.pythonSdk?.versionString ?: "none"
    @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization") // a very strange warning report here
    val startInterpreter = if (usedInterpreterAtStart == usedInterpreter) "same" else usedInterpreterAtStart

    primaryLanguage.onboardingFeedbackData = object : OnboardingFeedbackData(reportTitle, lessonEndInfo) {
      override val feedbackReportId = feedbackReportId

      override val additionalFeedbackFormatVersion: Int = 1

      val interpreters: List<String>? by lazy {
        if (interpreterVersions.isDone) interpreterVersions.get() else null
      }
      override val addAdditionalSystemData: JsonObjectBuilder.() -> Unit = {
        put("current_interpreter", usedInterpreter)
        put("start_interpreter", startInterpreter)
        put("found_interpreters", buildJsonArray {
          for (i in interpreters ?: emptyList()) {
            add(JsonPrimitive(i))
          }
        })
      }

      override val addRowsForUserAgreement: Panel.() -> Unit = {
        row(PythonLessonsBundle.message("python.onboarding.feedback.system.found.interpreters")) {
          @Suppress("HardCodedStringLiteral")
          val interpreters: @NlsSafe String? = interpreters?.toString()
          label(interpreters ?: PythonLessonsBundle.message("python.onboarding.feedback.system.no.interpreters"))
        }
        row(PythonLessonsBundle.message("python.onboarding.feedback.system.used.interpreter")) {
          label(usedInterpreter)
        }
        row(PythonLessonsBundle.message("python.onboarding.feedback.system.start.interpreter")) {
          label(startInterpreter)
        }
      }

      override fun feedbackHasBeenProposed() {
        PropertiesComponent.getInstance().setValue(configPropertyName, true, false)
      }
    }
  }
}