package training.commands

/**
 * Created by karashevich on 18/09/15.
 */
class BadCommandException : Exception {

  constructor(command: Command) : super("exception in command " + command.commandType.toString())
  constructor(s: String) : super(s)
}
