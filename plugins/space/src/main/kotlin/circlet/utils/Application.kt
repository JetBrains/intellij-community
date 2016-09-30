package circlet.utils

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.psi.*
import lifetime.*


inline fun <reified T : Any> getLogger(): Logger {
    return Logger.getInstance(T::class.java)
}

inline fun <reified T : Any> getLogger(instance: T): Logger {
    return Logger.getInstance(instance.javaClass)
}

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

interface IExternalTask{
    val lifetime : Lifetime

    val cancel : () -> Unit

    val title : String get
    val header : String get
    val description : String get
    val isIndeterminate : Boolean get
    val progress: Double
}


fun externalTask(project : Project, cancelable : Boolean, task : IExternalTask): Task {

    val lock = Object()
    task.lifetime.add {
        synchronized(lock) {
            lock.notify()
        }
    }

    return object : Task.Backgroundable(project, task.title, cancelable) {
        override fun run(pi: ProgressIndicator) {
            pi.isIndeterminate = task.isIndeterminate
            try {
                var canceled = false
                while (!task.lifetime.isTerminated) {
                    pi.text = task.header
                    pi.text2 = task.description
                    if (!task.isIndeterminate)
                        pi.fraction = task.progress

                    if (pi.isCanceled && !canceled) {
                        // cancel once
                        task.cancel()
                        canceled = true
                    }

                    synchronized(lock) {
                        lock.wait(50)
                    }
                }
            } catch (e: Exception) {
                getLogger(this).error(e)
            }
        }
    }
}
