// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift

import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.python.featuresTrainer.ift.lesson.assistance.PythonEditorCodingAssistanceLesson
import com.intellij.python.featuresTrainer.ift.lesson.basic.PythonContextActionsLesson
import com.intellij.python.featuresTrainer.ift.lesson.basic.PythonSelectLesson
import com.intellij.python.featuresTrainer.ift.lesson.basic.PythonSurroundAndUnwrapLesson
import com.intellij.python.featuresTrainer.ift.lesson.completion.*
import com.intellij.python.featuresTrainer.ift.lesson.essensial.PythonOnboardingTourLesson
import com.intellij.python.featuresTrainer.ift.lesson.navigation.PythonDeclarationAndUsagesLesson
import com.intellij.python.featuresTrainer.ift.lesson.navigation.PythonFileStructureLesson
import com.intellij.python.featuresTrainer.ift.lesson.navigation.PythonRecentFilesLesson
import com.intellij.python.featuresTrainer.ift.lesson.navigation.PythonSearchEverywhereLesson
import com.intellij.python.featuresTrainer.ift.lesson.refactorings.PythonInPlaceRefactoringLesson
import com.intellij.python.featuresTrainer.ift.lesson.refactorings.PythonQuickFixesRefactoringLesson
import com.intellij.python.featuresTrainer.ift.lesson.refactorings.PythonRefactorMenuLesson
import com.intellij.python.featuresTrainer.ift.lesson.refactorings.PythonRenameLesson
import com.intellij.python.featuresTrainer.ift.lesson.run.PythonDebugLesson
import com.intellij.python.featuresTrainer.ift.lesson.run.PythonRunConfigurationLesson
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonLanguage
import training.dsl.LessonUtil
import training.learn.CourseManager
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType
import training.learn.lesson.general.*
import training.learn.lesson.general.assistance.CodeFormatLesson
import training.learn.lesson.general.assistance.LocalHistoryLesson
import training.learn.lesson.general.assistance.ParameterInfoLesson
import training.learn.lesson.general.assistance.QuickPopupsLesson
import training.learn.lesson.general.navigation.FindInFilesLesson
import training.learn.lesson.general.refactorings.ExtractMethodCocktailSortLesson
import training.learn.lesson.general.refactorings.ExtractVariableFromBubbleLesson

class PythonLearningCourse : LearningCourseBase(PythonLanguage.INSTANCE.id) {
  override fun modules() = onboardingTour() + stableModules() + CourseManager.instance.findCommonModules("Git")

  private val isOnboardingLessonEnabled: Boolean
    get() = PlatformUtils.isPyCharmCommunity() || PlatformUtils.isPyCharmPro()

  private fun onboardingTour() = if (isOnboardingLessonEnabled) listOf(
    LearningModule(id = "Python.Onboarding",
                   name = PythonLessonsBundle.message("python.onboarding.module.name"),
                   description = PythonLessonsBundle.message("python.onboarding.module.description", LessonUtil.productName),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(PythonOnboardingTourLesson())
    }
  )
  else emptyList()

  private fun stableModules() = listOf(
    LearningModule(id = "Python.EditorBasics",
                   name = LessonsBundle.message("editor.basics.module.name"),
                   description = LessonsBundle.message("editor.basics.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
      listOf(
        PythonContextActionsLesson(),
        GotoActionLesson(ls("Actions.py.sample")),
        PythonSelectLesson(),
        CommentUncommentLesson(ls("Comment.py.sample")),
        DuplicateLesson(ls("Duplicate.py.sample")),
        MoveLesson("accelerate", ls("Move.py.sample")),
        CollapseLesson(ls("Collapse.py.sample")),
        PythonSurroundAndUnwrapLesson(),
        MultipleSelectionHtmlLesson(),
      )
    },
    LearningModule(id = "Python.CodeCompletion",
                   name = LessonsBundle.message("code.completion.module.name"),
                   description = LessonsBundle.message("code.completion.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      listOf(
        PythonBasicCompletionLesson(),
        PythonTabCompletionLesson(),
        PythonPostfixCompletionLesson(),
        PythonSmartCompletionLesson(),
        FStringCompletionLesson(),
      )
    },
    LearningModule(id = "Python.Refactorings",
                   name = LessonsBundle.message("refactorings.module.name"),
                   description = LessonsBundle.message("refactorings.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SCRATCH) {
      fun ls(sampleName: String) = loadSample("Refactorings/$sampleName")
      listOf(
        PythonRefactorMenuLesson(),
        PythonRenameLesson(),
        ExtractVariableFromBubbleLesson(ls("ExtractVariable.py.sample")),
        ExtractMethodCocktailSortLesson(ls("ExtractMethod.py.sample")),
        PythonQuickFixesRefactoringLesson(),
        PythonInPlaceRefactoringLesson(),
      )
    },
    LearningModule(id = "Python.CodeAssistance",
                   name = LessonsBundle.message("code.assistance.module.name"),
                   description = LessonsBundle.message("code.assistance.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.SINGLE_EDITOR) {
      fun ls(sampleName: String) = loadSample("CodeAssistance/$sampleName")
      listOf(
        LocalHistoryLesson(),
        CodeFormatLesson(ls("CodeFormat.py.sample"), true),
        ParameterInfoLesson(ls("ParameterInfo.py.sample")),
        QuickPopupsLesson(ls("QuickPopups.py.sample")),
        PythonEditorCodingAssistanceLesson(ls("EditorCodingAssistance.py.sample")),
      )
    },
    LearningModule(id = "Python.Navigation",
                   name = LessonsBundle.message("navigation.module.name"),
                   description = LessonsBundle.message("navigation.module.description"),
                   primaryLanguage = langSupport,
                   moduleType = LessonType.PROJECT) {
      listOf(
        PythonSearchEverywhereLesson(),
        FindInFilesLesson("src/warehouse/find_in_files_sample.py"),
        PythonDeclarationAndUsagesLesson(),
        PythonFileStructureLesson(),
        PythonRecentFilesLesson(),
      )
    },
  ) + if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(ProjectManager.getInstance().defaultProject)) { // project doesn't matter in this check for us
    listOf(
      LearningModule(id = "Python.RunAndDebug",
                     name = LessonsBundle.message("run.debug.module.name"),
                     description = LessonsBundle.message("run.debug.module.description"),
                     primaryLanguage = langSupport,
                     moduleType = LessonType.SINGLE_EDITOR) {
        listOf(
          PythonRunConfigurationLesson(),
          PythonDebugLesson(),
        )
      }
    )
  }
  else emptyList()

  override fun getLessonIdToTipsMap(): Map<String, List<String>> = mutableMapOf(
    // EditorBasics
    "context.actions" to listOf("ContextActions"),
    "Actions" to listOf("find_action", "GoToAction"),
    "Select" to listOf("smart_selection", "CtrlW"),
    "Comment line" to listOf("CommentCode"),
    "Duplicate" to listOf("CtrlD", "DeleteLine"),
    "Move" to listOf("MoveUpDown"),
    "Surround and unwrap" to listOf("SurroundWith"),

    // CodeCompletion
    "Basic completion" to listOf("CodeCompletion"),
    "Tab completion" to listOf("TabInLookups"),
    "Postfix completion" to listOf("PostfixCompletion"),

    // Refactorings
    "Refactoring menu" to listOf("RefactorThis"),
    "Rename" to listOf("Rename"),
    "Extract variable" to listOf("IntroduceVariable"),
    "Extract method" to listOf("ExtractMethod"),
    "refactoring.quick.fix" to listOf("QuickFixRightArrow"),
    "refactoring.in.place" to listOf("InPlaceRefactoring"),

    // CodeAssistance
    "CodeAssistance.LocalHistory" to listOf("local_history"),
    "CodeAssistance.CodeFormatting" to listOf("LayoutCode"),
    "CodeAssistance.ParameterInfo" to listOf("ParameterInfo"),
    "CodeAssistance.QuickPopups" to listOf("CtrlShiftIForLookup", "CtrlShiftI", "QuickJavaDoc"),
    "CodeAssistance.EditorCodingAssistance" to listOf("HighlightUsagesInFile", "NextPrevError", "NavigateBetweenErrors"),

    // Navigation
    "Search everywhere" to listOf("SearchEverywhere", "GoToClass", "search_everywhere_general"),
    "Find in files" to listOf("FindReplaceToggle", "FindInPath"),
    "Declaration and usages" to listOf("GoToDeclaration", "ShowUsages"),
    "File structure" to listOf("FileStructurePopup"),
    "Recent Files and Locations" to listOf("recent-locations", "RecentFiles"),

    // RunAndDebug
    "python.run.configuration" to listOf("SelectRunDebugConfiguration"),
    "python.debug.workflow" to listOf("BreakpointSpeedmenu", "QuickEvaluateExpression", "EvaluateExpressionInEditor"),
  ).also { map ->
    val gitCourse = CourseManager.instance.findCommonCourseById("Git")
    if (gitCourse != null) {
      map.putAll(gitCourse.getLessonIdToTipsMap())
    }
  }
}