package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.PolymorphRef
import com.intellij.driver.client.PolymorphRefRegistry
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ProjectManager
import com.intellij.driver.sdk.remoteDev.RdProjectUtil


class PolymorphRegistryImpl(private val driver: Driver) : PolymorphRefRegistry {
  override fun convert(ref: PolymorphRef, target: RdTarget): PolymorphRef {
    val currentTarget = (ref as RefWrapper).getRef().rdTarget()
    if (currentTarget == target) return ref

    return when (ref) {
      is Project -> convertProject(ref, currentTarget, target)
      else -> throw IllegalStateException("Object $ref was marked as polymorph, but there no convert method for it")
    }
  }

  private fun convertProject(project: Project, currentTarget: RdTarget, target: RdTarget): Project {
    val rdProjectUtilCurrentTarget = driver.utility(RdProjectUtil::class, currentTarget)
    val rdProjectUtilTarget = driver.utility(RdProjectUtil::class, target)

    val projectIdFromCurrent = rdProjectUtilCurrentTarget.getRdProjectId(project)

    val targetProjectManager = driver.service(ProjectManager::class, target)
    val openedProjects = targetProjectManager.getOpenProjects()

    return openedProjects.singleOrNull { projectFromTarget ->
      rdProjectUtilTarget.getRdProjectId(projectFromTarget).value == projectIdFromCurrent.value
    } ?: throw IllegalStateException("Can not find project ${project.getName()} for target $target")
  }
}
