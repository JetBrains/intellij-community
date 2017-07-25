package training.editor.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import java.util.*

/**
 * Created by karashevich on 17/08/15.
 */
class LearnActionPromoter : ActionPromoter {

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    for (action in actions) {
      if (action is LearnActions) {
        return ArrayList(actions)
      }
    }

    return ArrayList(actions)
  }
}
