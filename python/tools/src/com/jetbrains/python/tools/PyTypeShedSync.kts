// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.util.io.delete
import com.jetbrains.python.psi.LanguageLevel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This script was implemented to sync local copy of `typeshed` with bundled `typeshed`.
 *
 * As a result it skips top-level modules and packages that are listed in `blacklist`.
 * It allows us to reduce the size of bundled `typeshed` and do not run indexing and other analyzing processes on disabled stubs.
 */

val repo: Path = Paths.get("../../../../../../../../../typeshed").abs().normalize()
val bundled: Path = Paths.get("../../../../../../../../community/python/helpers/typeshed").abs().normalize()

println("Repo: ${repo.abs()}")
println("Bundled: ${bundled.abs()}")

println("Syncing")
sync(repo, bundled)

val blacklist = sequenceOf(
  "_collections",
  "_decimal",
  "_functools",
  "_hotshot",
  "_markupbase",
  "_md5",
  "_osx_support",
  "_posixsubprocess",
  "_pydecimal",
  "_sha",
  "_sha256",
  "_sha512",
  "_socket",
  "_sre",
  "_stat",
  "_struct",
  "_symtable",
  "_threading_local",
  "_weakref",
  "_weakrefset",
  "asynchat",
  "atexit",
  "backports",
  "backports_abc",
  "basehttpserver",
  "binhex",
  "bisect",
  "bleach",
  "boto",
  "calendar",
  "certifi",
  "cgihttpserver",
  "cgitb",
  "characteristic",
  "chunk",
  "click",
  "code",
  "codeop",
  "colorsys",
  "commands",
  "cookie",
  "cookielib",
  "copy",
  "copy_reg",
  "copyreg",
  "croniter",
  "cstringio",
  "dataclasses",
  "dateparser",
  "decorator",
  "dircache",
  "dis",
  "docutils",
  "emoji",
  "encodings",
  "ensurepip",
  "enum",
  "errno",
  "fb303",
  "fileinput",
  "first",
  "flask",
  "fnmatch",
  "future_builtins",
  "geoip2",
  "getopt",
  "getpass",
  "glob",
  "protobuf",
  "grp",
  "gzip",
  "html",
  "htmlentitydefs",
  "htmlparser",
  "httplib",
  "imp",
  "itsdangerous",
  "jinja2",
  "kazoo",
  "lib2to3",
  "linecache",
  "macurl2path",
  "mailbox",
  "mailcap",
  "markupbase",
  "markupsafe",
  "maxminddb",
  "md5",
  "mimetools",
  "msvcrt",
  "mutex",
  "mypy-extensions",
  "netrc",
  "nis",
  "nntplib",
  "nturl2path",
  "openssl-python",
  "optparse", // deprecated
  "pickletools",
  "popen2",
  "poplib",
  "profile",
  "pty",
  "pwd",
  "pyclbr",
  "pycurl",
  "pymssql",
  "pymysql",
  "pytz",
  "pyvmomi",
  "quopri",
  "readline",
  "redis",
  "repr",
  "reprlib",
  "rfc822",
  "rlcompleter",
  "robotparser",
  "routes",
  "runpy",
  "sched",
  "scribe",
  "secrets",
  "sets",
  "sha",
  "shelve",
  "shlex",
  "simplehttpserver",
  "simplejson",
  "singledispatch",
  "site",
  "smtpd",
  "smtplib",
  "sndhdr",
  "spwd",
  "sre_compile",
  "stat",
  "stringio",
  "stringold",
  "stringprep",
  "strop",
  "symbol",
  "symtable",
  "sysconfig",
  "syslog",
  "tabnanny",
  "tabulate",
  "telnetlib",
  "termcolor",
  "timeit",
  "tkinter",
  "toaiff",
  "toml",
  "tornado",
  "trace",
  "traceback",
  "tty",
  "ujson",
  "unicodedata",
  "urllib2",
  "user",
  "userdict",
  "userlist",
  "userstring",
  "weakref",
  "whichdb",
  "xdrlib",
  "xmlrpclib",
  "xxlimited", // not available in runtime
  "PyYAML"
).mapTo(hashSetOf()) { it.toLowerCase() }

println("Cleaning")
cleanTopLevelPackages(bundled, blacklist)

println("Processing stdlib/VERSIONS")
printStdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels(bundled, blacklist)

fun sync(repo: Path, bundled: Path) {
  if (!Files.exists(repo)) throw IllegalArgumentException("Not found: ${repo.abs()}")

  if (Files.exists(bundled)) {
    bundled.delete()
    println("Removed: ${bundled.abs()}")
  }

  val whiteList = setOf(".github",
                        "scripts",
                        "stdlib",
                        "stubs",
                        "tests",
                        ".flake8",
                        ".gitignore",
                        "CONTRIBUTING.md",
                        "LICENSE",
                        "pre-commit",
                        "pyproject.toml",
                        "pyrightconfig.json",
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

fun cleanTopLevelPackages(typeshed: Path, blackList: Set<String>) {
  val whiteList = hashSetOf<String>()

  sequenceOf(typeshed)
    .flatMap { sequenceOf(it.resolve("stdlib"), it.resolve("stdlib/@python2"), it.resolve("stubs")) }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .filter {
      val name = it.nameWithoutExtension().toLowerCase()

      if (name in blackList) {
        true
      }
      else {
        whiteList.add(name)
        false
      }
    }
    .forEach { it.delete() }

  println("White list size: ${whiteList.size}")
  println("Black list size: ${blackList.size}")
}

fun printStdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels(repo: Path, blackList: Set<String>) {
  val lowestPython2 = LanguageLevel.PYTHON27.toPythonVersion()
  val lowestPython3 = LanguageLevel.PYTHON36.toPythonVersion()

  val lines = Files
    .readAllLines(repo.resolve("stdlib/VERSIONS"))
    .map { it.split(": ", limit = 2) }

  lines.filter { it.size == 2 }
    .filter { it.first() !in blackList }
    .filter { it.last().let { pythonVersion -> pythonVersion != lowestPython2 && pythonVersion != lowestPython3 } }
    .forEach { println("${it.first()}, ${it.last()}") }

  lines.filter { it.size != 2 }.forEach { println("WARN: malformed line: ${it.first()}") }
}

fun Path.abs() = toAbsolutePath()
fun Path.copyRecursively(target: Path) = toFile().copyRecursively(target.toFile())
fun Path.name() = toFile().name
fun Path.nameWithoutExtension() = toFile().nameWithoutExtension
