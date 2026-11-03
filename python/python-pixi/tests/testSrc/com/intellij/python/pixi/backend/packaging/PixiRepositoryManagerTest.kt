// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.cache.firstPageOrEmpty
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.intellij.python.requirements.parser.PyRequirementParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiRepositoryManagerTest {
  private val projectFixture = projectFixture()
  private val pythonRepositoryManager by lazy { PixiTestPythonRepositoryManager(projectFixture.get()) }

  @Test
  fun `native browsing reuses names for repeated pages and substring searches`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val queries = mutableListOf<String>()
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { query ->
      queries.add(query)
      PyResult.success(listOf(
        PixiNativePackage("six", "1.17.0"), PixiNativePackage("python", "3.13.0"), PixiNativePackage("six", "1.16.0"),
      ))
    }.also { Disposer.register(disposable, it) }

    val first = manager.nativeRepository.search("", 1)
    assertThat(first.total).isEqualTo(2)
    assertThat(first.pages[0].contents().getOrThrow()).containsExactly("python")
    assertThat(first.pages[1].contents().getOrThrow()).containsExactly("six")
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("python", "six")
    assertThat(manager.nativeRepository.search("PY", 10).firstPageOrEmpty().getOrThrow()).containsExactly("python")
    assertThat(manager.nativeRepository.search("missing", 10).total).isZero()
    assertThat(queries).containsExactly("*")
  }

  @Test
  fun `native browsing invalidates names when channels or platform change`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var context = PixiPackageSearchContext("linux-64", listOf("first"))
    val queries = mutableListOf<String>()
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, nativeSearchContext = { PyResult.success(context) }) { query ->
      queries.add(query)
      PyResult.success(listOf(PixiNativePackage("${context.channels.single()}_${context.platform}", "1.0")))
    }.also { Disposer.register(disposable, it) }

    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("first_linux-64")
    context = context.copy(channels = listOf("second"))
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("second_linux-64")
    context = context.copy(platform = "win-64")
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("second_win-64")
    assertThat(queries).containsExactly("*", "*", "*")
  }

  @Test
  fun `a native search failure cannot become a cached empty channel`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var searches = 0
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) {
      searches++
      if (searches == 1) PyResult.localizedError("The test channel is unavailable.")
      else PyResult.success(listOf(PixiNativePackage("probe", "1.0")))
    }.also { Disposer.register(disposable, it) }

    assertThat(manager.nativeRepository.search("", 10).total).isZero()
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe")
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe")
    assertThat(searches).isEqualTo(2)
  }

  @Test
  fun `an unresolved native context cannot reuse a previous snapshot`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var contextAvailable = true
    var searches = 0
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, nativeSearchContext = {
      if (contextAvailable) PyResult.success(PixiPackageSearchContext("linux-64", listOf("first")))
      else PyResult.localizedError("The selected environment is unavailable.")
    }) {
      searches++
      PyResult.success(listOf(PixiNativePackage("probe", "1.0")))
    }.also { Disposer.register(disposable, it) }

    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe")
    contextAvailable = false
    assertThat(manager.nativeRepository.search("", 10).total).isZero()
    assertThat(manager.nativeRepository.getSize()).isZero()
    contextAvailable = true
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe")
    assertThat(searches).isEqualTo(2)
  }

  @Test
  fun `native browsing expires the name snapshot`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var now = 0L
    var searches = 0
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, nativeNameCacheClock = { now }) {
      searches++
      PyResult.success(listOf(PixiNativePackage("probe_$searches", "1.0")))
    }.also { Disposer.register(disposable, it) }

    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe_1")
    now += 4.minutes.inWholeNanoseconds
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe_1")
    now += 1.minutes.inWholeNanoseconds
    assertThat(manager.nativeRepository.search("", 10).firstPageOrEmpty().getOrThrow()).containsExactly("probe_2")
    assertThat(searches).isEqualTo(2)
  }

  @Test
  fun `concurrent blank searches share one native snapshot load`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var searches = 0
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) {
      searches++
      entered.complete(Unit)
      release.await()
      PyResult.success(listOf(PixiNativePackage("probe", "1.0")))
    }.also { Disposer.register(disposable, it) }

    val first = async { manager.nativeRepository.search("", 10) }
    entered.await()
    val second = async(start = CoroutineStart.UNDISPATCHED) { manager.nativeRepository.search("", 10) }
    assertThat(searches).isEqualTo(1)
    release.complete(Unit)
    assertThat(first.await().firstPageOrEmpty().getOrThrow()).containsExactly("probe")
    assertThat(second.await().firstPageOrEmpty().getOrThrow()).containsExactly("probe")
    assertThat(searches).isEqualTo(1)
  }

  @Test
  fun `native details retain channel versions when PyPI supplies the description`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, loadNativeDetails = { name ->
      assertThat(name).isEqualTo("six")
      PyResult.success(PythonSimplePackageDetails(
        name, listOf("99.0"), PyPiPackageRepository,
        summary = "The package summary.", description = "The package description.", descriptionContentType = "text/plain",
        documentationUrl = "https://example.org/docs",
      ))
    }) { PyResult.success(listOf(PixiNativePackage("six", "1.17.0"))) }
      .also { Disposer.register(disposable, it) }

    val details = manager.getPackageDetails("six", manager.nativeRepository).getOrThrow()

    assertThat(details.name).isEqualTo("six")
    assertThat(details.availableVersions).containsExactly("1.17.0")
    assertThat(details.repository).isSameAs(manager.nativeRepository)
    assertThat(details.summary).isEqualTo("The package summary.")
    assertThat(details.description).isEqualTo("The package description.")
    assertThat(details.descriptionContentType).isEqualTo("text/plain")
    assertThat(details.documentationUrl).isEqualTo("https://example.org/docs")
    assertThat(details.toPackageSpecification("1.17.0")?.repository).isSameAs(manager.nativeRepository)
  }

  @Test
  fun `a PyPI metadata failure cannot hide native versions`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, loadNativeDetails = {
      PyResult.localizedError("The metadata service is unavailable.")
    }) { PyResult.success(listOf(PixiNativePackage("native_only", "2.0"))) }
      .also { Disposer.register(disposable, it) }

    val result = manager.getPackageDetails("native_only", manager.nativeRepository)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow().availableVersions).containsExactly("2.0")
    assertThat(result.getOrThrow().repository).isSameAs(manager.nativeRepository)
  }

  @Test
  fun `the native repository offers packages before the user types a search`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { query ->
      assertThat(query).isEqualTo("*")
      PyResult.success(listOf(PixiNativePackage("python", "3.13.0"), PixiNativePackage("six", "1.17.0")))
    }.also { Disposer.register(disposable, it) }

    val result = manager.nativeRepository.search("", 50)

    assertThat(result.total).isEqualTo(2)
    assertThat(manager.nativeRepository.getSize()).isEqualTo(2)
    assertThat(result.firstPageOrEmpty().getOrThrow()).containsExactly("python", "six")
  }

  @Test
  fun `native version details recover the installed raw name`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, resolveInstalledPackage = { name ->
      assertThat(name).isEqualTo("raw-name")
      PyResult.success(PixiPackage("raw_name", "0.5", "conda"))
    }) { name ->
      PyResult.success(listOf(PixiNativePackage(name, if (name == "raw_name") "1.0" else "9.0")))
    }.also { Disposer.register(disposable, it) }
    val details = manager.getPackageDetails("raw-name", CondaPackageRepository).getOrThrow()
    assertThat(details.name).isEqualTo("raw_name")
    assertThat(details.availableVersions).containsExactly("1.0")
    val specification = details.toPackageSpecification("1.0")
    assertThat(specification?.requirement?.presentableTextWithoutVersion).isEqualTo("raw_name")
    val popupDetails = manager.getPackageDetails("raw-name", null).getOrThrow()
    assertThat(popupDetails.repository).isSameAs(manager.nativeRepository)
    assertThat(popupDetails.name).isEqualTo("raw_name")
  }

  @Test
  fun `the browser exposes both native and PyPI repositories`(@TestDisposable disposable: Disposable) {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { error("The repository list must not run a search.") }
      .also { Disposer.register(disposable, it) }
    assertThat(manager.allRepositories).contains(manager.nativeRepository, PyPiPackageRepository)
    assertThat(manager.builtInRepositories).containsExactly(manager.nativeRepository, PyPiPackageRepository)
    val projectUrl = manager.nativeRepository.getProjectUrl("typing_extensions")
    assertThat(projectUrl.label).isEqualTo("Anaconda")
    assertThat(projectUrl.url).isEqualTo("https://anaconda.org/anaconda/typing_extensions")
  }

  @Test
  fun `native browser search uses Pixi and preserves distinct Conda names`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { query ->
      assertThat(query).isEqualTo("*typing*")
      PyResult.success(listOf(
        PixiNativePackage("typing_extensions", "4.14.1"),
        PixiNativePackage("typing_extensions", "4.15.0"),
        PixiNativePackage("typing-extensions", "4.15.0"),
        PixiNativePackage("typing_inspection", "0.4.2"),
      ))
    }.also { Disposer.register(disposable, it) }

    val result = manager.searchPackages(CondaPackageRepository, "typing", 2)
    assertThat(result.total).isEqualTo(3)
    assertThat(result.pages).hasSize(2)
    assertThat(result.firstPageOrEmpty().getOrThrow()).containsExactly("typing-extensions", "typing_extensions")
    assertThat(result.pages[1].contents().getOrThrow()).containsExactly("typing_inspection")
  }

  @Test
  fun `native specification lookup does not use the global Conda cache`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { query ->
      assertThat(query).isEqualTo("native_only>=1,<2")
      PyResult.success(listOf(PixiNativePackage("native_only", "1.0")))
    }.also { Disposer.register(disposable, it) }
    val requirement = checkNotNull(PyRequirementParser.fromLine("native_only>=1,<2", projectFixture.get()))
    val specification = manager.findPackageSpecification(requirement, CondaPackageRepository)
    assertThat(specification).isNotNull()
    assertThat(specification?.repository).isSameAs(manager.nativeRepository)
    assertThat(specification?.requirement?.presentableText).isEqualTo(requirement.presentableText)
  }

  @Test
  fun `native details sort and deduplicate versions without the Conda cache`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { name ->
      assertThat(name).isEqualTo("typing_extensions")
      PyResult.success(listOf(
        PixiNativePackage("typing_extensions", "4.9.0"),
        PixiNativePackage("typing_extensions", "4.15.0"),
        PixiNativePackage("typing_extensions", "4.15.0"),
      ))
    }.also { Disposer.register(disposable, it) }

    val result = repository.getPackageDetails("typing_extensions", CondaPackageRepository)
    assertThat(result.isSuccess).isTrue()
    val details = result.getOrThrow()
    assertThat(details.name).isEqualTo("typing_extensions")
    assertThat(details.repository).isSameAs(repository.nativeRepository)
    assertThat(details.availableVersions).containsExactly("4.15.0", "4.9.0")
    val specification = details.toPackageSpecification("4.15.0")
    assertThat(specification).isNotNull()
    assertThat(specification?.repository).isSameAs(repository.nativeRepository)
    assertThat(specification?.requirement?.versionSpecs?.map { it.presentableText }).containsExactly("==4.15.0")
  }

  @Test
  fun `native details report an empty search as a failure`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { PyResult.success(emptyList()) }
      .also { Disposer.register(disposable, it) }
    assertThat(repository.getPackageDetails("missing", CondaPackageRepository).isSuccess).isFalse()
    assertThat(repository.getVersions("missing", CondaPackageRepository)).isNull()
  }

  @Test
  fun `native details preserve a search failure`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val failure = PyResult.localizedError("The test channel is unavailable.")
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { failure }
      .also { Disposer.register(disposable, it) }
    assertThat(repository.getPackageDetails("typing-extensions", CondaPackageRepository)).isSameAs(failure)
  }

  @Test
  fun `fuzzy native results cannot resolve a different package`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) {
      PyResult.success(listOf(PixiNativePackage("typing_extensions", "4.15.0")))
    }.also { Disposer.register(disposable, it) }
    assertThat(repository.getPackageDetails("typing-extensions", CondaPackageRepository).isSuccess).isFalse()
    val requirement = checkNotNull(PyRequirementParser.fromLine("typing-extensions>=4", projectFixture.get()))
    assertThat(repository.findPackageSpecification(requirement, CondaPackageRepository)).isNull()
  }

  @Test
  fun `native details exclude versions from fuzzy matches`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) {
      PyResult.success(listOf(PixiNativePackage("probe_extra", "9.0"), PixiNativePackage("probe", "1.0")))
    }.also { Disposer.register(disposable, it) }
    val details = repository.getPackageDetails("probe", CondaPackageRepository).getOrThrow()
    assertThat(details.name).isEqualTo("probe")
    assertThat(details.availableVersions).containsExactly("1.0")
  }

  @Test
  fun `native version constraints use Pixi instead of Python ordering`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { query ->
      assertThat(query).isEqualTo("probe>1.1a1")
      PyResult.success(listOf(PixiNativePackage("probe", "1.1.0dev1")))
    }.also { Disposer.register(disposable, it) }
    val requirement = checkNotNull(PyRequirementParser.fromLine("probe>1.1a1", projectFixture.get()))
    assertThat(repository.findPackageSpecification(requirement, CondaPackageRepository)).isNotNull()
  }

  @Test
  fun `native details omit versions the dialog cannot preserve`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val repository = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) {
      PyResult.success(listOf("1.1a1", "1.1.0dev1", "1.1.0post1", "1.1post1", "1.0.1_", "2.0", "1.1.post1")
                         .map { PixiNativePackage("probe", it) })
    }.also { Disposer.register(disposable, it) }
    assertThat(repository.getVersions("probe", CondaPackageRepository))
      .containsExactly("2.0", "1.1.post1", "1.1a1")
  }

  @Test
  fun `the update action resolves native specifications without a global cache`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, resolveInstalledPackage = {
        PyResult.success(PixiPackage("native_only", "1.0", "conda"))
      }) { query ->
        assertThat(query).isEqualTo("native_only==2.0")
        PyResult.success(listOf(PixiNativePackage("native_only", "2.0")))
      }.also { Disposer.register(disposable, it) }
      val requirement = checkNotNull(PyRequirementParser.fromLine("native-only==2.0", projectFixture.get()))

      val specification = manager.nativeRepository.findPackageSpecification(requirement)

      assertThat(specification).isNotNull()
      assertThat(specification?.repository).isSameAs(manager.nativeRepository)
      assertThat(specification?.requirement?.presentableText).isEqualTo(requirement.presentableText)
    }

  @Test
  fun `an installed native package cannot be resolved through PyPI`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, resolveInstalledPackage = {
      PyResult.success(PixiPackage("numpy", "1.0", "conda"))
    }) { query ->
      assertThat(query).isEqualTo("numpy==1.0")
      PyResult.success(listOf(PixiNativePackage("numpy", "1.0")))
    }.also { Disposer.register(disposable, it) }
    val requirement = checkNotNull(PyRequirementParser.fromLine("numpy==1.0", projectFixture.get()))
    assertThat(manager.findPackageSpecification(requirement, null)?.repository).isSameAs(manager.nativeRepository)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `installed native specifications keep their source when channels are unavailable`(
    failedSearch: Boolean, @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager, resolveInstalledPackage = { name ->
      PyResult.success(PixiPackage("native_only", "1.0", "conda").takeIf { name == "native-only" })
    }) {
      if (failedSearch) PyResult.localizedError("The test channel is unavailable.") else PyResult.success(emptyList())
    }.also { Disposer.register(disposable, it) }

    for (constraint in listOf("==1.0", ">=2,<3")) {
      val requirement = checkNotNull(PyRequirementParser.fromLine("native-only$constraint", projectFixture.get()))

      val specification = manager.findPackageSpecification(requirement, null)

      assertThat(specification).isNotNull()
      assertThat(specification?.repository).isSameAs(manager.nativeRepository)
      assertThat(specification?.requirement?.presentableText).isEqualTo(requirement.presentableText)
      assertThat(specification?.repository?.getProjectUrl("native_only"))
        .isEqualTo(CondaPackageRepository.getProjectUrl("native_only"))
    }
    val missing = checkNotNull(PyRequirementParser.fromLine("unknown-native-only==1.0", projectFixture.get()))
    assertThat(manager.findPackageSpecification(missing, null)).isNull()
  }

  @Test
  fun `the native repository keeps the persisted Conda enabled setting`(@TestDisposable disposable: Disposable) {
    val manager = PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager) { error("The enabled setting must not run a search.") }
      .also { Disposer.register(disposable, it) }
    val previous = CondaPackageRepository.enabled
    try {
      CondaPackageRepository.enabled = false
      assertThat(manager.nativeRepository.enabled).isFalse()
      manager.nativeRepository.enabled = true
      assertThat(CondaPackageRepository.enabled).isTrue()
    }
    finally {
      CondaPackageRepository.enabled = previous
    }
  }
}
