/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.path.EelPath
import com.intellij.terminal.backend.hyperlinks.TerminalHyperlinkFilterContextImpl
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFileHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.filter.FILENAME_MAX
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalGenericFileFilter
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.random.Random

internal class TerminalGenericFileFilterAbsolutePathTest {

  private val project = mock(Project::class.java)

  @Test
  fun `lonely slashes are not highlighted`() =
    getFilterResultAndCheckHighlightPositions("hello / world, hello \\ world", listOf(),false).checkFileLinks()

  @Test
  fun `slash-only strings are not highlighted`() {
    val slashStrings = listOf(
      "/",
      "//",
      "///",
      "////",
      "\\",
      "\\\\",
      "\\\\\\",
      "\\\\\\\\",
      "/\\",
      "\\/",
      "//\\\\",
    )

    for (slashString in slashStrings) {
      getFilterResultAndCheckHighlightPositions(
        "hello $slashString world",
        emptyList(),
        checkHighlights = false
      ).checkFileLinks()
    }

    getFilterResultAndCheckHighlightPositions("/// @param", emptyList(), false).checkFileLinks()
    getFilterResultAndCheckHighlightPositions("// comment", emptyList(), false).checkFileLinks()
  }

  @Test
  fun `short names are highlighted`() =
    getFilterResultAndCheckHighlightPositions("hello /a world", listOf("/a"),false)
      .checkFileLinks("/a")

  @Test
  fun `short Windows names are highlighted`() =
    getFilterResultAndCheckHighlightPositions("hello C:\\ C:\\b world C:\\d", listOf("C:\\", "C:\\b"), false, windows = true)
      .checkFileLinks("C:\\", "C:\\b")

  @Test
  fun `honor FILENAME_MAX for performance reasons`() {
    val longString = RandomStringUtils.secure().nextAlphanumeric(FILENAME_MAX + 1)

    val run = getFilterResultAndCheckHighlightPositions("/$longString /path/to/file", listOf("/path/to/file"), checkHighlights = false)
    run.checkFileLinks("/path/to/file")
    Assertions.assertThat(run.lookedUpPaths).doesNotContain(run.path("/$longString"))
  }

  @Test
  fun `honor FILENAME_MAX for performance reasons (with spaces)`() {
    val p1 = RandomStringUtils.secure().nextAlphanumeric(FILENAME_MAX / 2 + 1)
    val p2 = RandomStringUtils.secure().nextAlphanumeric(FILENAME_MAX / 2 + 1)
    assert(p1.length + p2.length > FILENAME_MAX)

    val run = getFilterResultAndCheckHighlightPositions("/$p1 $p2 /path/to/file", listOf("/path/to/file"), checkHighlights = false)
    run.checkFileLinks("/path/to/file")
    Assertions.assertThat(run.lookedUpPaths).doesNotContain(run.path("/$p1 $p2"))
  }

  @Test
  fun `nonexisting paths cancel early`() =
    getFilterResultAndCheckHighlightPositions("This /is/not/a path /path/to/file", listOf("/path/to/file"), checkHighlights = false)
      .checkFileLinks("/path/to/file")

  @Test
  fun `path without spaces is looked up once, not per segment`() {
    val run = getFilterResultAndCheckHighlightPositions("blah blah /path/to/file", listOf("/path/to/file"), checkHighlights = false)
    run.checkFileLinks("/path/to/file")
    Assertions.assertThat(run.lookedUpPaths).containsExactly(run.path("/path/to/file"))
  }

  @Test
  fun `nonexisting path followed by prose is not looked up per word`() {
    val run = getFilterResultAndCheckHighlightPositions(
      "This /is/not/a path with more words /path/to/file", listOf("/path/to/file"), checkHighlights = false
    )
    run.checkFileLinks("/path/to/file")
    // "/is/not/a", then its directory "/is/not" (which proves that no continuation can exist), then "/path/to/file"
    Assertions.assertThat(run.lookedUpPaths).containsExactly(run.path("/is/not/a"), run.path("/is/not"), run.path("/path/to/file"))
  }

  @Test
  fun `URL followed by prose is looked up a bounded number of times`() {
    val run = getFilterResultAndCheckHighlightPositions(
      "see https://example.com/docs/setup for more information about the setup", emptyList(), checkHighlights = false
    )
    run.checkFileLinks()
    Assertions.assertThat(run.lookedUpPaths).containsExactly(run.path("//example.com/docs/setup"), run.path("//example.com/docs"))
  }

  @Test
  fun `path with space in the first segment is not cancelled early`() =
    getFilterResultAndCheckHighlightPositions("blah /with space blah", listOf("/with space"), checkHighlights = false)
      .checkFileLinks("/with space")

  @Test
  fun `Windows path with space in the first segment is not cancelled early`() =
    getFilterResultAndCheckHighlightPositions("blah C:\\with space blah", listOf("C:\\with space"), checkHighlights = false, windows = true)
      .checkFileLinks("C:\\with space")

  @Test
  fun `recognize Windows path after a nonexisting file in an existing directory`() {
    // "C:\path\to" exists but "missing" does not, so the parser keeps going, since
    // the file name may contain spaces. The ':' branch then restarts it
    // at the second "C:".
    val run = getFilterResultAndCheckHighlightPositions(
      "Cannot open C:\\path\\to\\missing C:\\path\\to\\file", listOf("C:\\path\\to\\file"), checkHighlights = false, windows = true
    )
    run.checkFileLinks("C:\\path\\to\\file")
    Assertions.assertThat(run.lookedUpPaths).contains(run.path("C:\\path\\to\\missing"), run.path("C:\\path\\to"))
  }

  @Test
  fun `Windows path is not recognized in a Posix environment`() =
    getFilterResultAndCheckHighlightPositions("Cannot open C:\\path\\to\\file", listOf("/path/to/file"), checkHighlights = false)
      .checkFileLinks()

  @Test
  fun `recognize path with space in a middle segment when the prefix before the space does not exist`() =
    getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/my dir/file blah blah
                ^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/my dir/file"))
    .checkFileLinks("/path/to/my dir/file")

  @Test
  fun `recognize path at the end of a line without a line break`() =
    getFilterResultAndCheckHighlightPositions("blah blah /path/to/file", listOf("/path/to/file"), checkHighlights = false, lineBreak = "")
      .checkFileLinks("/path/to/file")

  @Test
  fun `recognize Windows path at the end of a line without a line break`() =
    getFilterResultAndCheckHighlightPositions("blah blah C:\\path\\to\\file", listOf("C:\\path\\to\\file"), checkHighlights = false, windows = true, lineBreak = "")
      .checkFileLinks("C:\\path\\to\\file")

  @Test
  fun `take longest path with spaces at the end of a line without a line break`() =
    getFilterResultAndCheckHighlightPositions("In folder /work projects 2", listOf("/work projects", "/work projects 2"), checkHighlights = false, lineBreak = "")
      .checkFileLinks("/work projects 2")

  @Test
  fun `nonexisting path at the end of a line is not looked up again with the line break`() {
    val run = getFilterResultAndCheckHighlightPositions("blah blah /is/not/a", emptyList(), checkHighlights = false)
    run.checkFileLinks()
    // Looked up at the line break; the end-of-line check must not repeat it with the line break included.
    Assertions.assertThat(run.lookedUpPaths).containsExactly(run.path("/is/not/a"), run.path("/is/not"))
  }

  @Test
  fun `recognize simple Linux path`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file
      ^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file"))
    .checkFileLinks("/path/to/file")

  @Test
  fun `recognize simple Windows path`() = getFilterResultAndCheckHighlightPositions("""
    | C:\path\to\file
      ^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("""C:\path\to\file"""), windows = true)
    .checkFileLinks("""C:\path\to\file""")

  @Test
  fun `recognize simple Windows path with forward slashes`() = getFilterResultAndCheckHighlightPositions("""
    | C:/path/to/file
      ^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("""C:/path/to/file"""), windows = true)
    .checkFileLinks("""C:/path/to/file""")

  @Test
  fun `recognize path with line number`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:3
      ^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.c"))
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `a huge line number is not a part of link`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:99999999999999999
      ^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.c"))
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path with line number and column`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:3:7
      ^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.c"))
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path in middle of a line`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file blah blah
                ^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file"))
    .checkFileLinks("/path/to/file")

  @Test
  fun `recognize multiple paths in a line`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file blah blah /another/path/to/file
                ^^^^^^^^^^^^^           ^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file", "/another/path/to/file"))
    .checkFileLinks(
      "/path/to/file",
      "/another/path/to/file"
    )

  @Test
  fun `recognize path with line number and column in parenthesis`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.kt: (3, 7): No value passed for parameter 'silent'
      ^^^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.kt"))
    .checkFileLinks("/path/to/file.kt")

  @Test
  fun `recognize path with space`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file/with space blah blah
                ^^^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file/with space"))
    .checkFileLinks("/path/to/file/with space")

  @Test
  fun `recognize path with space and numbers`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file/with space:3:7 blah blah
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file/with space"))
    .checkFileLinks("/path/to/file/with space")

  @Test
  fun `recognize path with space and numbers parenthesis Windows`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah C:\path\to\file\with space.kt: (3, 7): blah blah
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("""C:\path\to\file\with space.kt"""), windows = true)
    .checkFileLinks("""C:\path\to\file\with space.kt""")

  @Test
  fun `multiple lines and multiple links`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file1 blah blah /path/to/file2:3.
                ^^^^^^^^^^^^^^           ^^^^^^^^^^^^^^^^
    | blah blah blah /path/to/file3:1:2: error: blah blah /path/to/file4
                     ^^^^^^^^^^^^^^^^^^                   ^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file1", "/path/to/file2", "/path/to/file3", "/path/to/file4"))
    .checkFileLinks(
      "/path/to/file1",
      "/path/to/file2",
      "/path/to/file3",
      "/path/to/file4"
    )

  @Test
  fun `skip nonexisting files`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/existing/file and /path/to/missing/file
      ^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/existing/file"))
    .checkFileLinks(
        "/path/to/existing/file"
      )

  @Test
  fun `ignore slashes from progress indicators`() = getFilterResultAndCheckHighlightPositions("""
      | [1,234 / 5,678] Doing Something Important
  """.trimIndent(), listOf())
      .checkFileLinks()

  @Test
  fun `take longest path when prefix with spaces exists`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah C:\path\to\file\with space\and no more.kt: (5, 71): blah blah
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    | blah blah C:\path\to\file\with space and more.kt: (3, 7): blah blah
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    """.trimIndent(), listOf("""C:\path\to\file\with space\and no more.kt""", """C:\path\to\file\with space and more.kt"""), windows = true)
      .checkFileLinks(
        """C:\path\to\file\with space\and no more.kt""",
        """C:\path\to\file\with space and more.kt""",
      )

  @Test
  fun `real world case 1`() = getFilterResultAndCheckHighlightPositions("""
    | FAILURE: Build failed with an exception.
    |
    | * What went wrong:
    | Execution failed for task ':app:externalNativeBuildDebug'.
    | > Build command failed.
    |   Error while executing process /usr/local/google/home/tgeng/Android/Sdk/cmake/3.10.2.4988404/bin/ninja with arguments
    |     {-C /usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/.cxx/cmake/debug/armeabi-v7a native-lib}
    |   ninja: Entering directory `/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/.cxx/cmake/debug/armeabi-v7a'
    |   [1/2] Building CXX object CMakeFiles/native-lib.dir/native-lib.cpp.o
    |   FAILED: CMakeFiles/native-lib.dir/native-lib.cpp.o
    |   /usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++
    |     --target=armv7-none-linux-androideabi19
    |     --gcc-toolchain=/usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64
                          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |     ... -c /usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp
                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |   /usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp:21:5: error: use of undeclared identifier 'yoo'
    |   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |       yoo;
    |       ^
    | 1 error generated.
    | ninja: build stopped: subcommand failed.
  """.trimIndent(), listOf(
    "/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp",
    "/usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64"
  )).checkFileLinks(
    "/usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64",
    "/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp",
    "/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp"
  )

  @Test
  fun `real world case 2`() {
    getFilterResultAndCheckHighlightPositions("""
    | CMake Error at /Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt:156 (include):
                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |   include could not find load file:
    |
    |     /Users/jomof/projects/GunBox/GunBox/ExternalLibraries/__cmake/ExternalLibraries.cmake
          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |
    | CMake Error at /Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt:156 (include):
                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |   include could not find load file:
    """.trimIndent(), listOf(
      "/Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt",
      "/Users/jomof/projects/GunBox/GunBox/ExternalLibraries/__cmake/ExternalLibraries.cmake",
    ))
      .checkFileLinks(
        "/Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt",
        "/Users/jomof/projects/GunBox/GunBox/ExternalLibraries/__cmake/ExternalLibraries.cmake",
        "/Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt"
      )
  }

  @Test
  fun `recognize path with line range`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:10-20
      ^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.c"))
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path with single digit line range`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:1-5
      ^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.c"))
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path with line range at end of sentence`() = getFilterResultAndCheckHighlightPositions("""
    | error in /path/to/file.c:10-20.
               ^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file.c"))
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path with line range in middle of text`() = getFilterResultAndCheckHighlightPositions("""
    | see /path/to/file:5-10 for details
          ^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file"))
    .checkFileLinks("/path/to/file")

  @Test
  fun `recognize Windows path with line range`() = getFilterResultAndCheckHighlightPositions("""
    | C:\path\to\file.txt:10-14
      ^^^^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("""C:\path\to\file.txt"""), windows = true)
    .checkFileLinks("""C:\path\to\file.txt""")

  @Test
  fun `recognize multiple paths with line ranges`() = getFilterResultAndCheckHighlightPositions("""
    | Compare /path/to/file1:1-5 with /path/to/file2:10-20
              ^^^^^^^^^^^^^^^^^^      ^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file1", "/path/to/file2"))
    .checkFileLinks("/path/to/file1", "/path/to/file2")

  @Test
  fun `recognize mixed formats with line ranges and line column`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file1:10-20 and /path/to/file2:5:10
      ^^^^^^^^^^^^^^^^^^^^     ^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf("/path/to/file1", "/path/to/file2"))
    .checkFileLinks("/path/to/file1", "/path/to/file2")

  @Test
  fun `malformed line range without end line falls back to start line`() =
    getFilterResultAndCheckHighlightPositions("error in /path/to/file:10- see details\n", listOf("/path/to/file"), checkHighlights = false)
      .checkFileLinks("/path/to/file")

  @Test
  fun `malformed line range with non-numeric end falls back to start line`() =
    getFilterResultAndCheckHighlightPositions("error in /path/to/file:10-abc see details\n", listOf("/path/to/file"), checkHighlights = false)
      .checkFileLinks("/path/to/file")

  @Test
  fun `real world case for bug 167701951`() =getFilterResultAndCheckHighlightPositions("""
    | C:\android\Android studio Projects\AppManager\app\src\main\res\values-zh-rCN\strings.xml:427:4 Error: always_light
      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    | C:\android\Android studio\not_cached
    | when executing C:\android\Android studio\bin\studio.exe
                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    | In folder C:\android\Android studio
                ^^^^^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent(), listOf(
    """C:\android\Android studio Projects\AppManager\app\src\main\res\values-zh-rCN\strings.xml""",
    """C:\android\Android studio\bin\studio.exe"""
  ), windows = true).checkFileLinks(
    """C:\android\Android studio Projects\AppManager\app\src\main\res\values-zh-rCN\strings.xml""",
    """C:\android\Android studio\bin\studio.exe""",
    """C:\android\Android studio"""
  )

  @Test
  fun `fuzz test`() {
    val linuxPaths = mutableListOf<String>()

    val wordGen = oneOf(('a'..'z').asIterable() + ".-_()[]吃葡萄不吐葡萄皮".asIterable()).repeated(3..8)
    val linuxPathGen = ('/' + wordGen).repeated(2..10)
      .useGenerated { linuxPaths += it }
    // Windows paths are not absolute in a Posix environment and must not become links.
    val windowsPathGen = (oneOf('A'..'Z') + ":" + ('\\' + wordGen).repeated(2..10))
    val fileNumberGen = ':' + someInt()
    val pathWithLineNumberGen = oneOf(linuxPathGen, windowsPathGen) + fileNumberGen
    val pathWithLineAndColumnNumberGen = pathWithLineNumberGen + fileNumberGen
    val sentenceGen = oneOf(
      wordGen.repeated(1..10, " "),
      oneOf(linuxPathGen, windowsPathGen, pathWithLineNumberGen, pathWithLineAndColumnNumberGen)
    ).repeated(1..5, " ")
    val paragraphGen = sentenceGen.repeated(50..60, "\n")

    getFilterResultAndCheckHighlightPositions(Random.paragraphGen(), linuxPaths, checkHighlights = false)
      .checkFileLinks(*linuxPaths.toTypedArray())
  }

  // Home-relative path tests

  @Test
  fun `recognize home-relative path`() =
    getFilterResultAndCheckHighlightPositions("""
      | ~/IdeaProjects/file
        ^^^^^^^^^^^^^^^^^^^
    """.trimIndent(), listOf("/Users/testuser/IdeaProjects/file"), homeDirectory = "/Users/testuser")
      .checkFileLinks("/Users/testuser/IdeaProjects/file")

  @Test
  fun `recognize home-relative path with line and column`() =
    getFilterResultAndCheckHighlightPositions("""
      | ~/IdeaProjects/file.kt:3:7
        ^^^^^^^^^^^^^^^^^^^^^^^^^^
    """.trimIndent(), listOf("/Users/testuser/IdeaProjects/file.kt"), homeDirectory = "/Users/testuser")
      .checkFileLinks("/Users/testuser/IdeaProjects/file.kt")

  @Test
  fun `recognize home-relative path with backslash separator`() =
    getFilterResultAndCheckHighlightPositions("""
      | ~\IdeaProjects\file
        ^^^^^^^^^^^^^^^^^^^
    """.trimIndent(), listOf("""C:\Users\testuser\IdeaProjects\file"""), windows = true, homeDirectory = """C:\Users\testuser""")
      .checkFileLinks("""C:\Users\testuser\IdeaProjects\file""")

  @Test
  fun `recognize multiple home-relative paths in a line`() =
    getFilterResultAndCheckHighlightPositions(
      "Compare ~/foo with ~/bar",
      listOf("/Users/testuser/foo", "/Users/testuser/bar"),
      checkHighlights = false,
      homeDirectory = "/Users/testuser",
    ).checkFileLinks("/Users/testuser/foo", "/Users/testuser/bar")

  @Test
  fun `home-relative path is not resolved without a filter context`() =
    getFilterResultAndCheckHighlightPositions("~/IdeaProjects/file", emptyList(), checkHighlights = false)
      .checkFileLinks()

  @Test
  fun `tilde not followed by a separator is not treated as a home path`() =
    getFilterResultAndCheckHighlightPositions("~notahome and a lonely ~", emptyList(), checkHighlights = false, homeDirectory = "/Users/testuser")
      .checkFileLinks()

  @Test
  fun `recognize bare home directory`() =
    getFilterResultAndCheckHighlightPositions("""
      | ~/
        ^^
    """.trimIndent(), listOf("/Users/testuser/"), homeDirectory = "/Users/testuser")
      .checkFileLinks("/Users/testuser/")

  @Test
  fun `recognize bare home directory in the middle of a sentence`() =
    getFilterResultAndCheckHighlightPositions("cd ~/ to go home", listOf("/Users/testuser/"), checkHighlights = false, homeDirectory = "/Users/testuser")
      .checkFileLinks("/Users/testuser/")

  @Test
  fun `recognize home-relative path with line number and column in parenthesis`() =
    getFilterResultAndCheckHighlightPositions(
      "~/IdeaProjects/file.kt: (3, 7): No value passed for parameter 'silent'",
      listOf("/Users/testuser/IdeaProjects/file.kt"),
      checkHighlights = false,
      homeDirectory = "/Users/testuser",
    ).checkFileLinks("/Users/testuser/IdeaProjects/file.kt")

  @Test
  fun `recognize home-relative path with space`() =
    getFilterResultAndCheckHighlightPositions(
      "blah blah ~/IdeaProjects/with space blah blah",
      listOf("/Users/testuser/IdeaProjects/with space"),
      checkHighlights = false,
      homeDirectory = "/Users/testuser",
    ).checkFileLinks("/Users/testuser/IdeaProjects/with space")

  @Test
  fun `recognize home-relative path with line range`() =
    getFilterResultAndCheckHighlightPositions(
      "~/IdeaProjects/file.c:10-20",
      listOf("/Users/testuser/IdeaProjects/file.c"),
      checkHighlights = false,
      homeDirectory = "/Users/testuser",
    ).checkFileLinks("/Users/testuser/IdeaProjects/file.c")

  @Test
  fun `real world case with mixed absolute and home-relative paths`() {
    val content = "Downloading dependency to ~/.m2/repository/com/example/lib-1.0.jar\n" +
                  "Build failed, see /var/log/build.log for details"

    getFilterResultAndCheckHighlightPositions(
      content,
      listOf("/Users/testuser/.m2/repository/com/example/lib-1.0.jar", "/var/log/build.log"),
      checkHighlights = false,
      homeDirectory = "/Users/testuser",
    ).checkFileLinks("/Users/testuser/.m2/repository/com/example/lib-1.0.jar", "/var/log/build.log")
  }

  /** The results of one filter run and the fake file tree it ran against. */
  private class FilterRun(private val results: List<Filter.Result>, private val lookup: FakeTerminalFileLookup) {
    val lookedUpPaths: List<EelPath>
      get() = lookup.lookedUpPaths

    fun path(path: String): EelPath = lookup.path(path)

    fun checkFileLinks(vararg paths: String) {
      val actualPaths = results.flatMap { it.resultItems }.map { (it.hyperlinkInfo as TerminalFileHyperlinkInfo).path }
      Assertions.assertThat(actualPaths).isEqualTo(paths.map { path(it) })
    }
  }

  /**
   * Applies the filter to every line of [content] that starts with `| `. Unless
   * [checkHighlights] is `false`, the highlighted ranges are checked against the `^`
   * marks of the following line. Only [validPaths] and their parent directories exist.
   */
  private fun getFilterResultAndCheckHighlightPositions(
    content: String,
    validPaths: Collection<String>,
    checkHighlights: Boolean = true,
    windows: Boolean = false,
    homeDirectory: String? = null,
    lineBreak: String = "\n",
  ): FilterRun {
    val descriptor = if (windows) TestEelDescriptor.WINDOWS else TestEelDescriptor.POSIX
    val lookup = FakeTerminalFileLookup(descriptor)
    for (path in validPaths) {
      lookup.addFile(path)
    }
    val context = homeDirectory?.let { TerminalHyperlinkFilterContextImpl(descriptor, lookup.path(it)) }
    val filter = TerminalGenericFileFilter(project, descriptor, context, lookup)

    var totalLength = 0
    var previousInputLine = ""
    var previousInputStartIndex = 0
    val results = mutableListOf<Filter.Result?>()

    content.lines().forEach { line ->
      if (!checkHighlights || line.startsWith('|')) {
        val inputLine = line.removePrefix("| ") + lineBreak
        previousInputLine = inputLine
        previousInputStartIndex = totalLength
        totalLength += inputLine.length
        results += filter.applyFilter(inputLine, totalLength)
      }
      else {
        val actualHighlighted = results.last()!!.resultItems.map { item ->
          previousInputLine.substring(item.highlightStartOffset - previousInputStartIndex,
                                      item.highlightEndOffset - previousInputStartIndex)
        }
        val indicatorLine = line.removePrefix("  ")
        val expectedHighlighted = previousInputLine
          .mapIndexed { i, c -> if (indicatorLine.getOrNull(i) == '^') c else '%' }
          .joinToString("")
          .split(Regex("%+"))
          .filter { it.isNotEmpty() }
        Assertions.assertThat(actualHighlighted).isEqualTo(expectedHighlighted)
      }
    }
    return FilterRun(results.filterNotNull(), lookup)
  }
}

/** Creates a generator function that chooses one of the options from the given iterable randomly. */
fun <T> oneOf(options: Iterable<T>): Random.() -> T = { options.toList().let { it[nextInt(it.size)] } }

/** Creates a generator function that delegate a randomly chosen generator from the given options. */
fun <T> oneOf(vararg options: Random.() -> T): Random.() -> T = { options[nextInt(options.size)]() }

/** Creates a generator that randomly generates an integer. */
fun someInt(range: IntRange = 1..100): Random.() -> Int = { range.random(this) }

/** Creates a generator that invokes the given generator some random number of times and concatenate the [toString] values. */
fun <T> (Random.() -> T).repeated(times: IntRange = 0..10, separator: String = ""): Random.() -> String = {
  (0 until times.random(this)).joinToString(separator) { this@repeated().toString() }
}

/** Creates a generator that delegates the given generator and apply some side effects to the passed in consumer. */
fun <T> (Random.() -> T).useGenerated(consumer: (T) -> Unit): Random.() -> T = { this@useGenerated().also(consumer) }

/** Creates a generator that invokes the given two generators and concatenate their results together as strings. */
operator fun <T> (Random.() -> T).plus(that: Random.() -> T): Random.() -> String = { this@plus().toString() + that().toString() }

/** Creates a generator that invokes the given generator and append its result with a given object as string. */
operator fun <T> (Random.() -> T).plus(that: Any): Random.() -> String = { this@plus().toString() + that.toString() }

/** Creates a generator that invokes the given generator and prepend its result with a given object as string. */
operator fun <T> Any.plus(that: Random.() -> T): Random.() -> String = { this@plus.toString() + that() }
