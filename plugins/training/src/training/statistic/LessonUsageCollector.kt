package training.statistic

import com.intellij.internal.statistic.CollectUsagesException
import com.intellij.internal.statistic.UsagesCollector
import com.intellij.internal.statistic.beans.GroupDescriptor
import com.intellij.internal.statistic.beans.UsageDescriptor
import training.learn.CourseManager
import java.util.*

/**
 * Created by Sergey Karashevich on 02/02/16.
 */
class LessonUsageCollector : UsagesCollector() {

  @Throws(CollectUsagesException::class)
  override fun getUsages(): Set<UsageDescriptor> {
    val result = HashSet<UsageDescriptor>()

    val modules = CourseManager.getInstance().modules
    for (module in modules!!) {
      for (lesson in module.lessons) {
        lesson.statistic.mapTo(result) {
          UsageDescriptor("module>" + module.getName() +
                          ">lesson>" + lesson.name +
                          ">status>" + it.status +
                          ">time>" + it.timestamp, 1)
        }
      }
    }
    return result
  }

  override fun getGroupId(): GroupDescriptor {
    return GroupDescriptor.create("plugin.training.v" + PROTOCOL_VERSION)
  }

  companion object {
    private val PROTOCOL_VERSION = 1
  }

}
