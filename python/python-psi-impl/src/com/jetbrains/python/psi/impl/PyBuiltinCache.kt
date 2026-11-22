// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.python.sdk.backend.findPythonInterpreterIfReady
import com.intellij.python.sdk.backend.languageLevel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.isIgnoredInterpreterEntry
import com.intellij.python.sdk.backend.isInterpreterLibraryCarrier
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.util.Key
import com.jetbrains.python.psi.resolve.mainPythonInterpreter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.QualifiedName
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PythonSdkPathCache
import com.jetbrains.python.psi.resolve.PyResolveCachesInvalidator
import com.jetbrains.python.psi.resolve.findModuleForPsiElementCached
import com.jetbrains.python.psi.resolve.fromInterpreter
import com.jetbrains.python.psi.resolve.pythonInterpreterIfReady
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import org.jetbrains.annotations.NonNls

/**
 * Provides access to Python builtins via skeletons.
 */
class PyBuiltinCache private constructor(
  private val myBuiltinsFile: CachedFile? = null,
  private val myTypesFile: CachedFile? = null,
  private val myTypeshedFile: CachedFile? = null,
  private val myExceptionsFile: CachedFile? = null,
) {
  val builtinsFile: PyFile? = myBuiltinsFile?.file

  constructor(project: Project, interpreter: PythonInterpreter) : this(
    myBuiltinsFile = getBuiltinsForInterpreter(project, interpreter).toCachedFile(),
    myTypesFile = getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(TYPES_MODULE)).toCachedFile(),
    myTypeshedFile = getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(TYPESHED_MODULE)).toCachedFile(),
    myExceptionsFile = getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(EXCEPTIONS_MODULE)).toCachedFile(),
  )

  /**
   * True when the builtins file is present and each cached file is still valid.
   * Otherwise [com.jetbrains.python.psi.resolve.PythonSdkPathCache.getBuiltins] makes a new cache.
   */
  val isValid: Boolean
    get() = builtinsFile?.isValid() == true &&
            myTypesFile.isAbsentOrValid() && myTypeshedFile.isAbsentOrValid() && myExceptionsFile.isAbsentOrValid()

  /**
   * Looks for a top-level named item. (Package builtins does not contain any sensible nested names anyway.)
   *
   * @param name to look for
   * @return found element, or null.
   */
  fun getByName(name: @NonNls String?): PsiElement? {
    // Accept both the short name ("int") and the canonical builtin qualified name ("builtins.int")
    val shortName = PyNames.FQN.unqualifyBuiltinName(name)
    return builtinsFile?.getElementNamed(shortName)?.let { return it }
           ?: myExceptionsFile?.file?.getElementNamed(shortName)
  }

  fun getClass(name: @NonNls String): PyClass? {
    return builtinsFile?.findTopLevelClass(PyNames.FQN.unqualifyBuiltinName(name) ?: name)
  }

  fun getObjectType(name: @NonNls String): PyClassType? {
    return myBuiltinsFile?.getClassType(PyNames.FQN.unqualifyBuiltinName(name) ?: name)
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

    return PyUnionType.unionOrUnknown(str ?: PyAnyType.unknown, unicode ?: PyAnyType.unknown)
  }

  val boolType: PyClassType?
    get() = getObjectType("bool")

  val classMethodType: PyClassType?
    get() = getObjectType("classmethod")

  val staticMethodType: PyClassType?
    get() = getObjectType("staticmethod")

  val typeType: PyClassType?
    get() = getObjectType("type")

  val sliceType: PyClassType?
    get() = getObjectType("slice")

  /** The class type of `None`, or null when `types.NoneType` does not resolve. See also [noneTypeOrUnknown]. */
  val noneType: PyClassType?
    get() = myTypesFile?.getClassType("NoneType") ?: myTypeshedFile?.getClassType("NoneType")

  /** The type of `None`, or Unknown when `types.NoneType` does not resolve. */
  val noneTypeOrUnknown: PyType?
    get() = noneType ?: PyAnyType.unknown

  val ellipsisType: PyClassType?
    get() = myTypesFile?.getClassType("EllipsisType")

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
    return the_file === builtinsFile || the_file === myExceptionsFile?.file
  }

  companion object {
    const val BUILTIN_MODULE: String = "__builtin__"
    const val BUILTIN_MODULE_3K: String = "builtins"
    const val TYPES_MODULE: String = "types"
    const val TYPESHED_MODULE: String = "_typeshed"
    private const val EXCEPTIONS_MODULE = "exceptions"

    private val DUD_INSTANCE = PyBuiltinCache()
    private val INTERPRETER_FOR_FILE_KEY = Key.create<CachedValue<PythonInterpreter?>>("Python.interpreterForFile")

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
          val interpreter = findInterpreterForFile(reference.containingFile)
          if (interpreter != null) {
            return PythonSdkPathCache.getInstance(reference.project, interpreter).builtins
          }
        }
        catch (_: PsiInvalidElementAccessException) {
        }
      }
      return DUD_INSTANCE // a non-functional fail-fast instance, for a case when skeletons are not available
    }

    /**
     * The interpreter that resolves the names of [psiFile]: the interpreter of its module, or for a file outside every
     * module, see [findInterpreterForNonModuleFile].
     */
    @JvmStatic
    fun findInterpreterForFile(psiFile: PsiFileSystemItem?): PythonInterpreter? {
      psiFile ?: return null
      return CachedValuesManager.getCachedValue(psiFile, INTERPRETER_FOR_FILE_KEY) {
        val project = psiFile.project
        val module = findModuleForPsiElementCached(psiFile)
        val interpreter = if (module != null) module.pythonInterpreterIfReady() else findInterpreterForNonModuleFile(psiFile)
        CachedValueProvider.Result.create(
          interpreter,
          PsiModificationTracker.MODIFICATION_COUNT,
          ProjectRootManager.getInstance(project),
          PyResolveCachesInvalidator.getInstance(project).modificationTracker,
        )
      }
    }

    /**
     * The interpreter for [psiFile] outside every module: the main interpreter, or the interpreter of the library that
     * holds the file.
     */
    fun findInterpreterForNonModuleFile(psiFile: PsiFileSystemItem): PythonInterpreter? {
      val vfile: VirtualFile?
      if (psiFile is PsiFile) {
        val contextFile = FileContextUtil.getContextFile(psiFile)
        vfile = contextFile?.originalFile?.virtualFile
      }
      else {
        vfile = psiFile.virtualFile
      }
      var interpreter: PythonInterpreter? = null
      if (vfile != null) { // reality
        val projectRootManager = ProjectRootManager.getInstance(psiFile.project)
        // A Python console is served by `PythonRuntimeService.getConsoleInterpreter` together with `visitAllModules`, so
        // answering here would hand it a second, competing interpreter (PY-89831).
        if (PythonRuntimeService.getInstance().getConsoleInterpreter(psiFile) == null) {
          interpreter = psiFile.project.mainPythonInterpreter()
        }
        if (interpreter == null) {
          val orderEntries = projectRootManager.fileIndex.getOrderEntriesForFile(vfile)
          for (orderEntry in orderEntries) {
            // A module names its interpreter by one carrier, and the platform loads the other too, see
            // isIgnoredInterpreterEntry.
            if (orderEntry == null || orderEntry.isIgnoredInterpreterEntry()) continue
            if (orderEntry is JdkOrderEntry) {
              interpreter = orderEntry.jdk?.let { psiFile.project.findPythonInterpreterIfReady(it) }
            }
            // The interpreter library of a module carries the roots of its interpreter, as its SDK does.
            else if (OrderEntryUtil.isModuleLibraryOrderEntry(orderEntry) || orderEntry.isInterpreterLibraryCarrier()) {
              interpreter = orderEntry.ownerModule.pythonInterpreterIfReady()
            }
          }
        }
      }
      return interpreter
    }

    /** [getBuiltinsForInterpreter] for the interpreter that the registry of [project] holds for [sdk]. */
    @Deprecated("Use getBuiltinsForInterpreter", ReplaceWith("getBuiltinsForInterpreter(project, interpreter)"))
    @JvmStatic
    fun getBuiltinsForSdk(project: Project, sdk: Sdk): PyFile? =
      project.findPythonInterpreterIfReady(sdk)?.let { getBuiltinsForInterpreter(project, it) }

    @JvmStatic
    fun getBuiltinsForInterpreter(project: Project, interpreter: PythonInterpreter): PyFile? {
      val moduleName = getBuiltinsModuleName(interpreter.languageLevel)
      return getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(moduleName))
    }

    @JvmStatic
    fun getBuiltinsFileName(level: LanguageLevel): String {
      return getBuiltinsModuleName(level) + PyNames.DOT_PY
    }

    @JvmStatic
    fun getBuiltinsModuleName(level: LanguageLevel): String {
      return if (level.isPython2) BUILTIN_MODULE else BUILTIN_MODULE_3K
    }

    private fun getFileForInterpreter(project: Project, interpreter: PythonInterpreter, moduleName: QualifiedName): PyFile? {
      val results = resolveQualifiedName(moduleName, fromInterpreter(project, interpreter))
      return results.firstOrNull()?.let { PyUtil.turnDirIntoInit(it) } as? PyFile
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

private class CachedFile(
  val file: PyFile,
  private var myModificationStamp: Long = -1,
  /**
   * Stores the most often used types, returned by nNNType.
   */
  private val myTypeCache: MutableMap<String, PyClassType?> = mutableMapOf(),
) {
  fun getClassType(name: @NonNls String): PyClassType? {
    return synchronized(myTypeCache) {
      if (myModificationStamp != file.modificationStamp) {
        myTypeCache.clear()
        myModificationStamp = file.modificationStamp
      }
      myTypeCache[name]
        ?.also { it.assertValid(name) }
    } ?: resolveTopLevel(name)?.also {
      synchronized(myTypeCache) {
        myTypeCache[name] = it
      }
    }
  }

  fun resolveTopLevel(name: @NonNls String): PyClassType? {
    return file.findTopLevelClass(name)?.let { pyClass -> PyClassTypeImpl(pyClass, false) }
  }
}

private fun CachedFile?.isAbsentOrValid(): Boolean = this == null || file.isValid()

private fun PyFile?.toCachedFile() =
  this?.let { CachedFile(this) }
