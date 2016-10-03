package circlet.utils

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.*
import com.intellij.psi.*

inline fun <reified T: Any> ComponentManager.getComponent() : T =
    this.getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

val application: Application get() = ApplicationManager.getApplication()

inline fun <reified T : Any>component() : T = application.getComponent<T>()
inline fun <reified T : Any>Project.component() : T = this.getComponent<T>()

val DataContext.Editor : Editor?
    get() = CommonDataKeys.EDITOR.getData(this)

val DataContext.PsiFile : PsiFile?
    get() = CommonDataKeys.PSI_FILE.getData(this)

val DataContext.Project : Project?
    get() = CommonDataKeys.PROJECT.getData(this)

