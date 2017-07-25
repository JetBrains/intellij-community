package training.learn.exceptons

/**
 * Created by karashevich on 29/01/15.
 */
class BadModuleException : Exception {

  constructor() {}

  constructor(s: String) : super(s) {}
}
