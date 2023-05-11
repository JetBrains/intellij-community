package com.intellij.performance.performancePlugin.commands

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Ref
import com.intellij.usages.Usage
import com.intellij.util.Processors
import com.jetbrains.performancePlugin.commands.FindUsagesCommand
import com.jetbrains.performancePlugin.commands.GoToNamedElementCommand

import com.jetbrains.performancePlugin.commands.PerformanceCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinClassFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinPropertyFindUsagesOptions
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FindUsagesKotlinCommand(text: String, line: Int) : PerformanceCommand(text, line) {
  companion object {
    const val NAME = "findUsagesKotlin"
    const val PREFIX = CMD_PREFIX + NAME
    private val LOG = Logger.getInstance(FindUsagesKotlinCommand::class.java)
  }

  override fun getName(): String {
    return NAME
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val arguments = text.split(Regex.fromLiteral(" ")).toTypedArray()
    val position = arguments[1]
    val elementName = arguments[2]
    val result = GoToNamedElementCommand(GoToNamedElementCommand.PREFIX + " $position $elementName", -1).execute(context)
    result.onError {
      actionCallback.reject("fail to go to element $elementName")
    }
    result.blockingGet(30, TimeUnit.SECONDS)
    val project = context.project
    val findUsagesFinished = CountDownLatch(1)
    val allUsages: List<Usage> = ArrayList()
    val span: Ref<Span> = Ref()
    DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(fun() {

      val editor = FileEditorManager.getInstance(context.project).selectedTextEditor
      val offset = editor!!.caretModel.offset
      val element = when (GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)) {
        null -> GotoDeclarationAction.findTargetElement(project, editor, offset)
        else -> GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)
      }
      if (element == null) {
        actionCallback.reject("Can't find an element under current $offset offset.")
        return
      }

      val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
      val handler = findUsagesManager.getFindUsagesHandler(element, FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS)
      if (handler == null) {
        actionCallback.reject("No find usage handler found for the element:" + element.text)
        return
      }

      val findUsagesOptions = when (element.toString()) {
        "FUN" -> KotlinFunctionFindUsagesOptions(project).apply {
          isOverridingMethods = false
          isImplementingMethods = false
          isCheckDeepInheritance = true
          isIncludeInherited = false
          isIncludeOverloadUsages = false
          isImplicitToString = true
          isSearchForBaseMethod = true
          isSkipImportStatements = false
          isSearchForTextOccurrences = false
          isUsages = true
          searchExpected = true
        }
        "CLASS", "OBJECT_DECLARATION" -> KotlinClassFindUsagesOptions(project).apply {
          searchExpected = true
          searchConstructorUsages = true
          isMethodsUsages = false
          isFieldsUsages = false
          isDerivedClasses = false
          isImplementingClasses = false
          isDerivedInterfaces = false
          isCheckDeepInheritance = true
          isIncludeInherited = false
          isSkipImportStatements = false
          isSearchForTextOccurrences = true
          isUsages = true
        }
        else -> KotlinPropertyFindUsagesOptions(project).apply {
          searchExpected = true
          isReadWriteAccess = true
          searchOverrides = false
          isReadAccess = true
          isWriteAccess = true
          isSearchForAccessors = false
          isSearchInOverridingMethods = false
          isSearchForBaseAccessors = false
          isSkipImportStatements = false
          isSearchForTextOccurrences = false
          isUsages = true
        }
      }
      val collectProcessor = Processors.cancelableCollectProcessor(Collections.synchronizedList(allUsages))
      span.set(startSpan(FindUsagesCommand.SPAN_NAME))
      FindUsagesManager.startProcessUsages(handler, handler.primaryElements, handler.secondaryElements, collectProcessor,
                                           findUsagesOptions) { findUsagesFinished.countDown() }
    }))
    try {
      findUsagesFinished.await()
      span.get().setAttribute("number", allUsages.size.toLong())
      span.get().end()
    }
    catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
    FindUsagesCommand.storeMetricsDumpFoundUsages(allUsages, project)
    actionCallback.setDone()
    return actionCallback.toPromise()
  }
}