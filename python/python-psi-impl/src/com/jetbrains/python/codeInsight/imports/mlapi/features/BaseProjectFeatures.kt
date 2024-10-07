// Optimized and improved Kotlin code

package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.ml.*
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.codeInsight.imports.mlapi.MLUnitImportCandidatesList
import com.jetbrains.python.psi.*

private val interestingClasses = arrayOf(
  PyFile::class.java,
  PyAssignmentStatement::class.java,
  PyFunction::class.java,
  PyCallExpression::class.java,
  PyArgumentList::class.java,
  PyBinaryExpression::class.java,
)
enum class FileExtensionType {
  PY,     // .py files
  IPYNB,  // .ipynb files
  PYX,    // .pyx files
  PXD,    // .pxd files
  PXI     // .pxi files
}

class BaseProjectFeatures : FeatureProvider(MLUnitImportCandidatesList) {
  object Features {
    val NUM_PYTHON_FILES_IN_PROJECT = FeatureDeclaration.int("num_python_files_in_project") {
      "The estimated amount of files in the project (by a power of 2)"
    }.nullable()
    val PSI_PARENT_OF_ORIG = (1..5).map { i -> FeatureDeclaration.aClass("psi_parent_of_orig_$i") { "PSI parent of original element #$i" }.nullable() }
    val FILE_EXTENSION_TYPE = FeatureDeclaration.enum<FileExtensionType>("file_extension_type") { "extension of the original python file" }.nullable()
  }

  override val featureComputationPolicy = FeatureComputationPolicy(false, true)
  override val featureDeclarations = extractFieldsAsFeatureDeclarations(Features)

  override suspend fun computeFeatures(units: MLUnitsMap, usefulFeaturesFilter: FeatureFilter): List<Feature> = buildList {
    val candidates = units[MLUnitImportCandidatesList]
    if (candidates.isEmpty()) return@buildList

    val project = candidates[0].importable?.project ?: return@buildList

    val scopeSize = readAction {
      FileTypeIndex.getFiles(PythonFileType.INSTANCE, GlobalSearchScope.projectScope(project)).size
    }
    add(Features.NUM_PYTHON_FILES_IN_PROJECT with Integer.highestOneBit(scopeSize))

    val editor = readAction { FileEditorManager.getInstance(project).selectedEditor as? TextEditor } ?: return@buildList
    val psiFile = readAction { PsiManager.getInstance(project).findFile(editor.file) } ?: return@buildList
    val offset = readAction { editor.editor.caretModel.offset }
    val element = readAction { psiFile.findElementAt(offset) }
    add(Features.FILE_EXTENSION_TYPE with getFileExtensionType(psiFile.toString()))

    readAction {
      Features.PSI_PARENT_OF_ORIG.withIndex().forEach { (i, featureDeclaration) ->
        var parent: PsiElement? = element
        repeat(i + 1) {
          parent = PsiTreeUtil.getParentOfType(parent, *interestingClasses)
        }
        add(featureDeclaration with parent?.javaClass)
      }
    }
  }
  private fun getFileExtensionType(fileName: String): FileExtensionType? {
    val extension = fileName.substringAfterLast('.', "").uppercase()

    return try {
      FileExtensionType.valueOf(extension)
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}