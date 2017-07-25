package training.learn

import training.learn.exceptons.BadLessonException
import training.learn.exceptons.BadModuleException
import training.learn.exceptons.LessonIsOpenedException
import java.awt.FontFormatException
import java.io.IOException
import java.util.concurrent.ExecutionException

/**
 * Created by karashevich on 27/02/15.
 */
open class LessonListenerAdapter : LessonListener {

  override fun lessonStarted(lesson: Lesson) {}
  override fun lessonPassed(lesson: Lesson) {}
  override fun lessonClosed(lesson: Lesson) {}

  @Throws(BadLessonException::class, ExecutionException::class, IOException::class, FontFormatException::class, InterruptedException::class,
          BadModuleException::class, LessonIsOpenedException::class)
  override fun lessonNext(lesson: Lesson) {}

}