// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.collector

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.createAdditionalDataField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.statistics.*

object PythonNewProjectWizardCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private val GROUP = EventLogGroup("python.new.project.wizard", 6)
  const val PROJECT_GENERATED_EVENT_ID = "project.generated"
  private val INHERIT_GLOBAL_SITE_PACKAGE_FIELD = EventFields.Boolean("inherit_global_site_package")
  private val MAKE_AVAILABLE_TO_ALL_PROJECTS = EventFields.Boolean("make_available_to_all_projects")
  private val PREVIOUSLY_CONFIGURED = EventFields.Boolean("previously_configured")
  private val GENERATOR_FIELD = EventFields.Class("generator")
  private val DJANGO_ADMIN_FIELD = EventFields.Boolean("django_admin")
  private val ADDITIONAL = createAdditionalDataField(GROUP.id, PROJECT_GENERATED_EVENT_ID)

  private val PROJECT_GENERATED_EVENT = GROUP.registerVarargEvent(PROJECT_GENERATED_EVENT_ID,
                                                                  INTERPRETER_TYPE,
                                                                  EXECUTION_TYPE,
                                                                  INTERPRETER_CREATION_MODE,
                                                                  PYTHON_VERSION,
                                                                  GENERATOR_FIELD,
                                                                  INHERIT_GLOBAL_SITE_PACKAGE_FIELD,
                                                                  MAKE_AVAILABLE_TO_ALL_PROJECTS,
                                                                  PREVIOUSLY_CONFIGURED,
                                                                  ADDITIONAL)

  private val DJANGO_ADMIN_CHECKED = GROUP.registerEvent("django.admin.selected", DJANGO_ADMIN_FIELD)

  @JvmStatic
  fun logPythonNewProjectGenerated(info: InterpreterStatisticsInfo,
                                   pythonVersion: LanguageLevel,
                                   generatorClass: Class<*>,
                                   additionalData: List<EventPair<*>>) {
    PROJECT_GENERATED_EVENT.log(
      INTERPRETER_TYPE.with(info.type.value),
      EXECUTION_TYPE.with(info.target.value),
      INTERPRETER_CREATION_MODE.with(info.creationMode.value),
      PYTHON_VERSION.with(pythonVersion.toPythonVersion()),
      INHERIT_GLOBAL_SITE_PACKAGE_FIELD.with(info.globalSitePackage),
      MAKE_AVAILABLE_TO_ALL_PROJECTS.with(info.makeAvailableToAllProjects),
      PREVIOUSLY_CONFIGURED.with(info.previouslyConfigured),
      GENERATOR_FIELD.with(generatorClass),
      ADDITIONAL.with(ObjectEventData(additionalData))
    )
  }

  @JvmStatic
  fun logDjangoAdminSelected(djangoAdminSelected: Boolean) {
    DJANGO_ADMIN_CHECKED.log(djangoAdminSelected)
  }
}

data class InterpreterStatisticsInfo(val type: InterpreterType,
                                     val target: InterpreterTarget,
                                     val globalSitePackage: Boolean = false,
                                     val makeAvailableToAllProjects: Boolean = false,
                                     val previouslyConfigured: Boolean = false,
                                     val creationMode: InterpreterCreationMode = InterpreterCreationMode.NA)

