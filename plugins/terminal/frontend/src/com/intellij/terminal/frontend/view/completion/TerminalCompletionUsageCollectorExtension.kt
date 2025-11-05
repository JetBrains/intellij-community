package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputLanguage
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics

private val COMMAND_FIELD = EventFields.String(
  "terminal_command",
  TerminalCommandUsageStatistics.getKnownCommandValuesList(),
  "CLI name extracted from the first token in the currently typed command"
)
private val SUBCOMMAND_FIELD = EventFields.String(
  "terminal_subcommand",
  TerminalCommandUsageStatistics.getKnownSubCommandValuesList(),
  "Subcommand of the CLI extracted from the second token in the currently typed command"
)
private val LAST_SELECTED_ITEM_TYPE = EventFields.Enum<TerminalCompletionItemType>(
  "terminal_last_selected_item_type",
  "Type of the item that was last selected in the lookup at the moment of closing"
)
private val LAST_SELECTED_ITEM_LENGTH = EventFields.Int(
  "terminal_last_selected_item_length",
  "Length of the item that was last selected in the lookup at the moment of closing"
)
private val LAST_SELECTED_ITEM_MATCHING_LENGTH = EventFields.Int(
  "terminal_last_selected_item_matching_length",
  "Length of the typed prefix that matches (case-insensitive) the prefix of the last selected item in the lookup at the moment of closing"
)
private val WAS_EXECUTED_FIELD = EventFields.Boolean(
  "terminal_was_executed",
  "True means that there was a full match of typed text with the selected item, so the command was executed on Enter press instead of just inserting the completion item"
)

/**
 * Declares additional fields that can be reported with the "finished" event of "completion" FUS group.
 * Version of [LookupUsageTracker.GROUP] should be incremented every time any field is changed there.
 */
internal class TerminalCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(
      COMMAND_FIELD,
      SUBCOMMAND_FIELD,
      LAST_SELECTED_ITEM_TYPE,
      LAST_SELECTED_ITEM_LENGTH,
      LAST_SELECTED_ITEM_MATCHING_LENGTH,
      WAS_EXECUTED_FIELD,
    )
  }
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [TerminalCompletionUsageCollectorExtension].
 */
internal class TerminalCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "terminal"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    if (lookupResultDescriptor.language != TerminalOutputLanguage) return emptyList()

    return getCommandContextData(lookupResultDescriptor) + getLastSelectedItemData(lookupResultDescriptor)
  }

  private fun getLastSelectedItemData(descriptor: LookupResultDescriptor): List<EventPair<*>> {
    val lookup = descriptor.lookup as? LookupImpl ?: return emptyList()
    val item = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY) ?: return emptyList()
    val result = mutableListOf<EventPair<*>>()

    val suggestion = item.`object` as? ShellCompletionSuggestion
    if (suggestion != null) {
      val type = suggestion.type.toItemType()
      result.add(LAST_SELECTED_ITEM_TYPE with type)
    }

    result.add(LAST_SELECTED_ITEM_LENGTH with item.lookupString.length)

    val typedPrefix = lookup.itemPattern(item)
    val matchingLength = typedPrefix.commonPrefixWith(item.lookupString, ignoreCase = true).length
    result.add(LAST_SELECTED_ITEM_MATCHING_LENGTH with matchingLength)

    if (descriptor.finishType == LookupUsageTracker.FinishType.EXPLICIT
        && canExecuteWithChosenItem(item.lookupString, typedPrefix)) {
      result.add(WAS_EXECUTED_FIELD with true)
    }

    return result
  }

  private fun getCommandContextData(descriptor: LookupResultDescriptor): List<EventPair<*>> {
    val lookup = descriptor.lookup as? LookupImpl ?: return emptyList()
    val completingCommand = lookup.getUserData(TerminalCommandCompletion.COMPLETING_COMMAND_KEY) ?: return emptyList()
    val commandData = TerminalCommandUsageStatistics.getLoggableCommandData(completingCommand) ?: return emptyList()

    val command = COMMAND_FIELD with commandData.command
    val subCommand = commandData.subCommand?.let { SUBCOMMAND_FIELD with it }
    return listOfNotNull(command, subCommand)
  }
}

internal enum class TerminalCompletionItemType {
  COMMAND,
  OPTION,
  ARGUMENT,
  FILE,
  FOLDER,
}

private fun ShellSuggestionType.toItemType(): TerminalCompletionItemType {
  return when (this) {
    ShellSuggestionType.COMMAND -> TerminalCompletionItemType.COMMAND
    ShellSuggestionType.OPTION -> TerminalCompletionItemType.OPTION
    ShellSuggestionType.ARGUMENT -> TerminalCompletionItemType.ARGUMENT
    ShellSuggestionType.FILE -> TerminalCompletionItemType.FILE
    ShellSuggestionType.FOLDER -> TerminalCompletionItemType.FOLDER
  }
}