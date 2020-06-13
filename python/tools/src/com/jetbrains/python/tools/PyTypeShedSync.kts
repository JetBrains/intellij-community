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

val blacklist = sequenceOf(
  "_ast", // leads to broken tests but could be enabled
  "_collections",
  "_decimal",
  "_dummy_thread",
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
  "contextlib",
  "cookie",
  "cookielib",
  "copy",
  "copy_reg",
  "copyreg",
  "cprofile",
  "croniter",
  "cstringio",
  "dataclasses",
  "dateparser",
  "datetimerange", // leads to broken tests but could be enabled
  "decorator",
  "dircache",
  "dis",
  "docutils",
  "dummy_thread",
  "dummy_threading",
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
  "formatter", // leads to broken tests but could be enabled
  "fractions",
  "future_builtins",
  "geoip2",
  "getopt",
  "getpass",
  "glob",
  "google",
  "grp",
  "gzip",
  "html",
  "htmlentitydefs",
  "htmlparser",
  "httplib",
  "imp",
  "itsdangerous",
  "jinja2",
  "jwt",
  "kazoo",
  "keyword",
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
  "mypy_extensions",
  "netrc",
  "nis",
  "nntplib",
  "nturl2path",
  "openssl",
  "optparse", // deprecated
  "pickletools",
  "pipes",
  "pkgutil",
  "platform", // leads to broken tests but could be enabled
  "popen2",
  "poplib",
  "profile",
  "pty",
  "pwd",
  "pyclbr",
  "pycurl",
  "pymssql",
  "pymysql",
  "pynamodb",
  "pyre_extensions",
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
  "string", // leads to broken tests but could be enabled
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
  "thread",
  "timeit",
  "tkinter",
  "toaiff",
  "toml",
  "tornado",
  "trace",
  "traceback",
  "tty",
  "typed_ast",
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
  "yaml",
  "zoneinfo"
).mapTo(hashSetOf()) { it.toLowerCase() }

println("Cleaning")
cleanTopLevelPackages(bundled, blacklist)

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

fun cleanTopLevelPackages(typeshed: Path, blackList: Set<String>) {
  val whiteList = hashSetOf<String>()

  sequenceOf(typeshed)
    .flatMap { sequenceOf(it.resolve("stdlib"), it.resolve("third_party")) }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
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

fun Path.abs() = toAbsolutePath()
fun Path.copyRecursively(target: Path) = toFile().copyRecursively(target.toFile())
fun Path.name() = toFile().name
fun Path.nameWithoutExtension() = toFile().nameWithoutExtension
