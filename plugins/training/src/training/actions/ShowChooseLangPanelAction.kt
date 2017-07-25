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
package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.ui.LearnToolWindowFactory
import training.ui.views.LanguageChoosePanel

/**
 * @author Sergey Karashevich
 */
class ShowChooseLangPanelAction: AnAction() {

    override fun actionPerformed(e: AnActionEvent?) {
        val myLanguageChoosePanel = LanguageChoosePanel()
        val myLearnToolWindow = LearnToolWindowFactory.myLearnToolWindow ?: throw Exception("Unable to get Learn toolwindow (is null)")
        val scrollPane = myLearnToolWindow.scrollPane
        scrollPane!!.setViewportView(myLanguageChoosePanel)
        scrollPane.revalidate()
        scrollPane.repaint()
    }

}