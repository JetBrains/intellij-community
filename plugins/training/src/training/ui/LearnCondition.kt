package training.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Condition

/**
 * Created by jetbrains on 17/03/16.
 */
class LearnCondition : Condition<Any>, DumbAware {

  override fun value(o: Any): Boolean {
    return false
  }
}
