package training.commands

import com.intellij.find.FindManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import java.util.concurrent.ExecutionException


/**
 * Created by karashevich on 30/01/15.
 */
class SetSelectionCommand : Command(Command.CommandType.SETSELECTION) {

  //always put the caret at the end of the selection
  @Throws(InterruptedException::class, ExecutionException::class, BadCommandException::class)
  override fun execute(executionList: ExecutionList) {

    var start_line: Int
    var start_column: Int
    var end_line: Int
    var end_column: Int
    var park_caret: Int = 0

    val element = executionList.elements.poll()
    val editor = executionList.editor

    if (element.getAttribute(START_SELECT_POSITION) != null) {
      var positionString = element.getAttribute(START_SELECT_POSITION)!!.value
      var splitStrings = positionString.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      assert(splitStrings.size == 2)

      start_line = Integer.parseInt(splitStrings[0])
      start_column = Integer.parseInt(splitStrings[1])

      if (element.getAttribute(END_SELECT_POSITION) != null) {
        positionString = element.getAttribute(END_SELECT_POSITION)!!.value
        splitStrings = positionString.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        assert(splitStrings.size == 2)

        end_line = Integer.parseInt(splitStrings[0])
        end_column = Integer.parseInt(splitStrings[1])

        start_line--
        start_column--
        end_line--
        end_column--

        ApplicationManager.getApplication().invokeAndWait {
          val blockStart = LogicalPosition(start_line, start_column)
          val blockEnd = LogicalPosition(end_line, end_column)

          val start_position = editor.logicalPositionToOffset(blockStart)
          val end_position = editor.logicalPositionToOffset(blockEnd)

          editor.selectionModel.setSelection(start_position, end_position)
          park_caret = end_position
        }
      }
      else {
        throw BadCommandException(this)
      }
    }
    else if (element.getAttribute(START_SELECT_STRING) != null && element.getAttribute(END_SELECT_STRING) != null) {
      val document = editor.document
      val project = executionList.project

      ApplicationManager.getApplication().invokeAndWait {
        val findManager = FindManager.getInstance(project)
        val model = findManager.findInFileModel.clone()
        model.isGlobal = false
        model.isReplaceState = false

        val value_start = element.getAttribute(START_SELECT_STRING)!!.value
        model.stringToFind = value_start
        val start = FindManager.getInstance(project).findString(document.charsSequence, 0, model)

        val value_end = element.getAttribute(END_SELECT_STRING)!!.value
        model.stringToFind = value_end
        val end = FindManager.getInstance(project).findString(document.charsSequence, 0, model)

        selectInDocument(executionList, start.startOffset, end.endOffset)
        park_caret = end.endOffset
      }
    }
    else {
      throw BadCommandException(this)
    }

    //move caret to the end of the selection
    ApplicationManager.getApplication().invokeAndWait {
      executionList.editor.caretModel.moveToOffset(park_caret)
    }
    startNextCommand(executionList)
  }

  private fun selectInDocument(executionList: ExecutionList, startOffset: Int, endOffset: Int) {
    val editor = executionList.editor
    editor.selectionModel.setSelection(startOffset, endOffset)
  }

  companion object {

    val START_SELECT_POSITION = "start-position"
    val END_SELECT_POSITION = "end-position"

    val START_SELECT_STRING = "start-string"
    val END_SELECT_STRING = "end-string"
  }

}
