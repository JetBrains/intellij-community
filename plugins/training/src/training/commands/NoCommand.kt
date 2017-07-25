package training.commands


/**
 * Created by karashevich on 30/01/15.
 */
class NoCommand : Command(Command.CommandType.NOCOMMAND) {

  override fun execute(executionList: ExecutionList) {
    //do nothing
  }
}
