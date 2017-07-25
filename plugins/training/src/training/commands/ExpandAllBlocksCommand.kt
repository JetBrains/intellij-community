package training.commands

/**
 * Created by karashevich on 30/01/15.
 */
@Deprecated("")
class ExpandAllBlocksCommand : Command(Command.CommandType.EXPANDALLBLOCKS) {

  override fun execute(executionList: ExecutionList) {
    //Block caret and perform next command
    //        ActionManager.getInstance().getAction()
    executionList.elements.poll()
    executionList.editor.settings.isAutoCodeFoldingEnabled = false
    executionList.editor.settings.isAllowSingleLogicalLineFolding = false
    startNextCommand(executionList)

  }
}
