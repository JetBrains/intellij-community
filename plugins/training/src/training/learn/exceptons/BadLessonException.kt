package training.learn.exceptons

/**
 * Created by karashevich on 29/01/15.
 */
class BadLessonException : Exception {

  constructor(s: String) : super(s) {}

  constructor() : super() {}
}
