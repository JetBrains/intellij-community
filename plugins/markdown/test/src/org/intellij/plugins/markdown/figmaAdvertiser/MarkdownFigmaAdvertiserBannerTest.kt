package org.intellij.plugins.markdown.figmaAdvertiser

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.markdown.figmaAdvertiser.FIGMA_CONNECT_PLUGIN_ID
import com.intellij.markdown.figmaAdvertiser.FIGMA_CONNECT_PLUGIN_NAME
import com.intellij.markdown.figmaAdvertiser.FIGMA_LINK_SCAN_KEY
import com.intellij.markdown.figmaAdvertiser.FIGMA_SUGGESTION_DISMISSED_KEY
import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserRegistry
import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserUsagesCollector
import com.intellij.markdown.figmaAdvertiser.FigmaConnectPluginSuggestionProvider
import com.intellij.markdown.figmaAdvertiser.FigmaSuggestionOffer
import com.intellij.markdown.figmaAdvertiser.MarkdownFigmaAdvertiserBundle
import com.intellij.markdown.figmaAdvertiser.ShownSuggestions
import com.intellij.markdown.figmaAdvertiser.isFigmaSuggestionDismissed
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationPanel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * The banner over a Markdown file, and the wiring that puts it there.
 *
 * `FigmaAdvertiserUsagesCollector` reports one shape, so a `suggestion.shown` claimed by one case
 * would silence every case after it. [setUp] gives each case its own [ShownSuggestions], and
 * [tearDown] clears the dismissal the project carries, for the same reason: a
 * `BasePlatformTestCase` shares one project across the class.
 */
class MarkdownFigmaAdvertiserBannerTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    // Asked for rather than inherited: a branch that ships the advertisement off would otherwise
    // turn these into failures that name the assertion and not the cause.
    Registry.get(FigmaAdvertiserRegistry.KEY_ADVERTISER_ENABLED).setValue(true, testRootDisposable)
    project.replaceService(ShownSuggestions::class.java, ShownSuggestions(), testRootDisposable)
  }

  override fun tearDown() {
    try {
      PropertiesComponent.getInstance(project).unsetValue(FIGMA_SUGGESTION_DISMISSED_KEY)
      PropertiesComponent.getInstance().unsetValue(FIGMA_SUGGESTION_DISMISSED_KEY)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test a Markdown file that links to Figma is offered the banner`() {
    val panel = bannerOver(designNote())

    panel.text shouldBe MarkdownFigmaAdvertiserBundle.message("markdown.figma.suggestion.text")
    panel.findLabelByName(INSTALL_LABEL).shouldNotBeNull()
    panel.findLabelByName(DISMISS_LABEL).shouldNotBeNull()
  }

  /**
   * The mechanism above says nothing about whether anything asks it. This goes through the
   * extension point the platform's banner reads
   * (`PluginAdvertiserEditorNotificationProvider.kt:71`), so the descriptor's registration is what
   * is pinned. The plugin id is the value only this advertisement produces.
   */
  fun `test the extension point reaches the provider over a Markdown file`() {
    val note = designNote()

    val suggestion = PLUGIN_SUGGESTION_EP.extensionList
      .firstNotNullOfOrNull { it.getSuggestion(project, note.virtualFile) }

    suggestion.shouldNotBeNull()
    suggestion.pluginIds shouldBe listOf(FIGMA_CONNECT_PLUGIN_ID)
    panelOf(suggestion).text shouldBe MarkdownFigmaAdvertiserBundle.message("markdown.figma.suggestion.text")
  }

  /** The record says which trigger the user answered, which is what this group exists for. */
  fun `test the shown event names the Markdown trigger and the editor surface`() {
    val note = designNote()

    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      panelOf(suggestionOver(note).shouldNotBeNull())
    }

    val shown = events.single {
      it.group.id == FigmaAdvertiserUsagesCollector.GROUP_ID && it.event.id == "suggestion.shown"
    }
    shown.event.data["trigger"] shouldBe "MARKDOWN_FIGMA_LINK"
    shown.event.data["surface"] shouldBe "EDITOR"
  }

  /**
   * `PluginSuggestion.apply` runs per file editor and again on every
   * `EditorNotifications.updateAllNotifications()`, so an unguarded record would count repaints.
   */
  fun `test the shown event fires once for a project however often the banner is painted`() {
    val note = designNote()

    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      repeat(3) { panelOf(suggestionOver(note).shouldNotBeNull()) }
    }

    events.filter {
      it.group.id == FigmaAdvertiserUsagesCollector.GROUP_ID && it.event.id == "suggestion.shown"
    } shouldHaveSize 1
  }

  /** A Markdown file that links to nothing earns no banner, and the answer is kept on the file. */
  fun `test a Markdown file without a Figma link earns no banner and is read once`() {
    val note = myFixture.configureByText("notes.md", "We talked about the design in the meeting.\n")

    suggestionOver(note).shouldBeNull()
    val firstScan = FIGMA_LINK_SCAN_KEY.get(note.virtualFile).shouldNotBeNull()

    suggestionOver(note).shouldBeNull()

    // The same value object, so the second ask read the answer off the file and not the text.
    FIGMA_LINK_SCAN_KEY.get(note.virtualFile) shouldBeSameInstanceAs firstScan
  }

  /**
   * The path decides before the file is opened. A user opens files all day, and none of them is
   * read for this banner unless Markdown claims the extension.
   */
  fun `test a file that is not Markdown is never read`() {
    val note = myFixture.configureByText("notes.txt", "The spec is at https://www.figma.com/design/AbC123/Checkout\n")

    suggestionOver(note).shouldBeNull()

    FIGMA_LINK_SCAN_KEY.get(note.virtualFile).shouldBeNull()
  }

  /**
   * Tells a switched-off advertisement from a file it has nothing to say about. The same file earns
   * the banner in the cases above, so what changed is the key.
   */
  fun `test no banner is offered while the advertiser is switched off`() {
    val note = designNote()
    Registry.get(FigmaAdvertiserRegistry.KEY_ADVERTISER_ENABLED).setValue(false, testRootDisposable)

    suggestionOver(note).shouldBeNull()

    // The switch is read before the path is, so the file is not read either.
    FIGMA_LINK_SCAN_KEY.get(note.virtualFile).shouldBeNull()
  }

  /**
   * The answer is recorded on the project that was asked. A second project keeps the offer, which is
   * the accepted cost of not silencing the suggestion for someone who met it in the wrong project.
   */
  fun `test Dismiss records the answer on the project that was asked`() {
    val note = designNote()
    val panel = bannerOver(note)

    panel.findLabelByName(DISMISS_LABEL)!!.doClick()

    PropertiesComponent.getInstance(project).isTrueValue(FIGMA_SUGGESTION_DISMISSED_KEY).shouldBeTrue()
    PropertiesComponent.getInstance().isTrueValue(FIGMA_SUGGESTION_DISMISSED_KEY).shouldBeFalse()
    // The default project is a second project this test can read without opening one.
    isFigmaSuggestionDismissed(ProjectManager.getInstance().defaultProject).shouldBeFalse()
    suggestionOver(note).shouldBeNull()
  }

  /**
   * With Figma Connect loaded, nothing is drawn, and the exclusion is
   * `buildSuggestionIfNeeded`'s already-loaded filter rather than a check of this module's own.
   *
   * `loadPluginWithText` returns a `Disposable` that unloads and uninstalls, so the plugin does not
   * outlive the case. The three asks around it are what make the middle one mean something: the same
   * provider and the same file answer non-null before and after.
   */
  fun `test nothing is offered while a plugin with the Figma Connect id is loaded`() {
    val note = designNote()
    val provider = FigmaConnectPluginSuggestionProvider()
    provider.getSuggestion(project, note.virtualFile).shouldNotBeNull()

    val unload = loadFigmaConnectId()
    try {
      PluginManager.getLoadedPlugins().any { it.pluginId.idString == FIGMA_CONNECT_PLUGIN_ID }.shouldBeTrue()
      provider.getSuggestion(project, note.virtualFile).shouldBeNull()
    }
    finally {
      Disposer.dispose(unload)
    }

    provider.getSuggestion(project, note.virtualFile).shouldNotBeNull()
  }

  /**
   * The offer's word follows whether the plugin is on disk, not whether it is switched off. A
   * plugin that is on disk needs no install, and offering one sends the user to Marketplace for
   * something they already have.
   *
   * The fixture is a **loaded** plugin, the cheap way to make `getPlugin` answer. The state the
   * predicate actually matters in — on disk, switched on, not loaded — is the case below. The offer
   * is built directly because `buildSuggestionIfNeeded` drops a loaded plugin, so `getSuggestion`
   * cannot reach this.
   */
  fun `test the offer asks to enable a plugin that is already on disk`() {
    val unload = loadFigmaConnectId()
    try {
      val offer = offerForTest()

      offer.primaryActionText shouldBe
        MarkdownFigmaAdvertiserBundle.message("markdown.figma.suggestion.action.enable", FIGMA_CONNECT_PLUGIN_NAME)
      offer.primaryActionText shouldNotBe INSTALL_LABEL
    }
    finally {
      Disposer.dispose(unload)
    }
  }

  /**
   * On disk, switched on, and not loaded — the state `PluginEnabler.HEADLESS.enable` leaves behind,
   * because it writes the disabled flag and loads nothing. The offer must not call it an install,
   * and `isDisabled` answers false here, so the label cannot be derived from that.
   *
   * The state is built by publishing a plugin set the id is installed in and not enabled in, which
   * leaves `DisabledPluginsState` untouched. `PluginSet`'s constructor is `internal`, so
   * `PluginSetTestBuilder` is the way in, and the original set is put back in a `finally` because it
   * is application-wide.
   */
  fun `test the offer asks to enable a plugin on disk that is switched on and not loaded`() {
    withFigmaConnectOnDiskAndSwitchedOn {
      PluginManagerCore.getPlugin(PluginId.getId(FIGMA_CONNECT_PLUGIN_ID)).shouldNotBeNull()
      PluginManagerCore.isDisabled(PluginId.getId(FIGMA_CONNECT_PLUGIN_ID)).shouldBeFalse()
      PluginManager.getLoadedPlugins().none { it.pluginId.idString == FIGMA_CONNECT_PLUGIN_ID }.shouldBeTrue()

      val offer = offerForTest()
      offer.primaryActionText shouldBe
        MarkdownFigmaAdvertiserBundle.message("markdown.figma.suggestion.action.enable", FIGMA_CONNECT_PLUGIN_NAME)
      offer.primaryActionText shouldNotBe INSTALL_LABEL
    }
  }

  /**
   * The click asks the enabler to load the descriptor, and it asks for a plugin that is already
   * switched on as well. `DynamicPluginEnabler.enable` answers true at once when everything is
   * loaded and attempts the load otherwise, so one call covers both on-disk states; skipping it for
   * a switched-on plugin would send a user to a restart the session does not need.
   *
   * `PluginEnabler` is an application service, replaced here so that nothing is loaded into the
   * test application. Its answer is what decides the branch `accept()` takes next.
   */
  fun `test accepting asks the enabler to load a plugin on disk that is switched on`() {
    val enabler = RecordingPluginEnabler(loaded = true)
    ApplicationManager.getApplication().replaceService(PluginEnabler::class.java, enabler, testRootDisposable)

    withFigmaConnectOnDiskAndSwitchedOn {
      val offer = offerForTest()
      // Checked before the click, so a mutation that reverts the label predicate fails here rather
      // than in `installAndEnable`, which goes to Marketplace.
      offer.primaryActionText shouldNotBe INSTALL_LABEL
      offer.accept()
    }

    enabler.enabled shouldBe listOf(FIGMA_CONNECT_PLUGIN_ID)
  }

  /** The dismissal records what the user answered and nothing else. */
  fun `test the dismissed event names the Markdown trigger`() {
    val panel = bannerOver(designNote())

    val events = FUCollectorTestCase.collectLogEvents(testRootDisposable) {
      panel.findLabelByName(DISMISS_LABEL)!!.doClick()
    }

    val dismissed = events.single {
      it.group.id == FigmaAdvertiserUsagesCollector.GROUP_ID && it.event.id == "suggestion.dismissed"
    }
    dismissed.event.data["trigger"] shouldBe "MARKDOWN_FIGMA_LINK"
  }

  /**
   * Runs [body] with a plugin set in which Figma Connect's id is installed and not enabled, which
   * leaves `DisabledPluginsState` untouched — so the plugin reads as on disk, switched on, and not
   * loaded. `PluginSet`'s constructor is `internal`, so `PluginSetTestBuilder` is the way in, and
   * the set is application-wide, so the original goes back in a `finally`.
   */
  private fun <T> withFigmaConnectOnDiskAndSwitchedOn(body: () -> T): T {
    val pluginsDir = FileUtil.createTempDirectory("figmaAdvertiserTest", "unloaded", true).toPath()
    plugin(FIGMA_CONNECT_PLUGIN_ID) { name = FIGMA_CONNECT_PLUGIN_NAME }.installAt(pluginsDir)
    val originalPluginSet = PluginManagerCore.getPluginSet()
    PluginManagerCore.setPluginSet(
      PluginSetTestBuilder.fromPath(pluginsDir)
        .withDisabledPlugins(FIGMA_CONNECT_PLUGIN_ID)
        .build(configureClassLoaders = false)
    )
    try {
      return body()
    }
    finally {
      PluginManagerCore.setPluginSet(originalPluginSet)
    }
  }

  private fun offerForTest(): FigmaSuggestionOffer = FigmaSuggestionOffer(
    project,
    FigmaAdvertiserUsagesCollector.SuggestionTrigger.MARKDOWN_FIGMA_LINK,
    FigmaAdvertiserUsagesCollector.SuggestionSurface.EDITOR,
  )

  /** Records what `accept()` asks of the platform, and loads nothing into the test application. */
  private class RecordingPluginEnabler(private val loaded: Boolean) : PluginEnabler {
    val enabled: MutableList<String> = mutableListOf()

    override fun isDisabled(pluginId: PluginId): Boolean = false

    override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
      descriptors.mapTo(enabled) { it.pluginId.idString }
      return loaded
    }

    override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = false
  }

  /**
   * Loads a plugin carrying Figma Connect's id, and answers the `Disposable` that takes it away
   * again. It declares only a dependency on the platform's language modules, so the id is the whole
   * of what this fixture shares with the real plugin — which is all either test reads.
   */
  private fun loadFigmaConnectId(): Disposable = loadPluginWithText(
    pluginSpec = plugin(FIGMA_CONNECT_PLUGIN_ID) { dependsIntellijModulesLang() },
    pluginsDir = FileUtil.createTempDirectory("figmaAdvertiserTest", "plugins", true).toPath(),
  )

  private fun designNote(): PsiFile = myFixture.configureByText(
    "design.md",
    "# Checkout\n\nThe spec is at https://www.figma.com/design/AbC123/Checkout?node-id=1-2 .\n",
  )

  private fun suggestionOver(file: PsiFile): PluginSuggestion? =
    FigmaConnectPluginSuggestionProvider().getSuggestion(project, file.virtualFile)

  private fun bannerOver(file: PsiFile): EditorNotificationPanel =
    panelOf(suggestionOver(file).shouldNotBeNull())

  private fun panelOf(suggestion: PluginSuggestion): EditorNotificationPanel {
    val panel = suggestion.apply(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
    panel.shouldNotBeNull()
    return panel
  }

  private companion object {
    val PLUGIN_SUGGESTION_EP: ExtensionPointName<PluginSuggestionProvider> =
      ExtensionPointName("com.intellij.pluginSuggestionProvider")

    val INSTALL_LABEL: String =
      IdeBundle.message("plugins.advertiser.action.install.plugin.name", FIGMA_CONNECT_PLUGIN_NAME)
    val DISMISS_LABEL: String = IdeBundle.message("plugins.advertiser.action.ignore.ultimate")
  }
}
