/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package training.actions.showUI

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.util.LearnUiUtil

/**
 * Created by karashevich on 28/07/15.
 */
class ShowNavBar : AnAction() {

  override fun actionPerformed(anActionEvent: AnActionEvent) {
    LearnUiUtil.getInstance().highlightIdeComponent(LearnUiUtil.IdeComponent.NAVIGATION_BAR, anActionEvent.project)
  }
}
