// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.util.io.delete
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This script was implemented to sync local copy of `typeshed` with bundled `typeshed`.
 *
 * As a result it leaves top-level modules and packages that are listed in `whiteList`.
 * It allows us to reduce the size of bundled `typeshed` and do not run indexing and other analyzing processes on disabled stubs.
 *
 * @see [com.jetbrains.python.tools.splitBuiltins]
 */

val repo: Path = Paths.get("../../../../../../../../../typeshed").abs().normalize()
val bundled: Path = Paths.get("../../../../../../../../community/python/helpers/typeshed").abs().normalize()

println("Repo: ${repo.abs()}")
println("Bundled: ${bundled.abs()}")

println("Syncing")
sync(repo, bundled)

val whiteList = sequenceOf(
  "__builtin__",
  "__future__",
  //"_ast", leads to broken tests but could be enabled
  "_codecs",
  "_compression",
  "_csv",
  "_curses",
  "_dummy_threading",
  "_heapq",
  "_imp",
  "_importlib_modulespec",
  "_io",
  "_json",
  "_operator",
  "_random",
  "_thread",
  "_types",
  "_warnings",
  "abc",
  "antigravity",
  "argparse",
  "array",
  "ast",
  "asyncio",
  "asyncore",
  "attr",
  "audioop",
  "bdb",
  "binascii",
  "builtins",
  "cgi",
  "cmath",
  "cmd",
  "codecs",
  "collections",
  "concurrent",
  "configparser",
  "contextvars",
  "cPickle",
  "crypt",
  "Crypto",
  "cryptography",
  "csv",
  "ctypes",
  "curses",
  "datetime",
  //"datetimerange", leads to broken tests but could be enabled
  "dateutil",
  "dbm",
  "decimal",
  "difflib",
  "distutils",
  "doctest",
  "email",
  "exceptions",
  "faulthandler",
  "fcntl",
  "filecmp",
  //"formatter", leads to broken tests but could be enabled
  "functools",
  "gc",
  "genericpath",
  "gettext",
  "gflags",
  "hashlib",
  "heapq",
  "http",
  "imaplib",
  "imghdr",
  "importlib",
  "inspect",
  "io",
  "ipaddress",
  "itertools",
  "json",
  "locale",
  "logging",
  "macpath",
  "marshal",
  "math",
  "mimetypes",
  "mock",
  "modulefinder",
  "multiprocessing",
  "ntpath",
  "numbers",
  "opcode",
  "operator",
  //"optparse", deprecated
  "orjson",
  "os",
  "os2emxpath",
  "parser",
  "pathlib",
  "pathlib2",
  "pdb",
  "pickle",
  //"platform", leads to broken tests but could be enabled
  "plistlib",
  "posix",
  "posixpath",
  "pprint",
  "pstats",
  "py_compile",
  "pydoc",
  "pyexpat",
  "queue",
  "re",
  "requests",
  "resource",
  "select",
  "selectors",
  "shutil",
  "signal",
  "six",
  "socket",
  "socketserver",
  "sqlite3",
  "sre_constants",
  "sre_parse",
  "ssl",
  "statistics",
  //"string", leads to broken tests but could be enabled
  "struct",
  "subprocess",
  "sys",
  "tarfile",
  "tempfile",
  "termios",
  "textwrap",
  "this",
  "threading",
  "time",
  "token",
  "tokenize",
  "turtle",
  "types",
  "typing",
  "typing_extensions",
  "unittest",
  "urllib",
  "uu",
  "uuid",
  "venv",
  "warnings",
  "webbrowser",
  "werkzeug",
  "winsound",
  "xml",
  "zipapp",
  "zipfile",
  "zipimport",
  "zlib"
).mapTo(hashSetOf()) { it.toLowerCase() }

println("Cleaning")
cleanTopLevelPackages(bundled, whiteList)

println("Splitting builtins")
splitBuiltins(bundled)

fun sync(repo: Path, bundled: Path) {
  if (!Files.exists(repo)) throw IllegalArgumentException("Not found: ${repo.abs()}")

  if (Files.exists(bundled)) {
    bundled.delete()
    println("Removed: ${bundled.abs()}")
  }

  val whiteList = setOf("stdlib",
                        "tests",
                        "third_party",
                        ".flake8",
                        ".gitignore",
                        ".travis.yml",
                        "CONTRIBUTING.md",
                        "LICENSE",
                        "pyproject.toml",
                        "README.md",
                        "requirements-tests-py3.txt")

  Files
    .newDirectoryStream(repo)
    .forEach {
      if (it.name() in whiteList) {
        val target = bundled.resolve(it.fileName)

        it.copyRecursively(target)
        println("Copied: ${it.abs()} to ${target.abs()}")
      }
      else {
        println("Skipped: ${it.abs()}")
      }
    }
}

fun cleanTopLevelPackages(typeshed: Path, whiteList: Set<String>) {
  val blackList = mutableSetOf<String>()

  sequenceOf(typeshed)
    .flatMap { sequenceOf(it.resolve("stdlib"), it.resolve("third_party")) }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .filter {
      val name = it.nameWithoutExtension().toLowerCase()

      if (name !in whiteList) {
        blackList.add(name)
        true
      }
      else {
        false
      }
    }
    .forEach { it.delete() }

  println("White list size: ${whiteList.size}")
  println("Black list size: ${blackList.size}")
}

fun Path.abs() = toAbsolutePath()
fun Path.copyRecursively(target: Path) = toFile().copyRecursively(target.toFile())
fun Path.name() = toFile().name
fun Path.nameWithoutExtension() = toFile().nameWithoutExtension
