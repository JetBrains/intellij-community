// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.compatibility

import org.jdom.Element
import org.jetbrains.idea.maven.utils.MavenJDOMUtil


class MavenPluginM2ELifecycles(val prefix: String, vararg entities: MavenPluginLifecycleEntity) {
  private val runOnInc = HashSet<String>()
  private val runOnConfig = HashSet<String>()

  init {
    entities.forEach { entity ->
      if (entity.runOnIncBuild) runOnInc.addAll(entity.goals)
      if (entity.runOnConfiguration) runOnConfig.addAll(entity.goals)
    }
  }

  fun runOnIncremental(goal: String): Boolean = runOnInc.contains(goal)
  fun runOnConfiguration(goal: String): Boolean = runOnConfig.contains(goal)
}

data class MavenPluginLifecycleEntity(val goals: List<String>, val runOnIncBuild: Boolean, val runOnConfiguration: Boolean)


class MavenLifecycleMetadataReader {
  companion object {
    @JvmStatic
    fun read(prefix: String, data: ByteArray?): MavenPluginM2ELifecycles? {
      if (data == null) return null
      val root = MavenJDOMUtil.read(data, null)
      val pluginExecutions =
        MavenJDOMUtil.findChildrenByPath(root, "pluginExecutions", "pluginExecution")

      val entities = pluginExecutions.map { parseEntities(it) }
      if (entities.isEmpty()) return null
      return MavenPluginM2ELifecycles(prefix, *entities.toTypedArray())
    }

    private fun parseEntities(el: Element): MavenPluginLifecycleEntity {
      val goals = MavenJDOMUtil.findChildrenValuesByPath(el, "pluginExecutionFilter.goals", "goal");
      var action = MavenJDOMUtil.findChildByPath(el, "action");
      if (action == null || action.getChild("ignore") != null) return MavenPluginLifecycleEntity(goals, false, false);
      var runOnInc = MavenJDOMUtil.findChildValueByPath(action, "execute.runOnIncremental").toBoolean()
      var runOnConfig = MavenJDOMUtil.findChildValueByPath(action, "execute.runOnConfiguration").toBoolean()
      return MavenPluginLifecycleEntity(goals, runOnInc, runOnConfig);
    }
  }

}