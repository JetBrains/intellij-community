// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.collector

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.statistics.*

class PythonNewProjectWizardCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  companion object {
    private val GROUP = EventLogGroup("python.new.project.wizard", 2)
    private val INHERIT_GLOBAL_SITE_PACKAGE_FIELD = EventFields.Boolean("inherit_global_site_package")
    private val MAKE_AVAILABLE_TO_ALL_PROJECTS = EventFields.Boolean("make_available_to_all_projects")
    private val PREVIOUSLY_CONFIGURED = EventFields.Boolean("previously_configured")
    private val GENERATOR_FIELD = EventFields.Class("generator")
    private val DJANGO_ADMIN_FIELD = EventFields.Boolean("django_admin")
    private val PROJECT_GENERATED_EVENT = GROUP.registerVarargEvent("project.generated",
                                                            INTERPRETER_TYPE,
                                                            EXECUTION_TYPE,
                                                            PYTHON_VERSION,
                                                            GENERATOR_FIELD,
                                                            INHERIT_GLOBAL_SITE_PACKAGE_FIELD,
                                                            MAKE_AVAILABLE_TO_ALL_PROJECTS,
                                                            PREVIOUSLY_CONFIGURED)

    private val DJANGO_ADMIN_CHECKED = GROUP.registerEvent("django.admin.selected", DJANGO_ADMIN_FIELD)

    fun logPythonNewProjectGenerated(info: InterpreterStatisticsInfo, pythonVersion: LanguageLevel, generatorClass: Class<*>) {
      PROJECT_GENERATED_EVENT.log(
        INTERPRETER_TYPE.with(info.type.value),
        EXECUTION_TYPE.with(info.target.value),
        PYTHON_VERSION.with(pythonVersion.toPythonVersion()),
        INHERIT_GLOBAL_SITE_PACKAGE_FIELD.with(info.globalSitePackage),
        MAKE_AVAILABLE_TO_ALL_PROJECTS.with(info.makeAvailableToAllProjects),
        PREVIOUSLY_CONFIGURED.with(info.previouslyConfigured),
        GENERATOR_FIELD.with(generatorClass)
      )
    }

    fun logDjangoAdminSelected(djangoAdminSelected: Boolean) {
      DJANGO_ADMIN_CHECKED.log(djangoAdminSelected)
    }
  }
}

data class InterpreterStatisticsInfo(val type: InterpreterType,
                                     val target: InterpreterTarget,
                                     val globalSitePackage: Boolean,
                                     val makeAvailableToAllProjects: Boolean,
                                     val previouslyConfigured: Boolean)

