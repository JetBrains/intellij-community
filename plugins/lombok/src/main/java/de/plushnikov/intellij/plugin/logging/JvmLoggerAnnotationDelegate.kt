package de.plushnikov.intellij.plugin.logging

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.ProjectContainingLibrariesScope
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil

/**
 * Represents a delegate implementation of the JvmLogger interface that is used to insert loggers which are [PsiAnnotation].
 * Use it to handle with Lombok loggers
 * @param loggerTypeName the fully qualified name of the logger's type.
 * @param annotationTypeName the fully qualified name of annotation used to generate logger
 * @param priority the priority of the logger.
 */
class JvmLoggerAnnotationDelegate(
  override val loggerTypeName: String,
  override val id: String,
  private val annotationTypeName: String,
  override val priority: Int
) : JvmLogger {
  override fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement? {
    if (logger !is PsiAnnotation) return null
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(logger)
    return clazz.modifierList?.addAfter(logger, null)
  }

  override fun isAvailable(project: Project?): Boolean {
    return project != null &&
           JavaPsiFacade.getInstance(project).findClass(loggerTypeName, ProjectContainingLibrariesScope.getScope(project)) != null
           && LombokLibraryUtil.hasLombokLibrary(project)
  }

  override fun isAvailable(module: Module?): Boolean {
    return module != null && JavaLibraryUtil.hasLibraryClass(module, loggerTypeName) && LombokLibraryUtil.hasLombokClasses(module)
  }

  override fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass): Boolean = clazz.hasAnnotation(annotationTypeName).not()

  override fun createLogger(project: Project, clazz: PsiClass): PsiAnnotation {
    val factory = JavaPsiFacade.getElementFactory(project)
    return factory.createAnnotationFromText("@$annotationTypeName", clazz)
  }

  override fun getLogFieldName(clazz: PsiClass): String {
    return AbstractLogProcessor.getLoggerName(clazz)
  }
}