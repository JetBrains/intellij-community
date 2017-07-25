package training.commands

/**
 * Created by karashevich on 30/01/15.
 */
class TextCommand : Command(Command.CommandType.TEXT) {

  @Throws(InterruptedException::class)
  override fun execute(executionList: ExecutionList) {


    val element = executionList.elements.poll()
    val lesson = executionList.lesson

    var htmlText = if (element.content.isEmpty()) "" else element.content[0].value
    if (htmlText.isEmpty()) htmlText = element.getAttribute("description")!!.value

    if (htmlText.isEmpty())
      updateDescription(htmlText, lesson)
    else
      updateHTMLDescription(htmlText, lesson)
    startNextCommand(executionList)

  }

}
