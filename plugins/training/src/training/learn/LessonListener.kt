package training.learn

import training.learn.exceptons.BadLessonException
import training.learn.exceptons.BadModuleException
import training.learn.exceptons.LessonIsOpenedException
import java.awt.FontFormatException
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Created by karashevich on 27/02/15.
 */
interface LessonListener : EventListener {

  fun lessonStarted(lesson: Lesson)

  fun lessonPassed(lesson: Lesson)

  fun lessonClosed(lesson: Lesson)

  @Throws(BadLessonException::class, ExecutionException::class, IOException::class, FontFormatException::class, InterruptedException::class,
          BadModuleException::class, LessonIsOpenedException::class)
  fun lessonNext(lesson: Lesson)

}