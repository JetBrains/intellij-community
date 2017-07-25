package training.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.ExtensionPointName
import training.learn.CourseManager
import training.ui.LearnToolWindowFactory

/**
 * @author Sergey Karashevich
 */

@State(name = "TrainingLangManager", storages = arrayOf(Storage("trainingPlugin.xml")))
class LangManager: PersistentStateComponent<LangManager.State> {

    var supportedLanguagesExtensions: List<LanguageExtensionPoint<LangSupport>> = ExtensionPointName<LanguageExtensionPoint<LangSupport>>(LangSupport.EP_NAME).extensions.toList()
    val myState = State()

    private var myLangSupport: LangSupport? = null

    init {
        if (supportedLanguagesExtensions.size == 1) {
            val first = supportedLanguagesExtensions.first()
            myLangSupport = first.instance
            myState.languageName = first.language
        }
    }

    companion object { fun getInstance(): LangManager = ServiceManager.getService(LangManager::class.java) }

    fun isLangUndefined() = (myLangSupport == null)

    //do not call this if LearnToolWindow with modules or learn views due to reinitViews
    fun updateLangSupport(langSupport: LangSupport) {
        myLangSupport = langSupport
        CourseManager.getInstance().updateModules()
        (LearnToolWindowFactory.myLearnToolWindow ?: return).reinitViews()
    }

    fun getLangSupport() = myLangSupport

    override fun loadState(state: State?) {
        if (state == null) return
        myLangSupport = supportedLanguagesExtensions.find { langExt -> langExt.language == state.languageName }!!.instance
    }
    override fun getState() = myState

    class State {
        var languageName: String? = null
    }

    fun getLanguageDisplayName(): String {
        if (myState.languageName == null) return "default"
        return (Language.findLanguageByID(myState.languageName) ?: return "default").displayName
    }

}