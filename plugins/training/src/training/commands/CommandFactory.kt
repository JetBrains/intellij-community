package training.commands

import org.jdom.Element

/**
 * Created by karashevich on 30/01/15.
 */
object CommandFactory {

  fun buildCommand(element: Element?): Command {

    element ?: return NoCommand()

    val tagName = element.name ?: return NoCommand()
    val commandType: Command.CommandType = Command.CommandType.values().firstOrNull { tagName.toUpperCase() == it.toString().toUpperCase() } ?: return NoCommand()

    when (commandType) {
      Command.CommandType.TEXT -> return TextCommand()
      Command.CommandType.MOVECARET -> return MoveCaretCommand()
      Command.CommandType.TRY -> return TryCommand()
      Command.CommandType.COPYTEXT -> return CopyTextCommand()
      Command.CommandType.ACTION -> return ActionCommand()
      Command.CommandType.TYPETEXT -> return TypeTextCommand()
      Command.CommandType.WIN -> return WinCommand()
      Command.CommandType.MOUSEBLOCK -> return MouseBlockCommand()
      Command.CommandType.MOUSEUNBLOCK -> return MouseUnblockCommand()
      Command.CommandType.CARETBLOCK -> return CaretBlockCommand()
      Command.CommandType.CARETUNBLOCK -> return CaretUnblockCommand()
      Command.CommandType.SHOWLINENUMBER -> return ShowLineNumberCommand()
      Command.CommandType.EXPANDALLBLOCKS -> return ExpandAllBlocksCommand()
      Command.CommandType.SETSELECTION -> return SetSelectionCommand()
      else -> throw BadCommandException("unable to parse command from" + tagName)
    }
  }

}
