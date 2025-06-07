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
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.psi.LanguageLevel
import java.io.File

/**
 * Utilities for managing the local copy of the typeshed repository.
 *
 * The original Git repo is located [here](https://github.com/JetBrains/typeshed).
 *
 */
object PyTypeShed {

  private val stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels = mapOf(
    // name to python versions when this name was introduced and removed
    "_bootlocale" to (LanguageLevel.PYTHON36 to LanguageLevel.PYTHON39),
    "_dummy_thread" to (LanguageLevel.PYTHON36 to LanguageLevel.PYTHON38),
    "_dummy_threading" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON38),
    "_interpchannels" to (LanguageLevel.PYTHON313 to null),
    "_interpqueues" to (LanguageLevel.PYTHON313 to null),
    "_interpreters" to (LanguageLevel.PYTHON313 to null),
    "_py_abc" to (LanguageLevel.PYTHON37 to null),
    "aifc" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "asynchat" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON311),
    "asyncio.exceptions" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "asyncio.format_helpers" to (LanguageLevel.PYTHON37 to null),  // likely it is ignored now
    "asyncio.mixins" to (LanguageLevel.PYTHON310 to null),  // likely it is ignored now
    "asyncio.runners" to (LanguageLevel.PYTHON37 to null),  // likely it is ignored now
    "asyncio.staggered" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "asyncio.taskgroups" to (LanguageLevel.PYTHON311 to null),  // likely it is ignored now
    "asyncio.threads" to (LanguageLevel.PYTHON39 to null),  // likely it is ignored now
    "asyncio.timeouts" to (LanguageLevel.PYTHON311 to null),  // likely it is ignored now
    "asyncio.trsock" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "asyncore" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON311),
    "audioop" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "binhex" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON310),
    "cgi" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "cgitb" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "chunk" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "contextvars" to (LanguageLevel.PYTHON37 to null),
    "crypt" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "dataclasses" to (LanguageLevel.PYTHON37 to null),
    "dbm.sqlite3" to (LanguageLevel.PYTHON313 to null),
    "distutils" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON311),
    "distutils.command.bdist_msi" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON310),  // likely it is ignored now
    "distutils.command.bdist_wininst" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),  // likely it is ignored now
    "dummy_threading" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON38),
    "formatter" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),
    "graphlib" to (LanguageLevel.PYTHON39 to null),
    "imghdr" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "imp" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON311),
    "importlib._abc" to (LanguageLevel.PYTHON310 to null),
    "importlib.metadata" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "importlib.metadata._meta" to (LanguageLevel.PYTHON310 to null),  // likely it is ignored now
    "importlib.metadata.diagnose" to (LanguageLevel.PYTHON313 to null),
    "importlib.readers" to (LanguageLevel.PYTHON310 to null),
    "importlib.resources" to (LanguageLevel.PYTHON37 to null),  // likely it is ignored now
    "importlib.resources.abc" to (LanguageLevel.PYTHON311 to null),
    "importlib.resources.readers" to (LanguageLevel.PYTHON311 to null),
    "importlib.resources.simple" to (LanguageLevel.PYTHON311 to null),
    "importlib.simple" to (LanguageLevel.PYTHON311 to null),
    "lib2to3" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "mailcap" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "msilib" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "multiprocessing.resource_tracker" to (LanguageLevel.PYTHON38 to null), // likely it is ignored now
    "multiprocessing.shared_memory" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "nis" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "nntplib" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "ossaudiodev" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "parser" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),
    "pipes" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "smtpd" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON311),
    "sndhdr" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "spwd" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "sunau" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "symbol" to (LanguageLevel.PYTHON27 to LanguageLevel.PYTHON39),
    "sys._monitoring" to (LanguageLevel.PYTHON312 to null),
    "telnetlib" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "tkinter.tix" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "tomllib" to (LanguageLevel.PYTHON311 to null),
    "unittest._log" to (LanguageLevel.PYTHON39 to null),  // likely it is ignored now
    "unittest.async_case" to (LanguageLevel.PYTHON38 to null),  // likely it is ignored now
    "uu" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "wsgiref.types" to (LanguageLevel.PYTHON311 to null),  // likely it is ignored now
    "xdrlib" to (LanguageLevel.PYTHON30 to LanguageLevel.PYTHON312),
    "zipfile._path" to (LanguageLevel.PYTHON312 to null),
    "zoneinfo" to (LanguageLevel.PYTHON39 to null)
  ) // Modified by script 2024-07-31 07:30:25

  // mapping of definitions that are in the incorrect place in typeshed
  val typingRedirections: Map<String, String> = mapOf(
    "typing.Hashable" to "_collections_abc.Hashable",
    "typing.Awaitable" to "_collections_abc.Awaitable",
    "typing.Coroutine" to "_collections_abc.Coroutine",
    "typing.AsyncIterable" to "_collections_abc.AsyncIterable",
    "typing.AsyncIterator" to "_collections_abc.AsyncIterator",
    "typing.Iterable" to "_collections_abc.Iterable",
    "typing.Iterator" to "_collections_abc.Iterator",
    "typing.Reversible" to "_collections_abc.Reversible",
    "typing.Sized" to "_collections_abc.Sized",
    "typing.Container" to "_collections_abc.Container",
    "typing.Collection" to "_collections_abc.Collection",
    "typing.Callable" to "_collections_abc.Callable",
    "typing.AbstractSet" to "_collections_abc.Set",
    "typing.MutableSet" to "_collections_abc.MutableSet",
    "typing.Mapping" to "_collections_abc.Mapping",
    "typing.MutableMapping" to "_collections_abc.MutableMapping",
    "typing.Sequence" to "_collections_abc.Sequence",
    "typing.MutableSequence" to "_collections_abc.MutableSequence",
    "typing.ByteString" to "_collections_abc.ByteString",
    "typing.Deque" to "_collections.deque",
    "typing.MappingView" to "_collections_abc.MappingView",
    "typing.KeysView" to "_collections_abc.KeysView",
    "typing.ItemsView" to "_collections_abc.ItemsView",
    "typing.ValuesView" to "_collections_abc.ValuesView",
    "typing.DefaultDict" to "_collections.defaultdict",
    "typing.Generator" to "_collections_abc.Generator",
    "typing.AsyncGenerator" to "_collections_abc.AsyncGenerator"
  )

  val typeshedModuleRedirections: Map<String, String> = mapOf(
    "collections.abc" to "_collections_abc",
  )

  /**
   * Returns true if we allow to search typeshed for a stub for [name].
   */
  fun maySearchForStubInRoot(name: QualifiedName, root: VirtualFile, sdk: Sdk): Boolean {
    if (isInStandardLibrary(root)) {
      val head = name.firstComponent ?: return true
      val languageLevels = stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels[head] ?: return true
      val currentLanguageLevel = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk)
      return currentLanguageLevel.isAtLeast(languageLevels.first) && languageLevels.second.let {
        it == null || it.isAtLeast(currentLanguageLevel)
      }
    }
    return isInThirdPartyLibraries(root)
  }

  /**
   * Returns the stdlib roots in Typeshed for the Python language level of [sdk].
   *
   * For Python 2, there are two entries: typeshed/stdlib and typeshed/stdlib/@python2.
   */
  fun findStdlibRootsForSdk(sdk: Sdk): List<VirtualFile> {
    val level = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk)
    return findStdlibRootsForLanguageLevel(level)
  }

  /**
   * Returns the stdlib roots in Typeshed for the specified Python language [level].
   *
   * For Python 2, there are two entries: typeshed/stdlib and typeshed/stdlib/@python2.
   */
  fun findStdlibRootsForLanguageLevel(level: LanguageLevel): List<VirtualFile> {
    val dir = directory ?: return emptyList()

    val stdlib = dir.findChild("stdlib") ?: return emptyList()
    return if (level.isPython2) listOfNotNull(stdlib.findChild("@python2"), stdlib) else listOf(stdlib)
  }

  /**
   * Returns both stdlib and third-party stub roots in Typeshed for the specified Python language [level].
   */
  fun findAllRootsForLanguageLevel(level: LanguageLevel): List<VirtualFile> {
    // We no longer include Python 2 stubs for third-party packages, only for stdlib
    return findStdlibRootsForLanguageLevel(level) + thirdPartyStubRoot?.children?.toList().orEmpty()
  }

  /**
   * Checks if the [file] is located inside the typeshed directory.
   */
  fun isInside(file: VirtualFile): Boolean {
    val dir = directory
    return dir != null && VfsUtilCore.isAncestor(dir, file, true)
  }

  /**
   * The actual typeshed directory.
   */
  val directory: VirtualFile? by lazy {
    val path = directoryPath ?: return@lazy null
    StandardFileSystems.local().findFileByPath(path)
  }

  val thirdPartyStubRoot: VirtualFile? by lazy {
    directory?.findChild("stubs")
  }

  private val directoryPath: String?
    get() {
      val paths = listOf("${PathManager.getConfigPath()}/typeshed",
                         "${PathManager.getConfigPath()}/../typeshed",
                         PythonHelpersLocator.findPathStringInHelpers("typeshed"))
      return paths.asSequence()
          .filter { File(it).exists() }
          .firstOrNull()
    }

  /**
   * A shallow check for a [file] being located inside the typeshed third-party stubs.
   */
  fun isInThirdPartyLibraries(file: VirtualFile): Boolean = "stubs" in file.path

  fun isInStandardLibrary(file: VirtualFile): Boolean = "stdlib" in file.path

  /**
   * Find the directory containing .pyi stubs for the package [packageName] under `typeshed/stubs`.
   *
   * [packageName] should match the name of the package on PyPI.
   */
  fun getStubRootForPackage(packageName: String): VirtualFile? {
    return thirdPartyStubRoot?.findChild(packageName)
  }
}