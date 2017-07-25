package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import training.lang.LangManager
import training.learn.CourseManager
import training.ui.views.LanguageChoosePanel
import training.ui.views.LearnPanel
import training.ui.views.ModulesPanel
import javax.swing.JPanel


/**
 * Created by karashevich on 17/03/16.
 */
class LearnToolWindow : SimpleToolWindowPanel, DataProvider, Disposable {

    val myContentPanel: JPanel = JPanel()
    //TODO: remove public modificator set ScrollPane before release
    var scrollPane: JBScrollPane? = null
        set
    private var myLearnPanel: LearnPanel? = null
    private var modulesPanel: ModulesPanel? = null
    private var myProject: Project? = null

    internal constructor() : super(true, true)

    constructor(vertical: Boolean) : super(vertical) {}
    constructor(vertical: Boolean, borderless: Boolean) : super(vertical, borderless) {}

    fun init(project: Project) {

        myProject = project
        reinitViewsInternal()
        if (LangManager.getInstance().isLangUndefined()) {
            val myLanguageChoosePanel = LanguageChoosePanel()
            scrollPane = JBScrollPane(myLanguageChoosePanel)
        } else {
            scrollPane = JBScrollPane(modulesPanel)
        }
        setContent(scrollPane)
    }

    private fun reinitViewsInternal() {
        myLearnPanel = LearnPanel()
        modulesPanel = ModulesPanel()
        CourseManager.getInstance().modulesPanel = modulesPanel
        CourseManager.getInstance().learnPanel = myLearnPanel
    }

    //do not call on modulesPanel view view or learnPanel view
    public fun reinitViews() {
        reinitViewsInternal()
    }

    override fun dispose() {
        CourseManager.getInstance().learnPanel = null
        myLearnPanel = null
    }


}

