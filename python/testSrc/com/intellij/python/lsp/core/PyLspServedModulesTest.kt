// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.lsp.core.utils.PyLspToolVersionTracker
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.python.ty.TyPyTool
import org.eclipse.lsp4j.ConfigurationItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * One server answers for several modules, so the served set and its order carry weight. The platform
 * builds the server identity from the root paths in order, and the first served module runs the tool
 * binary.
 */
@TestApplication
@TestFor(issues = ["PY-92008"])
internal class PyLspServedModulesTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)

  /** Any per-folder tool serves; the mock interpreters hold no package, so every version is `null`. */
  private val tyTool = TyPyTool.getInstance()

  /** A key of the project's own workspace, so only the version tells two modules apart. */
  private fun ownWorkspace(version: String?) = PyLspServeKey(projectPath.get().toString(), version)

  @Nested
  inner class Selection {
    private val withSdkPath = projectPath.subdirectory("b_has_sdk")
    private val noSdkPath = projectPath.subdirectory("a_no_sdk")
    private val withSdk = projectFixture.pyModuleFixture(withSdkPath, addPathToSourceRoot = true)
    private val noSdk = projectFixture.pyModuleFixture(noSdkPath, addPathToSourceRoot = true)
    private val sdk = projectFixture.pyMockSdkFixture(withSdk) { PythonMockSdk.create() }
    private val noRoots = projectFixture.pyModuleFixture("rootless")

    @Test
    fun `only a module with a local interpreter is served`() {
      sdk.get()
      noSdk.get()

      val served = computePyLspServedModules(projectFixture.get())

      assertEquals(listOf(withSdk.get()), served)
    }

    @Test
    fun `a module without a content root is not served, because it gives no workspace folder`() {
      sdk.get()
      noRoots.get()

      assertTrue(noRoots.get() !in computePyLspServedModules(projectFixture.get()))
    }
  }

  @Nested
  inner class Ordering {
    private val firstPath = projectPath.subdirectory("aaa_root")
    private val secondPath = projectPath.subdirectory("zzz_root")
    private val first = projectFixture.pyModuleFixture(firstPath, addPathToSourceRoot = true)
    private val second = projectFixture.pyModuleFixture(secondPath, addPathToSourceRoot = true)

    /**
     * The platform builds the server identity from the root paths in order, so the roots must not
     * depend on the order of the served modules. Otherwise renaming a module reorders them, the
     * identity changes, and the next start request adds a second server for the same folders.
     */
    @Test
    fun `the roots do not depend on the order of the served modules`() {
      val ascending = tyDescriptorServing(first.get(), second.get())
      val descending = tyDescriptorServing(second.get(), first.get())

      assertEquals(ascending.roots.map { it.path }, descending.roots.map { it.path })
    }

    @Test
    fun `the roots are ordered by path`() {
      val roots = tyDescriptorServing(second.get(), first.get()).roots.map { it.path }

      assertEquals(roots.sorted(), roots)
    }

    @Test
    fun `a served module contributes every content root exactly once`() {
      val roots = tyDescriptorServing(first.get(), second.get()).roots.map { it.path }

      assertEquals(roots.distinct(), roots)
      assertTrue(roots.any { it == first.get().rootPath() })
      assertTrue(roots.any { it == second.get().rootPath() })
    }
  }

  /**
   * `Attach to project` loads another project as a module of this one, so its content root sits
   * outside the project directory. Such a module is a workspace of its own and needs a server of its
   * own: one server for both trees would hold two unrelated folder sets, and every attach and detach
   * would restart it for every module.
   */
  @Nested
  @TestFor(issues = ["PY-92008"])
  inner class Workspaces {
    private val insidePath = projectPath.subdirectory("aaa_inside")
    private val alsoInsidePath = projectPath.subdirectory("mmm_also_inside")
    private val attachedPath = tempPathFixture(prefix = "zzz_attached")
    private val inside = projectFixture.pyModuleFixture(insidePath, addPathToSourceRoot = true)
    private val alsoInside = projectFixture.pyModuleFixture(alsoInsidePath, addPathToSourceRoot = true)
    private val attached = projectFixture.pyModuleFixture(attachedPath, addPathToSourceRoot = true)
    // A mock SDK takes its name from the language level, and the SDK table rejects two of one name.
    private val insideSdk = projectFixture.pyMockSdkFixture(inside) { PythonMockSdk.create(LanguageLevel.PYTHON39) }
    private val alsoInsideSdk = projectFixture.pyMockSdkFixture(alsoInside) { PythonMockSdk.create(LanguageLevel.PYTHON310) }
    private val attachedSdk = projectFixture.pyMockSdkFixture(attached) { PythonMockSdk.create(LanguageLevel.PYTHON311) }

    private fun allSdks() {
      insideSdk.get()
      alsoInsideSdk.get()
      attachedSdk.get()
    }

    @Test
    fun `a module inside the project directory belongs to the project workspace`() {
      allSdks()

      assertEquals(projectPath.get().toString(), pyLspWorkspaceRootOf(inside.get()))
      assertEquals(projectPath.get().toString(), pyLspWorkspaceRootOf(alsoInside.get()))
    }

    @Test
    fun `an attached module is a workspace of its own`() {
      allSdks()

      assertEquals(attached.get().rootPath(), pyLspWorkspaceRootOf(attached.get()))
    }

    @Test
    fun `the modules of the project share a server, and the attached module gets its own`() {
      allSdks()

      val ownServer = listOf(inside.get(), alsoInside.get())
      assertEquals(ownServer, pyLspModulesToServeWith(inside.get(), tyTool))
      assertEquals(ownServer, pyLspModulesToServeWith(alsoInside.get(), tyTool))
      assertEquals(listOf(attached.get()), pyLspModulesToServeWith(attached.get(), tyTool))
    }

    @Test
    fun `a module takes the tool version of its own workspace, not of the other one`() {
      allSdks()
      val served = listOf(inside.get(), alsoInside.get(), attached.get())
      // The first module of each workspace holds no copy of the tool, so it takes the version of the
      // next module of its own workspace.
      val keys = mapOf(
        inside.get() to PyLspServeKey(projectPath.get().toString(), null),
        alsoInside.get() to PyLspServeKey(projectPath.get().toString(), "1.1.1"),
        attached.get() to PyLspServeKey(attached.get().rootPath(), null),
      )

      assertEquals(listOf(inside.get(), alsoInside.get()), pyLspServeGroupOf(inside.get(), served) { keys.getValue(it) })
      assertEquals(listOf(attached.get()), pyLspServeGroupOf(attached.get(), served) { keys.getValue(it) })
    }

    @Test
    fun `a server for each workspace is fresh, not stale`() {
      allSdks()
      val served = listOf(inside.get(), alsoInside.get(), attached.get())
      val descriptors = listOf(
        tyDescriptorServing(inside.get(), alsoInside.get()),
        tyDescriptorServing(attached.get()),
      )

      assertFalse(pyLspFolderSetIsStale(descriptors, served) { pyLspServeKeyOf(it, tyTool) })
    }

    @Test
    fun `a server holding both workspaces is stale`() {
      allSdks()
      val served = listOf(inside.get(), alsoInside.get(), attached.get())
      val descriptor = tyDescriptorServing(inside.get(), alsoInside.get(), attached.get())

      assertTrue(pyLspFolderSetIsStale(listOf(descriptor), served) { pyLspServeKeyOf(it, tyTool) })
    }
  }

  @Nested
  inner class Scope {
    private val mainPath = tempPathFixture()
    private val otherPath = tempPathFixture()
    private val main = projectFixture.pyModuleFixture(mainPath, addPathToSourceRoot = true)
    private val other = projectFixture.pyModuleFixture(otherPath, addPathToSourceRoot = true)

    @Test
    fun `a scope uri resolves to the served module that owns that content root`() {
      val descriptor = tyDescriptorServing(main.get(), other.get())
      val otherRoot = descriptor.roots.single { it.path == other.get().rootPath() }

      assertEquals(other.get(), descriptor.servedModuleForScope(itemFor(descriptor.getFileUri(otherRoot))))
    }

    @Test
    fun `an item with no scope resolves to no module, so the primary module answers`() {
      val descriptor = tyDescriptorServing(main.get(), other.get())

      assertNull(descriptor.servedModuleForScope(ConfigurationItem()))
    }

    @Test
    fun `a scope uri outside every served root resolves to no module`() {
      val descriptor = tyDescriptorServing(main.get())
      val unservedRoot = descriptor.getFileUri(
        VirtualFileManager.getInstance().refreshAndFindFileByNioPath(otherPath.get())!!)

      assertNull(descriptor.servedModuleForScope(itemFor(unservedRoot)))
    }

    /**
     * A server may echo a folder back in another form than the one it got. The module must still
     * answer, or the primary module answers with its own interpreter for that folder.
     */
    @Test
    @TestFor(issues = ["PY-92008"])
    fun `a scope uri with a trailing slash resolves to the module that owns that root`() {
      val descriptor = tyDescriptorServing(main.get(), other.get())
      val otherRoot = descriptor.roots.single { it.path == other.get().rootPath() }

      assertEquals(other.get(), descriptor.servedModuleForScope(itemFor(descriptor.getFileUri(otherRoot) + "/")))
    }

    private fun itemFor(scopeUri: String) = ConfigurationItem().also { it.scopeUri = scopeUri }
  }

  @Nested
  inner class SharedServer {
    private val firstPath = projectPath.subdirectory("aaa_root")
    private val secondPath = projectPath.subdirectory("zzz_root")
    private val first = projectFixture.pyModuleFixture(firstPath, addPathToSourceRoot = true)
    private val second = projectFixture.pyModuleFixture(secondPath, addPathToSourceRoot = true)
    private val firstSdk = projectFixture.pyMockSdkFixture(first) { PythonMockSdk.create(LanguageLevel.PYTHON312) }
    private val secondSdk = projectFixture.pyMockSdkFixture(second) { PythonMockSdk.create(LanguageLevel.PYTHON313) }
    private val noSdk = projectFixture.pyModuleFixture(projectPath.subdirectory("mmm_no_sdk"), addPathToSourceRoot = true)

    @Test
    fun `every served module goes to one server, the lowest root first`() {
      firstSdk.get()
      secondSdk.get()

      assertEquals(listOf(first.get(), second.get()), pyLspModulesToServeWith(second.get(), tyTool))
    }

    @Test
    @TestFor(issues = ["PY-92008"])
    fun `the type engine registry key does not decide how many servers run`() {
      firstSdk.get()
      secondSdk.get()

      assertFalse(PyTypeEngineUtils.isMultiModuleSupportEnabled)
      assertEquals(listOf(first.get(), second.get()), pyLspModulesToServeWith(second.get(), tyTool))
    }

    @Test
    fun `a module the shared server cannot serve stays alone`() {
      firstSdk.get()
      secondSdk.get()

      assertEquals(listOf(noSdk.get()), pyLspModulesToServeWith(noSdk.get(), tyTool))
    }
  }

  /**
   * One server runs one binary, so a module whose environment pins another version of the tool needs
   * a server of its own. A module that holds no copy of the tool states no version and joins the
   * version of the first served module that does.
   */
  @Nested
  @TestFor(issues = ["PY-92008"])
  inner class VersionGroups {
    private val firstPath = projectPath.subdirectory("aaa_root")
    private val secondPath = projectPath.subdirectory("mmm_root")
    private val thirdPath = projectPath.subdirectory("zzz_root")
    private val first = projectFixture.pyModuleFixture(firstPath, addPathToSourceRoot = true)
    private val second = projectFixture.pyModuleFixture(secondPath, addPathToSourceRoot = true)
    private val third = projectFixture.pyModuleFixture(thirdPath, addPathToSourceRoot = true)

    private fun served() = listOf(first.get(), second.get(), third.get())

    @Test
    fun `one version for every module gives one server`() {
      val served = served()

      for (module in served) {
        assertEquals(served, pyLspServeGroupOf(module, served) { ownWorkspace("1.1.1") })
      }
    }

    @Test
    fun `no module holds the tool, so one server runs whatever the path provides`() {
      val served = served()

      assertEquals(served, pyLspServeGroupOf(served.first(), served) { ownWorkspace(null) })
    }

    @Test
    fun `a module that pins another version gets a server of its own`() {
      val served = served()
      val versions = mapOf(served[0] to "1.1.1", served[1] to "0.40.0", served[2] to "1.1.1")

      assertEquals(listOf(served[0], served[2]), pyLspServeGroupOf(served[0], served) { ownWorkspace(versions[it]) })
      assertEquals(listOf(served[1]), pyLspServeGroupOf(served[1], served) { ownWorkspace(versions[it]) })
      assertEquals(listOf(served[0], served[2]), pyLspServeGroupOf(served[2], served) { ownWorkspace(versions[it]) })
    }

    @Test
    fun `a module without the tool joins the version of the lowest root that has it`() {
      val served = served()
      val versions = mapOf(served[1] to "0.40.0", served[2] to "1.1.1")

      // The first module holds no copy, so it runs the version of the second, the lowest root with one.
      assertEquals(listOf(served[0], served[1]), pyLspServeGroupOf(served[0], served) { ownWorkspace(versions[it]) })
      assertEquals(listOf(served[2]), pyLspServeGroupOf(served[2], served) { ownWorkspace(versions[it]) })
    }

    @Test
    fun `each served module lands in exactly one group`() {
      val served = served()
      val versions = mapOf(served[0] to "1.1.1", served[1] to "0.40.0")

      val groups = served.map { pyLspServeGroupOf(it, served) { m -> ownWorkspace(versions[m]) } }.distinct()

      assertEquals(served.size, groups.sumOf { it.size })
      assertEquals(served.toSet(), groups.flatten().toSet())
    }
  }

  /**
   * The folder listener restarts a tool's clients only when [pyLspFolderSetIsStale] says so. A wrong
   * `true` restarts a server on every roots change. A wrong `false` leaves a stale folder set running
   * and lets the next start request add a second server.
   */
  @Nested
  inner class FolderSet {
    private val mainPath = projectPath.subdirectory("aaa_main")
    private val otherPath = projectPath.subdirectory("zzz_other")
    private val main = projectFixture.pyModuleFixture(mainPath, addPathToSourceRoot = true)
    private val other = projectFixture.pyModuleFixture(otherPath, addPathToSourceRoot = true)
    private val unserved = projectFixture.pyModuleFixture(projectPath.subdirectory("mmm_unserved"), addPathToSourceRoot = true)

    /** Every module runs the same version, so the project wants one server for all of them. */
    private val oneVersion: (Module) -> PyLspServeKey = { ownWorkspace("1.1.1") }

    @Test
    fun `a shared server is stale once only one module is served`() {
      val descriptor = tyDescriptorServing(main.get(), other.get())

      assertTrue(pyLspFolderSetIsStale(listOf(descriptor), listOf(main.get()), oneVersion))
    }

    @Test
    fun `a server for one served module is stale once the project wants one server for all`() {
      val served = listOf(main.get(), other.get())
      val descriptor = tyDescriptorServing(main.get())

      assertTrue(pyLspFolderSetIsStale(listOf(descriptor), served, oneVersion))
    }

    @Test
    fun `a shared server with the wanted folders is fresh`() {
      val served = listOf(main.get(), other.get())
      val descriptor = tyDescriptorServing(main.get(), other.get())

      assertFalse(pyLspFolderSetIsStale(listOf(descriptor), served, oneVersion))
    }

    @Test
    @TestFor(issues = ["PY-92008"])
    fun `a server of its own version group is fresh, not stale`() {
      val served = listOf(main.get(), other.get())
      val versions = mapOf(main.get() to "1.1.1", other.get() to "0.40.0")
      val descriptors = listOf(tyDescriptorServing(main.get()), tyDescriptorServing(other.get()))

      assertFalse(pyLspFolderSetIsStale(descriptors, served) { ownWorkspace(versions[it]) })
    }

    @Test
    @TestFor(issues = ["PY-92008"])
    fun `a shared server is stale once a module pins another version`() {
      val served = listOf(main.get(), other.get())
      val versions = mapOf(main.get() to "1.1.1", other.get() to "0.40.0")
      val descriptor = tyDescriptorServing(main.get(), other.get())

      assertTrue(pyLspFolderSetIsStale(listOf(descriptor), served) { ownWorkspace(versions[it]) })
    }

    /**
     * The primary module is the lowest root, and it can leave the served set while the rest of its
     * group stays. Asking the primary module alone would call this server fresh, and it would keep
     * the folder of a module the project dropped.
     */
    @Test
    @TestFor(issues = ["PY-92008"])
    fun `a shared server is stale once its primary module leaves the served set`() {
      val descriptor = tyDescriptorServing(main.get(), other.get())

      assertTrue(pyLspFolderSetIsStale(listOf(descriptor), listOf(other.get()), oneVersion))
    }

    /** No folder set suits such a server, so it is not stale. The caller stops it instead. */
    @Test
    @TestFor(issues = ["PY-92008"])
    fun `a server for a module the project does not serve is not stale, it serves nothing`() {
      val served = listOf(main.get(), other.get())
      val descriptor = tyDescriptorServing(unserved.get())

      assertFalse(pyLspFolderSetIsStale(listOf(descriptor), served, oneVersion))
      assertTrue(pyLspServesNothing(descriptor, served))
    }

    @Test
    @TestFor(issues = ["PY-92008"])
    fun `a server with one module still served serves something`() {
      val descriptor = tyDescriptorServing(main.get(), other.get())

      assertFalse(pyLspServesNothing(descriptor, listOf(other.get())))
    }
  }

  /**
   * A module whose content root contains the project directory shares a tree with the project. A
   * workspace of its own would give two servers folders that nest, and one tree would be analysed
   * twice.
   */
  @Nested
  @TestFor(issues = ["PY-92008"])
  inner class AncestorWorkspace {
    private val outerPath = tempPathFixture(prefix = "outer")
    private val innerProjectPath = outerPath.subdirectory("inner_project")
    private val innerProject = projectFixture(innerProjectPath, openAfterCreation = true)
    private val ancestor = innerProject.pyModuleFixture(outerPath, addPathToSourceRoot = true)

    @Test
    fun `a module rooted at an ancestor of the project directory belongs to the project workspace`() {
      assertEquals(innerProjectPath.get().toString(), pyLspWorkspaceRootOf(ancestor.get()))
    }
  }

  /**
   * One server runs one binary. Only a module that holds the tool may provide it, and without one the
   * lookup falls back to `PATH` and to `uvx`, which answer the same for every module.
   */
  @Nested
  @TestFor(issues = ["PY-92008"])
  inner class Candidates {
    private val first = projectFixture.pyModuleFixture(projectPath.subdirectory("aaa_root"), addPathToSourceRoot = true)
    private val second = projectFixture.pyModuleFixture(projectPath.subdirectory("zzz_root"), addPathToSourceRoot = true)
    private val firstSdk = projectFixture.pyMockSdkFixture(first) { PythonMockSdk.create(LanguageLevel.PYTHON312) }
    private val secondSdk = projectFixture.pyMockSdkFixture(second) { PythonMockSdk.create(LanguageLevel.PYTHON313) }

    /**
     * Asking every module would probe the environment once per module, and `fileOpened` holds the
     * read lock for each probe. The mock interpreters hold no package, so no module holds the tool.
     */
    @Test
    fun `once the snapshot knows every module and none holds the tool, only the primary module is a candidate`() = runBlocking {
      firstSdk.get()
      secondSdk.get()
      pyLspRefreshServeKeys(projectFixture.get(), tyTool)

      assertEquals(listOf(first.get()), tyDescriptorServing(first.get(), second.get()).executableCandidates())
    }

    @Test
    fun `a fresh snapshot with a holder gives the holders alone`() {
      val (a, b) = first.get() to second.get()
      val view = PyLspServeKeysView(mapOf(a to ownWorkspace(null), b to ownWorkspace("1.1.1")), isFresh = true)

      assertEquals(listOf(b), pyLspExecutableCandidates(listOf(a, b), view))
    }

    @Test
    fun `a fresh snapshot with no holder gives the primary module alone`() {
      val (a, b) = first.get() to second.get()
      val view = PyLspServeKeysView(mapOf(a to ownWorkspace(null), b to ownWorkspace(null)), isFresh = true)

      assertEquals(listOf(a), pyLspExecutableCandidates(listOf(a, b), view))
    }

    /**
     * The second module just got the tool, and the stale snapshot does not say so yet. Asking the
     * primary module alone would miss that binary, and no later event would retry the start.
     */
    @Test
    fun `a stale snapshot keeps every module a candidate`() {
      val (a, b) = first.get() to second.get()
      val view = PyLspServeKeysView(mapOf(a to ownWorkspace(null), b to ownWorkspace(null)), isFresh = false)

      assertEquals(listOf(a, b), pyLspExecutableCandidates(listOf(a, b), view))
    }

    @Test
    fun `a stale snapshot asks the modules it knows hold the tool first`() {
      val (a, b) = first.get() to second.get()
      val view = PyLspServeKeysView(mapOf(a to ownWorkspace(null), b to ownWorkspace("1.1.1")), isFresh = false)

      assertEquals(listOf(b, a), pyLspExecutableCandidates(listOf(a, b), view))
    }

    @Test
    fun `a cold snapshot keeps every module a candidate`() {
      val (a, b) = first.get() to second.get()

      assertEquals(listOf(a, b), pyLspExecutableCandidates(listOf(a, b), PyLspServeKeysView(emptyMap(), isFresh = false)))
    }
  }

  @Nested
  @TestFor(issues = ["PY-92008"])
  inner class VersionTracker {
    /** A shared counter would make a ruff install drop the serve keys of pyrefly for nothing. */
    @Test
    fun `a version change of one tool does not move the counter of another`() {
      val tracker = PyLspToolVersionTracker.getInstance(projectFixture.get())
      val pyreflyBefore = tracker.counterOf("pyrefly")
      val ruffBefore = tracker.counterOf("ruff")

      tracker.bump("ruff")

      assertEquals(pyreflyBefore, tracker.counterOf("pyrefly"))
      assertTrue(tracker.counterOf("ruff") > ruffBefore)
    }
  }

  /**
   * A workspace folder holds its whole tree. A module nested in it that this server does not serve is
   * analysed here as well, with the interpreter of the outer folder, and no answer of it reaches the
   * IDE. Pyrefly takes such roots as `extraProjectExcludes`.
   */
  @Nested
  @TestFor(issues = ["PY-92008"])
  inner class ForeignNestedRoots {
    private val outerPath = projectPath.subdirectory("outer")
    private val nestedPath = outerPath.subdirectory("nested")
    private val outer = projectFixture.pyModuleFixture(outerPath, addPathToSourceRoot = true)
    private val nested = projectFixture.pyModuleFixture(nestedPath, addPathToSourceRoot = true)
    private val inner = projectFixture.pyModuleFixture(nestedPath.subdirectory("inner"), addPathToSourceRoot = true)
    private val globbed = projectFixture.pyModuleFixture(outerPath.subdirectory("glob[bed]"), addPathToSourceRoot = true)
    private val sibling = projectFixture.pyModuleFixture(projectPath.subdirectory("sibling"), addPathToSourceRoot = true)

    @Test
    fun `a nested module the server does not serve is excluded`() {
      val all = listOf(outer.get(), nested.get(), sibling.get())

      assertEquals(listOf(nested.get().rootPath()), pyLspForeignNestedRoots(listOf(outer.get()), all))
    }

    @Test
    fun `a nested module the server serves is not excluded`() {
      val all = listOf(outer.get(), nested.get(), sibling.get())

      assertEquals(emptyList<String>(), pyLspForeignNestedRoots(listOf(outer.get(), nested.get()), all))
    }

    @Test
    fun `a module outside every folder of the server is not excluded`() {
      val all = listOf(outer.get(), sibling.get())

      assertEquals(emptyList<String>(), pyLspForeignNestedRoots(listOf(outer.get()), all))
    }

    /** A tool cannot include a file again once a glob excludes it, so excluding `nested` would drop `inner`. */
    @Test
    fun `a module that holds a folder of the server is not excluded`() {
      val all = listOf(outer.get(), nested.get(), inner.get())

      assertEquals(emptyList<String>(), pyLspForeignNestedRoots(listOf(outer.get(), inner.get()), all))
    }

    /** The descriptor reads every module of the project, so the assertions name only the modules of this case. */
    @Test
    fun `a descriptor excludes the nested modules it does not serve, and nothing outside its folders`() {
      val descriptor = tyDescriptorServing(outer.get())
      nested.get()
      sibling.get()
      runReadActionBlocking { descriptor.refreshProjectExcludes() }

      assertTrue(nested.get().rootPath() in descriptor.projectExcludes())
      assertFalse(sibling.get().rootPath() in descriptor.projectExcludes())
    }

    /** `nested` shows that the excludes were computed, so a missing `globbed` means the filter left it out. */
    @Test
    fun `a root with a glob character is not excluded, because the tool would read it as a pattern`() {
      val descriptor = tyDescriptorServing(outer.get())
      nested.get()
      globbed.get()
      runReadActionBlocking { descriptor.refreshProjectExcludes() }

      assertTrue(nested.get().rootPath() in descriptor.projectExcludes())
      assertFalse(globbed.get().rootPath() in descriptor.projectExcludes())
    }

    /**
     * The LSP listener thread answers `workspace/configuration` and reads the excludes, so it must
     * never compute them. They change only when a caller computes them again, and that caller learns
     * whether to tell the server.
     */
    @Test
    fun `the excludes change only when they are computed again, and the answer says whether they changed`() {
      val descriptor = tyDescriptorServing(outer.get())
      nested.get()

      assertEquals(emptyList<String>(), descriptor.projectExcludes())
      assertTrue(runReadActionBlocking { descriptor.refreshProjectExcludes() })
      assertFalse(runReadActionBlocking { descriptor.refreshProjectExcludes() })
      assertTrue(nested.get().rootPath() in descriptor.projectExcludes())
    }
  }

  private fun Module.rootPath(): String = ModuleRootManager.getInstance(this).contentRoots.single().path

  /** A real descriptor, so the test exercises the production root and scope resolution. */
  private fun tyDescriptorServing(vararg servedModules: Module) =
    TyLspClientDescriptor(servedModules.first(), servedModules.toList())
}

/** A directory named [name] inside this fixture's path. The parent fixture deletes the whole tree. */
private fun TestFixture<Path>.subdirectory(name: String): TestFixture<Path> =
  testFixture(name) {
    val parent = this@subdirectory.init()
    val path = withContext(Dispatchers.IO) { parent.resolve(name).createDirectories() }
    initialized(path) {}
  }
