package com.jetbrains.performancePlugin.commands

import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class TestTipsAndFeaturesIntegrationCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val callback = ActionCallbackProfilerStopper()

    val tips: List<TipAndTrickBean> = TipAndTrickBean.EP_NAME.extensionList
    val registry = ProductivityFeaturesRegistry.getInstance() ?: error("ProductivityFeaturesRegistry doesn't created")
    val features: List<FeatureDescriptor> = registry.featureIds.map(registry::getFeatureDescriptor)

    val issues = buildString {
      append(testEveryTipLinkedToAtLeastOneFeature(tips, features))
      append(testEveryFeatureHasLinkedTip(features))
      append(testEveryFeatureHasDisplayName(features))
      append(testEveryGroupHasDisplayName(features))
    }

    if (issues.isNotEmpty()) {
      throw Exception(issues)
    }

    callback.setDone()
    return callback.toPromise()
  }

  private fun testEveryTipLinkedToAtLeastOneFeature(tips: List<TipAndTrickBean>, features: List<FeatureDescriptor>): String {
    return findIncorrectValues(tips, "Tips that are not linked to any feature") { tip ->
      features.find { it.tipId == tip.id } == null
    }
  }

  private fun testEveryFeatureHasLinkedTip(features: List<FeatureDescriptor>): String {
    return findIncorrectValues(features, "Features without specified tip file") { feature ->
      feature.tipId.isNullOrEmpty()
    }
  }

  private fun testEveryFeatureHasDisplayName(features: List<FeatureDescriptor>): String {
    return findIncorrectValues(features, "Features without display name") { feature ->
      feature.displayName.startsWith("!")
    }
  }

  private fun testEveryGroupHasDisplayName(features: List<FeatureDescriptor>): String {
    val groupsWithoutDisplayName = features.mapNotNull { it.groupId }.toSet().filter { it.startsWith("!") }
    return if (groupsWithoutDisplayName.isNotEmpty()) {
      "Groups without display name:\n" + groupsWithoutDisplayName.joinToString("\n") + "\n"
    }
    else ""
  }

  private fun <T> findIncorrectValues(values: List<T>, message: String, predicate: (T) -> Boolean): String {
    val incorrect = values.filter(predicate)
    return if (incorrect.isNotEmpty()) {
      "$message:\n" + incorrect.joinToString("\n") + "\n"
    }
    else ""
  }

  companion object {
    const val PREFIX: String = "%testTipsAndFeaturesIntegration"
  }
}