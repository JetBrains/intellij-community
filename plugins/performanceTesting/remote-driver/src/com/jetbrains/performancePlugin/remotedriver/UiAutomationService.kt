// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.ui.UiActionResult
import com.intellij.driver.model.ui.UiBounds
import com.intellij.driver.model.ui.UiClickRequest
import com.intellij.driver.model.ui.UiDragRequest
import com.intellij.driver.model.ui.UiFillRequest
import com.intellij.driver.model.ui.UiLocator
import com.intellij.driver.model.ui.UiNode
import com.intellij.driver.model.ui.UiRole
import com.intellij.driver.model.ui.UiSnapshot
import com.intellij.driver.model.ui.UiState
import com.intellij.driver.model.ui.UiValue
import com.intellij.driver.model.ui.UiWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.math.min

@Suppress("unused")
@Service(Service.Level.APP)
class UiAutomationService {
  private val robot get() = service<RobotService>().robot
  private val nextNodeId = AtomicLong(1)
  private val nextRevision = AtomicLong(1)
  private val nodeIds = WeakHashMap<Component, Long>()

  fun snapshot(): UiSnapshot = capture().snapshot

  fun find(locator: UiLocator, timeoutMs: Long): UiNode? {
    require(timeoutMs >= 0) { "timeoutMs must be >= 0, got $timeoutMs" }
    return resolve(locator, timeoutMs)?.node
  }

  fun activateWindow(locator: UiLocator, timeoutMs: Long): UiNode? {
    require(timeoutMs >= 0) { "timeoutMs must be >= 0, got $timeoutMs" }
    val resolved = resolve(locator, timeoutMs) ?: return null
    activateWindow(resolved.component)
    return resolve(locator, 0)?.node ?: error("UI element ${describe(locator)} disappeared after its window was activated")
  }

  fun click(request: UiClickRequest): UiActionResult {
    require(request.clickCount >= 1) { "clickCount must be >= 1, got ${request.clickCount}" }
    return perform(request.locator, request.timeoutMs) { resolved ->
      prepareForMouseAction(resolved.component)
      robot.click(resolved.component, request.button, request.clickCount)
      robot.waitForIdle()
    }
  }

  fun fill(request: UiFillRequest): UiActionResult {
    return perform(request.locator, request.timeoutMs) { resolved ->
      prepareForKeyboardAction(resolved.component)
      onEdt { replaceAccessibleText(resolved.component, request.text) }
      robot.waitForIdle()
      checkInputFocus(resolved.component)
      retryable {
        check(onEdt { readAccessibleText(resolved.component) } == request.text) {
          "component did not expose the requested text after replacement"
        }
      }
    }
  }

  fun drag(request: UiDragRequest): UiActionResult {
    require(request.timeoutMs >= 0) { "timeoutMs must be >= 0, got ${request.timeoutMs}" }
    val startedAt = System.currentTimeMillis()
    val from = resolve(request.from, request.timeoutMs) ?: error("No UI element found for ${describe(request.from)}")
    val remaining = (request.timeoutMs - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0)
    val to = resolve(request.to, remaining) ?: error("No UI element found for ${describe(request.to)}")
    prepareForMouseAction(from.component)
    prepareForMouseAction(to.component)
    val fromPoint = centerOf(from.component)
    val toPoint = centerOf(to.component)
    robot.moveMouse(from.component, fromPoint)
    robot.pressMouse(from.component, fromPoint, com.intellij.driver.model.RemoteMouseButton.LEFT)
    try {
      robot.moveMouse(to.component, toPoint)
    }
    finally {
      robot.releaseMouse(com.intellij.driver.model.RemoteMouseButton.LEFT)
    }
    robot.waitForIdle()
    val after = capture()
    return UiActionResult(after.nodesById[from.node.id] ?: from.node, from.capture.snapshot.revision, after.snapshot.revision)
  }

  private fun perform(locator: UiLocator, timeoutMs: Long, action: (Resolved) -> Unit): UiActionResult {
    require(timeoutMs >= 0) { "timeoutMs must be >= 0, got $timeoutMs" }
    val deadline = System.currentTimeMillis() + timeoutMs
    var lastFailure: Throwable? = null
    do {
      val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
      val resolved = resolve(locator, remaining)
      if (resolved == null) break
      try {
        action(resolved)
        val after = capture()
        return UiActionResult(after.nodesById[resolved.node.id] ?: resolved.node, resolved.capture.snapshot.revision, after.snapshot.revision)
      }
      catch (e: RetryableActionException) {
        lastFailure = e
      }
      if (System.currentTimeMillis() < deadline) Thread.sleep(min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()))
    }
    while (System.currentTimeMillis() <= deadline)
    val suffix = lastFailure?.message?.let { ": $it" }.orEmpty()
    error("UI element ${describe(locator)} did not become actionable within ${timeoutMs}ms$suffix")
  }

  private fun resolve(locator: UiLocator, timeoutMs: Long): Resolved? {
    val deadline = System.currentTimeMillis() + timeoutMs
    do {
      val captured = capture()
      val matches = findMatches(locator, captured)
      if (matches.size > 1) {
        val candidates = matches.take(5).joinToString { describe(it.node) }
        error("Strict locator ${describe(locator)} matched ${matches.size} elements: $candidates")
      }
      if (matches.size == 1) return matches.single()
      if (System.currentTimeMillis() < deadline) Thread.sleep(min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()))
    }
    while (System.currentTimeMillis() <= deadline)
    return null
  }

  private fun findMatches(locator: UiLocator, capture: Capture): List<Resolved> {
    val ids = UiLocatorMatcher.find(locator, capture.snapshot.root) { xpathIds(it, capture) }
    return ids.mapNotNull { id ->
      val node = capture.nodesById[id] ?: return@mapNotNull null
      val component = capture.componentsById[id] ?: return@mapNotNull null
      Resolved(capture, node, component)
    }
  }

  private fun xpathIds(xpath: String, capture: Capture): Set<Long> {
    val result = XPathFactory.newInstance().newXPath().compile(xpath)
      .evaluate(capture.document, XPathConstants.NODESET) as NodeList
    return (0 until result.length).mapNotNullTo(mutableSetOf()) { index ->
      (result.item(index) as? Element)?.let(capture.elementIds::get)
    }
  }

  private fun capture(): Capture {
    val document = XpathDataModelCreator().create(null)
    val revision = nextRevision.getAndIncrement()
    return GuiActionRunner.execute(object : GuiQuery<Capture>() {
      override fun executeInEDT(): Capture {
        val componentsById = mutableMapOf<Long, Component>()
        val nodesById = mutableMapOf<Long, UiNode>()
        val elementIds = IdentityHashMap<Element, Long>()
        val children = document.documentElement.childElements().mapNotNull {
          createNode(it, componentsById, nodesById, elementIds)
        }.toList()
        val root = UiNode(
          id = 0,
          className = "Application",
          javaClassName = "",
          role = UiRole.UNKNOWN,
          name = null,
          description = null,
          texts = emptyList(),
          states = emptySet(),
          actions = emptyList(),
          value = null,
          bounds = null,
          attributes = emptyMap(),
          children = children,
        )
        nodesById[root.id] = root
        return Capture(
          UiSnapshot(revision, root, captureWindows()),
          document,
          componentsById,
          nodesById,
          elementIds,
        )
      }
    })
  }

  private fun createNode(
    element: Element,
    componentsById: MutableMap<Long, Component>,
    nodesById: MutableMap<Long, UiNode>,
    elementIds: IdentityHashMap<Element, Long>,
  ): UiNode? {
    val component = element.getUserData("component") as? Component ?: return null
    val id = nodeId(component)
    componentsById[id] = component
    elementIds[element] = id
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val attributes = element.selectedAttributes()
    val states = buildSet {
      attributes["accessiblestates"]?.split(',')?.mapNotNullTo(this, ::parseState)
      if (component.isShowing) add(UiState.SHOWING)
      if (component.isVisible) add(UiState.VISIBLE)
      if (component.isEnabled) add(UiState.ENABLED)
      if (component.isFocusable) add(UiState.FOCUSABLE)
      if (component.isFocusOwner) add(UiState.FOCUSED)
      if (attributes["selected"].toBoolean()) add(UiState.SELECTED)
      if (attributes["disabled"].toBoolean()) remove(UiState.ENABLED)
      if (component is Window) {
        if (component.isActive || component === focusManager.activeWindow) add(UiState.ACTIVE)
        if (component.isFocused || component === focusManager.focusedWindow) add(UiState.FOCUSED)
        if (component is Dialog && component.isModal) add(UiState.MODAL)
      }
    }
    val children = element.childElements().mapNotNull { createNode(it, componentsById, nodesById, elementIds) }.toList()
    val value = createValue(attributes)
    val bounds = component.boundsOnScreen()
    val role = parseRole(attributes["accessiblerole"] ?: attributes["role"])
      .takeUnless { it == UiRole.UNKNOWN }
      ?: component.windowRole()
    val node = UiNode(
      id = id,
      className = element.getAttribute("class").ifBlank { element.tagName },
      javaClassName = element.getAttribute("javaclass"),
      role = role,
      name = attributes["accessiblename"] ?: attributes["contentdescription"],
      description = attributes["accessibledescription"],
      texts = (attributes["visible_text"] ?: attributes["text"])
        ?.split(" || ")
        ?.filter(String::isNotBlank)
        .orEmpty(),
      states = states,
      actions = attributes["accessibleactions"]?.split(" || ")?.filter(String::isNotBlank).orEmpty(),
      value = value,
      bounds = bounds,
      attributes = attributes,
      children = children,
      text = readAccessibleTextOrNull(component, role),
      windowId = component.containingWindow()?.let(::nodeId),
    )
    nodesById[id] = node
    return node
  }

  private fun captureWindows(): List<UiWindow> {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return Window.getWindows()
      .filter { it.isShowing }
      .map { window ->
        UiWindow(
          id = nodeId(window),
          className = window.componentClassName(),
          javaClassName = window.javaClass.name,
          role = window.windowRole(),
          title = window.windowTitle(),
          ownerId = window.owner?.let(::nodeId),
          bounds = window.boundsOnScreen(),
          active = window === focusManager.activeWindow || window.isActive,
          focused = window === focusManager.focusedWindow || window.isFocused,
          modal = window is Dialog && window.isModal,
        )
      }
  }

  @ApiStatus.Internal
  fun getWindowsForHitTest(): List<Window> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return Window.getWindows()
      .filter { it.isShowing }
      .withIndex()
      .sortedWith(hitTestWindowOrder(focusManager))
      .map { it.value }
  }

  private fun hitTestWindowOrder(focusManager: KeyboardFocusManager): Comparator<IndexedValue<Window>> {
    return compareByDescending<IndexedValue<Window>> { it.value === focusManager.focusedWindow }
      .thenByDescending { it.value === focusManager.activeWindow || it.value.isActive }
      .thenByDescending { it.value.ownerDepth() }
      .thenByDescending { it.index }
  }

  private fun nodeId(component: Component): Long {
    return synchronized(nodeIds) { nodeIds.getOrPut(component) { nextNodeId.getAndIncrement() } }
  }

  private fun prepareForMouseAction(component: Component) {
    retryable {
      onEdt {
        check(component.isDisplayable) { "component is not displayable" }
        check(component.isShowing) { "component is not showing" }
        check(component.isEnabled) { "component is disabled" }
        if (component is JComponent) component.scrollRectToVisible(Rectangle(0, 0, component.width, component.height))
      }
      robot.waitForIdle()
      activateWindow(component)
      val firstBounds = onEdt { component.boundsOnScreen() ?: error("component has no screen bounds") }
      robot.waitForIdle()
      val secondBounds = onEdt { component.boundsOnScreen() ?: error("component has no screen bounds") }
      check(firstBounds == secondBounds) { "component bounds are not stable" }
      onEdt {
        val window = SwingUtilities.getWindowAncestor(component) ?: error("component has no window")
        val point = Point(secondBounds.x + secondBounds.width / 2, secondBounds.y + secondBounds.height / 2)
        SwingUtilities.convertPointFromScreen(point, window)
        val hit = deepestComponentAtIgnoringBareGlassPane(window, point)
        check(hit === component || hit != null && SwingUtilities.isDescendingFrom(hit, component)) {
          "component center is covered by ${hit?.javaClass?.name ?: "nothing"}"
        }
      }
    }
  }

  private fun prepareForKeyboardAction(component: Component) {
    retryable {
      onEdt {
        check(component.isDisplayable) { "component is not displayable" }
        check(component.isShowing) { "component is not showing" }
        check(component.isEnabled) { "component is disabled" }
        check(component.isFocusable) { "component is not focusable" }
        if (component is JComponent) component.scrollRectToVisible(Rectangle(0, 0, component.width, component.height))
      }
      robot.waitForIdle()
      activateAndFocus(component)
    }
  }

  private fun activateWindow(component: Component) {
    val focusTarget = onEdt {
      val window = component.containingWindow() ?: error("component has no window")
      if (component !is Window && component.isFocusable) component
      else window.focusOwner?.takeIf { it.isUsableFocusTarget(window) }
           ?: window.mostRecentFocusOwner?.takeIf { it.isUsableFocusTarget(window) }
           ?: window.firstFocusableDescendant()
           ?: error("window has no focusable component")
    }
    activateAndFocus(focusTarget)
  }

  private fun activateAndFocus(component: Component) {
    onEdt {
      val window = component.containingWindow() ?: error("component has no window")
      requestForeground(window)
    }
    robot.waitForIdle()
    robot.focusAndWaitForFocusGain(component)
    checkInputFocus(component)
  }

  private fun checkInputFocus(expected: Component) {
    retryable {
      check(onEdt { expected.isFocusOwner }) { "focus was not acquired by ${expected.javaClass.name}" }
      check(robot.hasInputFocus()) { "IDE did not receive the focus probe" }
      check(onEdt { isWindowOrOwnerActive(SwingUtilities.getWindowAncestor(expected)) }) { "IDE window is not active" }
    }
  }

  private fun centerOf(component: Component): Point = onEdt { Point(component.width / 2, component.height / 2) }

  private fun Component.boundsOnScreen(): UiBounds? {
    if (!isShowing || width <= 0 || height <= 0) return null
    val location = runCatching { locationOnScreen }.getOrNull() ?: return null
    return UiBounds(location.x, location.y, width, height)
  }

  private fun Window.firstFocusableDescendant(): Component? {
    fun find(container: Container): Component? {
      for (child in container.components) {
        if (child.isShowing && child.isEnabled && child.isFocusable) return child
        if (child is Container) find(child)?.let { return it }
      }
      return null
    }
    return find(this)
  }

  private fun Component.isUsableFocusTarget(window: Window): Boolean {
    return this !== window && isShowing && isEnabled && isFocusable
  }

  private fun <T> onEdt(action: () -> T): T = GuiActionRunner.execute(object : GuiQuery<T>() {
    override fun executeInEDT(): T = action()
  })

  private fun retryable(action: () -> Unit) {
    try {
      action()
    }
    catch (e: RetryableActionException) {
      throw e
    }
    catch (e: Throwable) {
      throw RetryableActionException(e.message ?: e.javaClass.simpleName, e)
    }
  }

  private data class Capture(
    val snapshot: UiSnapshot,
    val document: Document,
    val componentsById: Map<Long, Component>,
    val nodesById: Map<Long, UiNode>,
    val elementIds: IdentityHashMap<Element, Long>,
  )

  private data class Resolved(val capture: Capture, val node: UiNode, val component: Component)

  private class RetryableActionException(message: String, cause: Throwable) : RuntimeException(message, cause)

  private companion object {
    const val POLL_INTERVAL_MS = 100L
  }
}

private val SEMANTIC_ATTRIBUTES = setOf(
  "accessiblename",
  "accessibledescription",
  "accessiblerole",
  "accessiblestates",
  "accessibleactions",
  "accessiblevalue",
  "accessiblevaluemin",
  "accessiblevaluemax",
  "contentdescription",
  "disabled",
  "editabletext",
  "focused",
  "heading",
  "progresscurrent",
  "progressrange",
  "role",
  "selected",
  "testtag",
  "text",
  "title",
  "toggleablestate",
  "tooltip",
  "tooltiptext",
  "visible_text",
)

private fun Element.selectedAttributes(): Map<String, String> = buildMap {
  for (index in 0 until attributes.length) {
    val attribute = attributes.item(index)
    if (attribute.nodeName in SEMANTIC_ATTRIBUTES && attribute.nodeValue.isNotBlank()) {
      put(attribute.nodeName, attribute.nodeValue)
    }
  }
}

private fun Element.childElements(): Sequence<Element> = sequence {
  for (index in 0 until childNodes.length) {
    (childNodes.item(index) as? Element)?.takeIf { it.tagName == "div" }?.let { yield(it) }
  }
}

private fun UiNode.descendants(): Sequence<UiNode> = sequence {
  yield(this@descendants)
  for (child in children) yieldAll(child.descendants())
}

private fun matchesText(actual: String?, expected: String, exact: Boolean): Boolean {
  return actual?.let { if (exact) it == expected else it.contains(expected, ignoreCase = true) } ?: false
}

internal object UiLocatorMatcher {
  fun find(locator: UiLocator, root: UiNode, xpathIds: (String) -> Set<Long> = { emptySet() }): List<Long> {
    return when (locator) {
      is UiLocator.AccessibleName -> root.descendants()
        .filter { matchesText(it.name, locator.value, locator.exact) }
        .map { it.id }
        .toList()
      is UiLocator.Role -> root.descendants()
        .filter {
          val expectedName = locator.name
          it.role == locator.role && (expectedName == null || matchesText(it.name, expectedName, locator.exactName))
        }
        .map { it.id }
        .toList()
      is UiLocator.Text -> root.descendants()
        .filter { node -> node.texts.any { matchesText(it, locator.value, locator.exact) } }
        .map { it.id }
        .toList()
      is UiLocator.ClassName -> root.descendants()
        .filter { it.className == locator.value || it.javaClassName == locator.value }
        .map { it.id }
        .toList()
      is UiLocator.All -> {
        require(locator.locators.isNotEmpty()) { "Combined locator must contain at least one selector" }
        val matches = locator.locators.map { find(it, root, xpathIds) }
        val remaining = matches.drop(1).map(List<Long>::toSet)
        matches.first().filter { id -> remaining.all { id in it } }
      }
      is UiLocator.Within -> {
        val ancestors = find(locator.ancestor, root, xpathIds).toSet()
        val descendantIds = root.descendants()
          .filter { it.id in ancestors }
          .flatMap { it.children.asSequence().flatMap(UiNode::descendants) }
          .mapTo(mutableSetOf()) { it.id }
        find(locator.descendant, root, xpathIds).filter { it in descendantIds }
      }
      is UiLocator.Nth -> {
        require(locator.index >= 0) { "Locator index must be >= 0, got ${locator.index}" }
        find(locator.locator, root, xpathIds).getOrNull(locator.index)?.let(::listOf).orEmpty()
      }
      is UiLocator.Xpath -> xpathIds(locator.value).toList()
    }
  }
}

private fun parseRole(value: String?): UiRole {
  val normalized = value?.trim()?.uppercase()?.replace(' ', '_') ?: return UiRole.UNKNOWN
  return when (normalized) {
    "PUSH_BUTTON", "BUTTON" -> UiRole.BUTTON
    "CHECK_BOX", "CHECKBOX" -> UiRole.CHECK_BOX
    "RADIO_BUTTON", "RADIOBUTTON" -> UiRole.RADIO_BUTTON
    "TEXT_AREA", "TEXTAREA" -> UiRole.TEXT
    "TAB" -> UiRole.PAGE_TAB
    else -> runCatching { UiRole.valueOf(normalized) }.getOrDefault(UiRole.UNKNOWN)
  }
}

internal fun replaceAccessibleText(component: Component, text: String) {
  val editableText = component.accessibleContext?.accessibleEditableText
                     ?: error("${component.javaClass.name} does not expose AccessibleEditableText")
  editableText.setTextContents(text)
}

private fun readAccessibleText(component: Component): String {
  val editableText = component.accessibleContext?.accessibleEditableText
                     ?: error("${component.javaClass.name} does not expose AccessibleEditableText")
  return if (editableText.charCount == 0) "" else editableText.getTextRange(0, editableText.charCount)
}

internal fun readAccessibleTextOrNull(component: Component, role: UiRole): String? {
  if (role == UiRole.PASSWORD_TEXT) return null
  val editableText = component.accessibleContext?.accessibleEditableText ?: return null
  return runCatching {
    if (editableText.charCount == 0) "" else editableText.getTextRange(0, editableText.charCount)
  }.getOrNull()
}

private fun parseState(value: String): UiState? {
  return runCatching { UiState.valueOf(value.trim().uppercase().replace(' ', '_')) }.getOrNull()
}

private fun createValue(attributes: Map<String, String>): UiValue? {
  val current = (attributes["accessiblevalue"] ?: attributes["progresscurrent"])?.toDoubleOrNull()
  val minimum = attributes["accessiblevaluemin"]?.toDoubleOrNull()
  val maximum = attributes["accessiblevaluemax"]?.toDoubleOrNull()
  if (current == null && minimum == null && maximum == null) return null
  return UiValue(current, minimum, maximum)
}

private fun describe(locator: UiLocator): String = when (locator) {
  is UiLocator.AccessibleName -> "accessible name '${locator.value}'"
  is UiLocator.Role -> "role ${locator.role}${locator.name?.let { " named '$it'" }.orEmpty()}"
  is UiLocator.Text -> "text '${locator.value}'"
  is UiLocator.ClassName -> "class '${locator.value}'"
  is UiLocator.All -> locator.locators.joinToString(prefix = "all of (", postfix = ")", transform = ::describe)
  is UiLocator.Within -> "${describe(locator.descendant)} within ${describe(locator.ancestor)}"
  is UiLocator.Nth -> "${describe(locator.locator)} at index ${locator.index}"
  is UiLocator.Xpath -> "XPath '${locator.value}'"
}

private fun Component.containingWindow(): Window? = this as? Window ?: SwingUtilities.getWindowAncestor(this)

private fun Component.componentClassName(): String {
  return generateSequence<Class<*>>(javaClass) { it.superclass }
           .map { it.simpleName }
           .firstOrNull(String::isNotBlank)
         ?: javaClass.name
}

private fun Component.windowRole(): UiRole = when (this) {
  is Dialog -> UiRole.DIALOG
  is Frame -> UiRole.FRAME
  is Window -> UiRole.WINDOW
  else -> UiRole.UNKNOWN
}

private fun Window.windowTitle(): String? {
  return when (this) {
    is Dialog -> title
    is Frame -> title
    else -> accessibleContext?.accessibleName
  }?.takeIf(String::isNotBlank)
}

private fun Window.ownerDepth(): Int = generateSequence(owner, Window::getOwner).count()

private fun deepestComponentAtIgnoringBareGlassPane(window: Window, pointInWindow: Point): Component? {
  val rootPane = (window as? RootPaneContainer)?.rootPane
                 ?: return SwingUtilities.getDeepestComponentAt(window, pointInWindow.x, pointInWindow.y)
  val pointInRootPane = SwingUtilities.convertPoint(window, pointInWindow, rootPane)
  return deepestComponentAtIgnoringBareGlassPane(rootPane, pointInRootPane)
}

internal fun deepestComponentAtIgnoringBareGlassPane(rootPane: JRootPane, pointInRootPane: Point): Component? {
  val hit = SwingUtilities.getDeepestComponentAt(rootPane, pointInRootPane.x, pointInRootPane.y)
  if (hit !== rootPane.glassPane) return hit
  val pointInLayeredPane = SwingUtilities.convertPoint(rootPane, pointInRootPane, rootPane.layeredPane)
  return SwingUtilities.getDeepestComponentAt(rootPane.layeredPane, pointInLayeredPane.x, pointInLayeredPane.y)
}

private fun isWindowOrOwnerActive(window: Window?): Boolean {
  val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
  return generateSequence(window, Window::getOwner).any { it === activeWindow || it.isActive }
}

private fun requestForeground(window: Window) {
  runCatching { Desktop.getDesktop().requestForeground(true) }
  window.toFront()
}

private fun describe(node: UiNode): String {
  val label = node.name ?: node.texts.firstOrNull() ?: "unnamed"
  return "${node.className}('$label', id=${node.id})"
}
