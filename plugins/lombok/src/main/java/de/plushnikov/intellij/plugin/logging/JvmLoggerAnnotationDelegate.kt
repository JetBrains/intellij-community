package de.plushnikov.intellij.plugin.logging

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil

/**
 * Represents a delegate implementation of the JvmLogger interface that is used to insert loggers which are [PsiAnnotation].
 * Use it to handle with Lombok loggers
 * @param fieldLoggerName the fully qualified name of the logger's type.
 * @param loggerTypeName the fully qualified name of annotation used to generate logger
 * @param priority the priority of the logger.
 */
class JvmLoggerAnnotationDelegate(
  private val fieldLoggerName: String,
  override val loggerTypeName: String,
  override val priority: Int
) : JvmLogger {
  override fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement? {
    if (logger !is PsiAnnotation) return null
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(logger)
    return clazz.modifierList?.addAfter(logger, null)
  }

  override fun isAvailable(project: Project?): Boolean {
    return project != null && JavaLibraryUtil.hasLibraryClass(project, fieldLoggerName) && LombokLibraryUtil.hasLombokLibrary(project)
  }

  override fun isAvailable(module: Module?): Boolean {
    return module != null && JavaLibraryUtil.hasLibraryClass(module, fieldLoggerName) && LombokLibraryUtil.hasLombokClasses(module)
  }

  override fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass): Boolean = clazz.hasAnnotation(loggerTypeName).not()

  override fun createLogger(project: Project, clazz: PsiClass): PsiAnnotation {
    val factory = JavaPsiFacade.getElementFactory(project)
    return factory.createAnnotationFromText("@$loggerTypeName", clazz)
  }
}