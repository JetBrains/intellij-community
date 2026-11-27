// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("VisibleToClassesUsage")

package com.intellij.sh.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.setEelDescriptor
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Path
import javax.swing.JPanel

@TestApplication
internal class ShFrontendTerminalRunnerTest {
  private val ownerFixture = projectFixture()
  private val memberFixture = projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  @OptIn(EelDelicateApi::class)
  fun `workspace Markdown rejects a terminal from another target`(): Unit = timeoutRunBlocking {
    val context = Context(this)
    withContext(Dispatchers.UI) {
      context.runner.createNewTerminal(context.member, "first", "/member", "Example", "file:///README.md")
      val created = context.memberTabs.tabCreated.await()
      val target = object : EelDescriptor by LocalEelDescriptor {}
      context.member.setEelDescriptor(target)
      try {
        assertFalse(context.runner.hasTerminalStartedFrom(context.member, "file:///README.md"))
        created.startSession(target)
        assertTrue(context.runner.hasTerminalStartedFrom(context.member, "file:///README.md"))
      }
      finally {
        context.member.setEelDescriptor(LocalEelDescriptor)
      }
    }
  }

  @Test
  fun `workspace Markdown reuses a terminal without a known directory`(): Unit = timeoutRunBlocking {
    val context = Context(this)
    withContext(Dispatchers.UI) {
      context.runner.createNewTerminal(context.member, "first", "/member", "Example", "file:///README.md")
      val created = context.memberTabs.tabCreated.await()
      assertTrue(context.ownerTabs.created.isEmpty())
      assertSame(created.tab.content, context.toolWindow.contentManager.contents.single())
      assertFalse(context.runner.hasTerminalStartedFrom(context.owner, "file:///README.md"))
      var requestedNewTerminal = false
      context.runner.run(context.owner, "owner", "Owner", { requestedNewTerminal = true })
      assertTrue(requestedNewTerminal)
      fun runCommand(command: String) {
        assertTrue(context.runner.hasTerminalStartedFrom(context.member, "file:///README.md"))
        assertTrue(context.runner.run(context.member, command, "Example", { error("The member terminal must be reused") }))
      }

      runCommand("before-session")
      created.startSession(LocalEelDescriptor)
      runCommand("after-session")

      assertEquals(listOf("first", "before-session", "after-session"), created.commands)
    }
  }

  private inner class Context(scope: CoroutineScope) {
    val owner = ownerFixture.get()
    val member = memberFixture.get()
    private val ownerWindows = ToolWindowHeadlessManagerImpl(owner)
    val toolWindow = ownerWindows.doRegisterToolWindow("Terminal")
    val ownerTabs = TestTabsManager(toolWindow, scope)
    val memberTabs = TestTabsManager(null, scope)
    val runner = ShFrontendTerminalRunner()

    init {
      owner.replaceService(ToolWindowManager::class.java, ownerWindows, disposable)
      member.replaceService(ToolWindowManager::class.java, ToolWindowHeadlessManagerImpl(member), disposable)
      owner.replaceService(TerminalToolWindowTabsManager::class.java, ownerTabs, disposable)
      member.replaceService(TerminalToolWindowTabsManager::class.java, memberTabs, disposable)
      val windowManager = WindowManagerEx.getInstanceEx()
      windowManager.addFrameGuest(owner, member)
      Disposer.register(disposable) { windowManager.removeFrameGuest(member) }
    }
  }

  private class TestTabsManager(private val toolWindow: ToolWindow?, private val scope: CoroutineScope) : TerminalToolWindowTabsManager {
    val created = mutableListOf<CreatedTab>()
    val tabCreated = CompletableDeferred<CreatedTab>()

    override val tabs: List<TerminalToolWindowTab>
      get() = error("Unexpected tab lookup")

    override fun createTabBuilder(): TerminalToolWindowTabBuilder {
      val options = mutableMapOf<String, Any?>()
      lateinit var builder: TerminalToolWindowTabBuilder
      builder = proxy { name, args ->
        if (name == "createTabAsync") {
          val result = CreatedTab(options, scope)
          created.add(result)
          if (options["shouldAddToToolWindow"] != false) {
            requireNotNull(toolWindow).contentManager.addContent(result.tab.content)
          }
          tabCreated.complete(result)
          result.tab
        }
        else {
          options[name] = args.single()
          builder
        }
      }
      return builder
    }

    override fun attachTab(tab: TerminalToolWindowTab, contentManager: ContentManager?) {
      (contentManager ?: requireNotNull(toolWindow).contentManager).addContent(tab.content)
    }

    override fun closeTab(tab: TerminalToolWindowTab): Unit = error("Unexpected close")
    override fun detachTab(tab: TerminalToolWindowTab): Unit = error("Unexpected detach")

    @Suppress("OVERRIDE_DEPRECATION")
    override fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener) = Unit
  }

  private class CreatedTab(options: Map<String, Any?>, scope: CoroutineScope) {
    val commands = mutableListOf<String>()
    private val session = CompletableDeferred<TerminalSession>()

    fun startSession(descriptor: EelDescriptor) {
      session.complete(proxy { name, _ ->
        when (name) {
          "getEelDescriptor" -> descriptor
          else -> error(name)
        }
      })
    }

    private val title = TerminalTitle()
    private val sender = object : TerminalSendTextBuilder {
      override fun shouldExecute(): TerminalSendTextBuilder = this
      override fun useBracketedPasteMode(): TerminalSendTextBuilder = this
      override fun send(text: String) {
        commands.add(text)
      }
    }
    private val view = proxy<TerminalView> { name, _ ->
      when (name) {
        "getCoroutineScope" -> scope
        "getTitle" -> title
        "getWorkingDirectoryFlow" -> MutableStateFlow<Path?>(null)
        "getSessionDeferred" -> session
        "createSendTextBuilder" -> sender
        else -> error(name)
      }
    }
    val tab: TerminalToolWindowTab

    init {
      val content = ContentFactory.getInstance().createContent(JPanel(), options["tabName"] as String, false)
      tab = proxy { name, _ ->
        when (name) {
          "getContent" -> content
          "getView" -> view
          else -> error(name)
        }
      }
      content.putUserData(TerminalToolWindowTab.KEY, tab)
    }
  }

  private companion object {
    inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
      Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
        when (method.name) {
          "equals" -> instance === args?.single()
          "hashCode" -> System.identityHashCode(instance)
          "toString" -> T::class.java.simpleName
          else -> invoke(method.name, args ?: emptyArray())
        }
      } as T
  }
}
