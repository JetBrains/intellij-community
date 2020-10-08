// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement

class SpaceScriptRunLineMarkerProvider : RunLineMarkerContributor() {

  override fun getInfo(element: PsiElement): Info? {
    val refs = element.references
    refs.forEach {
      // TODO: optimize it!!!
      /*
                  if (it is KtSimpleNameReference) {
                      val resolveResult = it.resolve()
                      if (resolveResult != null) {
                          val fqnName = resolveResult.getKotlinFqName()
                          if (fqnName != null) {
                              if (fqnName.asString() == "circlet.pipelines.config.dsl.api.Project.job") {
                                  val valueArgumentList = element.nextSibling as KtValueArgumentList
                                  val taskName = valueArgumentList.arguments.firstOrNull()?.children?.firstOrNull()?.reference?.canonicalText
                                  if (taskName != null) {
                                      val runAction = object : AnAction(ExecutionBundle.message("run.configurable.display.name"), null, AllIcons.RunConfigurations.TestState.Run) {
                                          override fun actionPerformed(e: AnActionEvent) {
                                              CircletRunConfigurationUtils.run(taskName, element.project)
                                          }
                                      }
                                      return Info(runAction)
                                  }
                              }
                          }
                      }
                  }
      */
    }

    return null
  }
}
