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
package com.jetbrains.python.psi.impl

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PythonSdkPathCache
import com.jetbrains.python.psi.resolve.ResolveImportUtil
import com.jetbrains.python.psi.resolve.fromSdk
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.NonNls

/**
 * Provides access to Python builtins via skeletons.
 */
class PyBuiltinCache(
  val builtinsFile: PyFile? = null,
  private val myTypeshedFile: PyFile? = null,
  private val myExceptionsFile: PyFile? = null
) {
  /**
   * Stores the most often used types, returned by nNNType.
   */
  private val myTypeCache = mutableMapOf<String?, PyClassTypeImpl?>()

  private var myModStamp: Long = -1

  constructor(project: Project, sdk: Sdk) : this(
    builtinsFile = getBuiltinsForSdk(project, sdk),
    myTypeshedFile = getFileForSdk(project, sdk, QualifiedName.fromDottedString(TYPESHED_MODULE)),
    myExceptionsFile = getExceptionsForSdk(project, sdk)
  )

  val isValid: Boolean
    get() = builtinsFile?.isValid() == true

  /**
   * Looks for a top-level named item. (Package builtins does not contain any sensible nested names anyway.)
   *
   * @param name to look for
   * @return found element, or null.
   */
  fun getByName(name: @NonNls String?): PsiElement? {
    if (builtinsFile != null) {
      val element = builtinsFile.getElementNamed(name)
      if (element != null) {
        return element
      }
    }
    if (myExceptionsFile != null) {
      return myExceptionsFile.getElementNamed(name)
    }
    return null
  }

  fun getClass(name: @NonNls String): PyClass? {
    if (builtinsFile != null) {
      return builtinsFile.findTopLevelClass(name)
    }
    return null
  }

  fun getObjectType(name: @NonNls String): PyClassTypeImpl? {
    var pyClass: PyClassTypeImpl?
    synchronized(myTypeCache) {
      if (builtinsFile != null) {
        if (builtinsFile.modificationStamp != myModStamp) {
          myTypeCache.clear()
          myModStamp = builtinsFile.modificationStamp
        }
      }
      pyClass = myTypeCache[name]
    }
    if (pyClass == null) {
      val cls = getClass(name)
      if (cls != null) { // null may happen during testing
        pyClass = PyClassTypeImpl(cls, false)
        pyClass.assertValid(name)
        synchronized(myTypeCache) {
          myTypeCache.put(name, pyClass)
        }
      }
    }
    else {
      pyClass.assertValid(name)
    }
    return pyClass
  }

  val objectType: PyClassType?
    get() = getObjectType("object")

  val listType: PyClassType?
    get() = getObjectType("list")

  val dictType: PyClassType?
    get() = getObjectType("dict")

  val setType: PyClassType?
    get() = getObjectType("set")

  val tupleType: PyClassType?
    get() = getObjectType("tuple")

  val intType: PyClassType?
    get() = getObjectType("int")

  val floatType: PyClassType?
    get() = getObjectType("float")

  val complexType: PyClassType?
    get() = getObjectType("complex")

  val strType: PyClassType?
    get() = getObjectType("str")

  fun getBytesType(level: LanguageLevel): PyClassType? {
    return getObjectType(if (level.isPy3K) "bytes" else "str")
  }

  fun getUnicodeType(level: LanguageLevel): PyClassType? {
    return getObjectType(if (level.isPy3K) "str" else "unicode")
  }

  fun getStringType(level: LanguageLevel): PyType? {
    return if (level.isPy3K) {
      getObjectType("str")
    }
    else {
      this.strOrUnicodeType
    }
  }

  fun getByteStringType(level: LanguageLevel): PyType? {
    return if (level.isPy3K) {
      getObjectType("bytes")
    }
    else {
      this.strOrUnicodeType
    }
  }

  val strOrUnicodeType: PyType?
    get() = getStrOrUnicodeType(false)

  fun getStrOrUnicodeType(definition: Boolean): PyType? {
    var str: PyClassLikeType? = getObjectType("str")
    var unicode: PyClassLikeType? = getObjectType("unicode")

    if (str != null && str.isDefinition xor definition) {
      str = if (definition) str.toClass() else str.toInstance()
    }

    if (unicode != null && unicode.isDefinition xor definition) {
      unicode = if (definition) unicode.toClass() else unicode.toInstance()
    }

    return PyUnionType.union(str, unicode)
  }

  val boolType: PyClassType?
    get() = getObjectType("bool")

  val classMethodType: PyClassType?
    get() = getObjectType("classmethod")

  val staticMethodType: PyClassType?
    get() = getObjectType("staticmethod")

  val typeType: PyClassType?
    get() = getObjectType("type")


  val noneType: PyClassType?
    get() {
      val name = "NoneType"
      var pyClass: PyClassTypeImpl?
      synchronized(myTypeCache) {
        pyClass = myTypeCache[name]
      }
      if (pyClass == null) {
        val cls = if (myTypeshedFile != null)
          ResolveImportUtil.resolveChildren(
            myTypeshedFile, name, myTypeshedFile, false, true, false, false
          ).getOrNull(0)?.element as PyClass?
        else
          null
        if (cls != null) { // null may happen during testing
          pyClass = PyClassTypeImpl(cls, false)
          pyClass.assertValid(name)
          synchronized(myTypeCache) {
            myTypeCache.put(name, pyClass)
          }
        }
      }
      else {
        pyClass.assertValid(name)
      }
      return pyClass
    }

  /**
   * @param target an element to check.
   * @return true iff target is inside the __builtins__.py
   */
  fun isBuiltin(target: PsiElement?): Boolean {
    target ?: return false
    PyPsiUtils.assertValid(target)
    if (!target.isValid) return false
    val the_file = target.containingFile
    if (the_file !is PyFile) {
      return false
    }
    // files are singletons, no need to compare URIs
    return the_file === builtinsFile || the_file === myExceptionsFile
  }

  companion object {
    const val BUILTIN_MODULE: String = "__builtin__"
    const val BUILTIN_MODULE_3K: String = "builtins"
    const val TYPESHED_MODULE: String = "_typeshed"
    private const val EXCEPTIONS_MODULE = "exceptions"

    private val DUD_INSTANCE = PyBuiltinCache()

    /**
     * Returns an instance of builtin cache. Instances differ per module and are cached.
     *
     * @param reference something to define the module from.
     * @return an instance of cache. If reference was null, the instance is a fail-fast dud one.
     */
    @JvmStatic
    fun getInstance(reference: PsiElement?): PyBuiltinCache {
      if (reference != null) {
        try {
          val sdk: Sdk? = findSdkForFile(reference.containingFile)
          if (sdk != null) {
            return PythonSdkPathCache.getInstance(reference.project, sdk).builtins
          }
        }
        catch (_: PsiInvalidElementAccessException) {
        }
      }
      return DUD_INSTANCE // a non-functional fail-fast instance, for a case when skeletons are not available
    }

    @JvmStatic
    fun findSdkForFile(psiFile: PsiFileSystemItem?): Sdk? {
      psiFile ?: return null
      val module = ModuleUtilCore.findModuleForPsiElement(psiFile)
      return if (module != null) PythonSdkUtil.findPythonSdk(module) else findSdkForNonModuleFile(psiFile)
    }

    fun findSdkForNonModuleFile(psiFile: PsiFileSystemItem): Sdk? {
      val vfile: VirtualFile?
      if (psiFile is PsiFile) {
        val contextFile = FileContextUtil.getContextFile(psiFile)
        vfile = contextFile?.originalFile?.virtualFile
      }
      else {
        vfile = psiFile.virtualFile
      }
      var sdk: Sdk? = null
      if (vfile != null) { // reality
        val projectRootManager = ProjectRootManager.getInstance(psiFile.project)
        sdk = projectRootManager.projectSdk
        if (sdk == null) {
          val orderEntries = projectRootManager.fileIndex.getOrderEntriesForFile(vfile)
          for (orderEntry in orderEntries) {
            if (orderEntry is JdkOrderEntry) {
              sdk = orderEntry.jdk
            }
            else if (OrderEntryUtil.isModuleLibraryOrderEntry(orderEntry)) {
              sdk = PythonSdkUtil.findPythonSdk(orderEntry!!.ownerModule)
            }
          }
        }
      }
      return sdk
    }

    @JvmStatic
    fun getBuiltinsForSdk(project: Project, sdk: Sdk): PyFile? {
      return getFileForSdk(project, sdk, QualifiedName.fromDottedString(getBuiltinsModuleName(PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk))))
    }

    @JvmStatic
    fun getBuiltinsFileName(level: LanguageLevel): String {
      return getBuiltinsModuleName(level) + PyNames.DOT_PY
    }

    @JvmStatic
    fun getBuiltinsModuleName(level: LanguageLevel): String {
      return if (level.isPython2) BUILTIN_MODULE else BUILTIN_MODULE_3K
    }

    fun getExceptionsForSdk(project: Project, sdk: Sdk): PyFile? {
      return getFileForSdk(project, sdk, QualifiedName.fromDottedString(EXCEPTIONS_MODULE))
    }

    private fun getFileForSdk(project: Project, sdk: Sdk, moduleName: QualifiedName): PyFile? {
      val sdkType = sdk.getSdkType()
      if (PyNames.PYTHON_SDK_ID_NAME == sdkType.name) {
        val results = resolveQualifiedName(moduleName, fromSdk(project, sdk))
        return results.firstOrNull()?.let { PyUtil.turnDirIntoInit(it) } as? PyFile
      }
      return null
    }

    @JvmStatic
    fun isInBuiltins(expression: PyExpression): Boolean {
      if (expression is PyQualifiedExpression && (expression.isQualified)) {
        return false
      }
      val name = expression.name
      val reference = expression.reference
      if (reference != null && name != null) {
        val cache: PyBuiltinCache = getInstance(expression)
        if (cache.getByName(name) != null) {
          val resolved = reference.resolve()
          if (resolved != null && cache.isBuiltin(resolved)) {
            return true
          }
        }
      }
      return false
    }
  }
}
