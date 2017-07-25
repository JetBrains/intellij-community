package training.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jdom.Element
import training.learn.Lesson
import java.util.*

/**
 * Created by karashevich on 02/07/15.
 */
class ExecutionList(val elements: Queue<Element>, val lesson: Lesson, val project: Project, val editor: Editor, val target: String?)
