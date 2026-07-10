// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ide.KeyboardAwareFocusOwner
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.webview.impl.engine.WebViewFocusDirection
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
import com.intellij.ui.webview.impl.host.WebViewEditShortcutPolicy
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyBoundsListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

private val LOG = logger<SwingWebViewHostPanel>()

/**
 * Swing host panel that manages the lifecycle of a native [WebViewEngineBridge].
 *
 * The native WebView is attached in [addNotify] when the panel joins a displayable Swing
 * hierarchy, and detached in [removeNotify] when the panel is removed. The first native show
 * is delayed until Swing has a showing, non-empty host rectangle. Resize and
 * visibility events are forwarded to the native view with coalescing to avoid redundant native calls.
 *
 * **Threading**: Must be created and used on the EDT. The [scope] is used for
 * coroutine-based lifecycle management; native calls are internally dispatched
 * to the owning native UI thread.
 */
@ApiStatus.Internal
internal class SwingWebViewHostPanel(
  val scope: CoroutineScope,
  val engine: WebViewEngineBridge,
  private val focusEntrySink: WebViewFocusEntrySink? = null,
  nativeHostPeer: NativeWebViewHostPeer? = null,
) : JPanel(BorderLayout()), SwingWebViewHost, KeyboardAwareFocusOwner {

  internal data class NativeFrame(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
  )

  internal data class NativeBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
  )

  internal companion object {
    private const val HOST_MOUSE_NATIVE_FOCUS_SUPPRESSION_NANOS = 500_000_000L

    fun calculateNativeFrame(host: Component, anchor: Component): NativeFrame {
      val hostOrigin = SwingUtilities.convertPoint(host, 0, 0, anchor)
      val width = host.width.toDouble()
      val height = host.height.toDouble()
      val flippedY = anchor.height.toDouble() - hostOrigin.y.toDouble() - height

      return NativeFrame(
        x = hostOrigin.x.toDouble(),
        y = flippedY,
        width = width,
        height = height,
      )
    }

    fun calculateWindowsBounds(host: Component, anchor: Component): NativeBounds {
      val hostOrigin = SwingUtilities.convertPoint(host, 0, 0, anchor)
      val visibleClip = calculateVisibleClip(host, anchor, hostOrigin)
      return NativeBounds(
        x = visibleClip.left,
        y = visibleClip.top,
        width = (visibleClip.right - visibleClip.left).coerceAtLeast(0),
        height = (visibleClip.bottom - visibleClip.top).coerceAtLeast(0),
      )
    }

    private data class VisibleClip(
      val left: Int,
      val top: Int,
      val right: Int,
      val bottom: Int,
    )

    private fun calculateVisibleClip(host: Component, anchor: Component, hostOrigin: Point): VisibleClip {
      var left = hostOrigin.x
      var top = hostOrigin.y
      var right = hostOrigin.x + host.width
      var bottom = hostOrigin.y + host.height

      for (component in host.selfAndAncestorsUntil(anchor)) {
        val parent = component.parent ?: continue
        val parentOrigin = SwingUtilities.convertPoint(parent, 0, 0, anchor)
        if (!parent.isWindowsRootBoundary(anchor)) {
          left = maxOf(left, parentOrigin.x)
          top = maxOf(top, parentOrigin.y)
          right = minOf(right, parentOrigin.x + parent.width)
          bottom = minOf(bottom, parentOrigin.y + parent.height)
        }

      }
      return VisibleClip(left, top, right, bottom)
    }

    private fun Component.selfAndAncestorsUntil(anchor: Component): Sequence<Component> {
      return generateSequence(this) { component -> component.parent }
        .takeWhile { component -> component !== anchor }
    }

    private fun Component.isWindowsRootBoundary(anchor: Component): Boolean {
      if (this === anchor) return true
      return anchor is JRootPane && (this === anchor.contentPane || this === anchor.layeredPane || this === anchor.glassPane)
    }

    internal fun resolveAnchor(component: Component): Component? {
      val window = SwingUtilities.getWindowAncestor(component) ?: return null
      return if (window is RootPaneContainer) window.contentPane else window
    }

    internal fun resolveWindowsAnchor(component: Component): Component? {
      val window = SwingUtilities.getWindowAncestor(component) ?: return null
      return if (window is RootPaneContainer) window.rootPane else window
    }

    internal fun hasNonEmptyClippedBounds(host: Component): Boolean {
      val anchor = resolveWindowsAnchor(host) ?: return false
      val bounds = calculateWindowsBounds(host, anchor)
      return bounds.width > 0 && bounds.height > 0
    }
  }

  override val component: JComponent
    get() = this

  override fun skipKeyEventDispatcher(event: KeyEvent): Boolean {
    val peer = nativePeer ?: return false
    val policy = peer.editShortcutPolicy
    if (policy == WebViewEditShortcutPolicy.NONE || !focusInsideHost) return false

    val command = WebViewEditCommand.matchingCommand(event.keyCode, event.modifiersEx, WebViewEditCommand.DEFAULTS) ?: return false
    // Returning true only keeps the IDE dispatcher out of this shortcut. The backend policy decides
    // whether the original native event path handles it or an explicit native command is required.
    if (policy == WebViewEditShortcutPolicy.HANDLE_IN_NATIVE_PEER && peer.handleWebViewShortcut(event, command)) {
      event.consume()
    }
    return true
  }

  private var hierarchyListener: HierarchyListener? = null
  private var hierarchyBoundsListener: HierarchyBoundsListener? = null
  private var ancestorContainerListener: ContainerAdapter? = null
  private val ancestorContainersWithListener = ArrayList<Container>()
  private var focusTransferListener: AWTEventListener? = null
  private var swingFocusOwnerListener: PropertyChangeListener? = null
  private var listenersInstalled = false
  private var snapshotImage: BufferedImage? = null
  private val componentBackedEngine = engine as? ComponentBackedWebViewEngine
  private val nativePeer = if (componentBackedEngine == null) nativeHostPeer else null
  private var focusInsideHost = false
  private var pendingExitDirection: WebViewFocusDirection? = null
  private var pageFocusHandledForCurrentActivation = false
  private var hostMouseActivationNanos = 0L
  private var nativePeerAttached = false
  private var heavyweightRegistration: Disposable? = null
  private val focusLogId = Integer.toHexString(System.identityHashCode(this))

  private val webViewFocusListener = object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      if (e.isTemporary) return
      val cause = e.cause
      val wasFocusInside = focusInsideHost
      logFocus(
        "focusGained",
        "cause=$cause, opposite=${componentDiagnostics(e.oppositeComponent)}, " +
        "wasFocusInside=$wasFocusInside, ${focusDiagnostics()}",
      )
      focusInsideHost = true
      pendingExitDirection = null
      if (shouldRequestNativeFocusOnSwingFocusGained(cause)) {
        requestWebViewFocus()
      }
      if (wasFocusInside) return

      val direction = cause.toWebViewFocusDirection() ?: return
      logFocus("entered", "direction=$direction, cause=$cause")
      enterWebViewFocus(direction)
    }

    override fun focusLost(e: FocusEvent) {
      logFocus(
        "focusLost",
        "cause=${e.cause}, temporary=${e.isTemporary}, " +
        "opposite=${componentDiagnostics(e.oppositeComponent)}, ${focusDiagnostics()}",
      )
      if (e.isTemporary || containsFocusComponent(e.oppositeComponent)) return
      markWebViewFocusOutsideHost()
    }
  }

  init {
    // Native heavyweight WebViews cover the panel; painting the default grey
    // Panel.background would flash through transient gaps during live resize.
    isOpaque = false
    isFocusable = true
    isRequestFocusEnabled = true
    addFocusListener(webViewFocusListener)
    componentBackedEngine?.let {
      installComponentBackedFocusTraversal(it.component)
      it.component.addFocusListener(webViewFocusListener)
      add(it.component, BorderLayout.CENTER)
    }
  }

  private val resizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) = nativeHostBoundsChanged()
    override fun componentMoved(e: ComponentEvent) = nativeHostBoundsChanged()
    override fun componentShown(e: ComponentEvent) = updateVisibility(false)
    override fun componentHidden(e: ComponentEvent) = updateVisibility(true)
  }

  @RequiresEdt
  override fun addNotify() {
    super.addNotify()
    installListeners()
    ensureNativePeerAttached()
    syncNativePeerFromSwingEvent(allowReveal = false)
    syncWebViewFocusWithSwingFocusOwner()
  }

  @RequiresEdt
  override fun removeNotify() {
    unregisterHeavyweight()
    if (nativePeerAttached) {
      nativePeer?.detach()
    }
    nativePeerAttached = false
    uninstallListeners()
    super.removeNotify()
  }

  @RequiresEdt
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun reshape(x: Int, y: Int, w: Int, h: Int) {
    super.reshape(x, y, w, h)
    nativeHostBoundsChanged()
  }

  @RequiresEdt
  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val image = snapshotImage ?: return
    g.drawImage(image, 0, 0, width, height, null)
  }

  @RequiresEdt
  private fun scheduleFrameUpdate() {
    if (ensureNativePeerAttached()) {
      nativePeer?.scheduleFrameUpdate(this)
    }
  }

  @RequiresEdt
  private fun updateVisibility(hidden: Boolean) {
    if (hidden) {
      nativePeer?.updateVisibility(this, true)
      notifyHeavyweightChanged()
    }
    else {
      syncNativePeerFromSwingEvent(allowReveal = true)
    }
  }

  @RequiresEdt
  private fun nativeHostBoundsChanged() {
    syncNativePeerFromSwingEvent(allowReveal = true)
  }

  @RequiresEdt
  private fun ensureNativePeerAttached(): Boolean {
    val peer = nativePeer ?: return false
    if (nativePeerAttached) return true
    if (!isDisplayable) return false
    nativePeerAttached = peer.attach(this)
    if (nativePeerAttached) {
      ensureHeavyweightRegistered()
    }
    return nativePeerAttached
  }

  @RequiresEdt
  private fun syncNativePeerFromSwingEvent(allowReveal: Boolean) {
    val peer = nativePeer ?: return
    if (!ensureNativePeerAttached()) return

    scheduleFrameUpdate()
    notifyHeavyweightChanged()
    if (!allowReveal || !isReadyForNativeDisplay(peer)) {
      peer.updateVisibility(this, true)
      return
    }

    peer.updateVisibility(this, false)
  }

  @RequiresEdt
  private fun ensureHeavyweightRegistered() {
    if (!engine.isHeavyweight || heavyweightRegistration != null || !nativePeerAttached) return
    heavyweightRegistration = WebViewHeavyweightHostRegistry.register(this)
  }

  @RequiresEdt
  private fun unregisterHeavyweight() {
    heavyweightRegistration?.let { Disposer.dispose(it) }
    heavyweightRegistration = null
  }

  @RequiresEdt
  private fun notifyHeavyweightChanged() {
    heavyweightRegistration?.let {
      WebViewHeavyweightHostRegistry.componentChanged(this)
    }
  }

  private fun isReadyForNativeDisplay(peer: NativeWebViewHostPeer): Boolean {
    return isDisplayable && isShowing && width > 0 && height > 0 && peer.hasNonEmptyNativeBounds(this)
  }

  override fun requestWebViewFocus() {
    logFocus("request.webViewFocus", focusDiagnostics())
    requestSwingFocusForNativeWebViewFocusIfNeeded()
    requestNativeWebViewFocus()
  }

  private fun requestNativeWebViewFocus() {
    logFocus("request.nativeFocus", focusDiagnostics())
    componentBackedEngine?.requestWebViewFocus() ?: nativePeer?.requestFocus()
  }

  override fun clearWebViewFocus() {
    componentBackedEngine?.clearWebViewFocus() ?: nativePeer?.clearFocus()
  }

  internal fun clearWebViewFocusForSwingFocusTransfer() {
    logFocus("clear.nativeFocusForSwingTransfer", focusDiagnostics())
    val componentEngine = componentBackedEngine
    if (componentEngine != null) {
      componentEngine.clearWebViewFocus()
    }
    else {
      nativePeer?.clearFocusForSwingFocusTransfer()
    }
  }

  internal fun exitWebViewFocus(direction: WebViewFocusDirection) {
    runOnEdt {
      exitWebViewFocusOnEdt(direction)
    }
  }

  internal fun activateWebViewFocus() {
    runOnEdt {
      logFocus("page.activated", focusDiagnostics())
      // This is called from the page-side pointerdown focus interop handler. The native WebView
      // already owns the mouse event, so avoid a programmatic native focus move in the same click.
      rememberHostMouseActivation()
      pageFocusHandledForCurrentActivation = true
      activateWebViewFocusOnEdt()
    }
  }

  internal fun nativeWebViewFocusGained() {
    runOnEdt {
      logFocus(
        "native.focusGained",
        "pageFocusHandled=$pageFocusHandledForCurrentActivation, ${focusDiagnostics()}",
      )
      if (!isShowing) {
        logFocus("native.focusGained.ignored", "reason=host-not-showing")
        return@runOnEdt
      }
      // WebView2 has already reported native focus here. We still synchronize Swing's focus owner
      // to the host panel, but we must not follow that with another native focus request: on Windows
      // WebView2 observes the extra MoveFocus(PROGRAMMATIC) as a blur/focus bounce, which closes
      // click-opened browser/Radix-style popups.
      activateWebViewFocusOnEdt()
    }
  }

  internal fun activateWebViewFocusFromNativeMouse() {
    // Windows sends mouse activation to the native WebView2 child before Swing sees a normal AWT
    // focus event. Run the Swing host activation synchronously on EDT while the native window proc
    // is still handling that mouse activation, so Swing's focus owner is correct before WebView2
    // dispatches the page pointer event. This must not request native focus; the click already does.
    if (EDT.isCurrentThreadEdt()) {
      logFocus("native.mouseActivation", "thread=edt, ${focusDiagnostics()}")
      activateWebViewFocusFromMouseOnEdt()
    }
    else {
      SwingUtilities.invokeAndWait {
        logFocus(
          "native.mouseActivation",
          "thread=invokeAndWait, ${focusDiagnostics()}",
        )
        activateWebViewFocusFromMouseOnEdt()
      }
    }
  }

  internal fun syncWebViewFocusWithSwingFocusOwner() {
    runOnEdt {
      markWebViewFocusOutsideIfSwingFocusMovedOutside(KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner)
    }
  }

  internal fun syncNativePeerWithSwingState() {
    runOnEdt {
      syncNativePeerFromSwingEvent(allowReveal = true)
    }
  }

  private fun runOnEdt(action: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) {
      action()
    }
    else {
      SwingUtilities.invokeLater {
        action()
      }
    }
  }

  private fun activateWebViewFocusOnEdt() {
    if (!isShowing) {
      logFocus("activation.ignored", "reason=host-not-showing")
      return
    }

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    pendingExitDirection = null
    focusInsideHost = true
    if (!containsFocusComponent(focusManager.permanentFocusOwner)) {
      // Browser-owned activation enters here while the page/native view is already processing a
      // mouse event. Keep this to an in-window Swing-owner synchronization attempt; a forced focus
      // request can re-enter native window focus handling in the same pointer pipeline.
      requestSwingFocusForWebViewActivation(allowForcedFocusFallback = false)
    }
    logFocus("activation.applied", focusDiagnostics())
  }

  private fun exitWebViewFocusOnEdt(direction: WebViewFocusDirection) {
    if (!isShowing) {
      logFocus("exit.ignored", "reason=host-not-showing, direction=$direction")
      return
    }
    if (pendingExitDirection == direction) return

    pendingExitDirection = direction
    clearWebViewFocusForSwingFocusTransfer()
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    when (direction) {
      WebViewFocusDirection.FORWARD -> focusManager.focusNextComponent(this)
      WebViewFocusDirection.BACKWARD -> focusManager.focusPreviousComponent(this)
    }
    logFocus("exit.applied", "direction=$direction, ${focusDiagnostics()}")
  }

  internal fun setSnapshotImage(width: Int, height: Int, pixels: IntArray) {
    if (width <= 0 || height <= 0 || pixels.isEmpty()) {
      clearSnapshotImage()
      return
    }

    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
    val target = (image.raster.dataBuffer as DataBufferInt).data
    pixels.copyInto(target, endIndex = minOf(target.size, pixels.size))
    snapshotImage = image
    repaint()
  }

  internal fun clearSnapshotImage() {
    snapshotImage = null
    repaint()
  }

  private fun installListeners() {
    if (listenersInstalled) return
    addComponentListener(resizeListener)
    val listener = HierarchyListener { e ->
      if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
        updateVisibility(!isShowing)
      }
    }
    hierarchyListener = listener
    addHierarchyListener(listener)
    val boundsListener = object : HierarchyBoundsAdapter() {
      override fun ancestorMoved(e: HierarchyEvent) = nativeHostBoundsChanged()
      override fun ancestorResized(e: HierarchyEvent) = nativeHostBoundsChanged()
    }
    hierarchyBoundsListener = boundsListener
    addHierarchyBoundsListener(boundsListener)
    installAncestorContainerListeners()
    val focusListener = AWTEventListener { event ->
      if (event !is MouseEvent || event.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
      val source = event.component ?: return@AWTEventListener
      val hostWindow = SwingUtilities.getWindowAncestor(this) ?: return@AWTEventListener
      if (SwingUtilities.getWindowAncestor(source) != hostWindow) return@AWTEventListener
      if (source === this) {
        activateWebViewFocusFromHostMouse()
        return@AWTEventListener
      }
      if (SwingUtilities.isDescendingFrom(source, this)) return@AWTEventListener
      markWebViewFocusOutsideHost()
    }
    focusTransferListener = focusListener
    Toolkit.getDefaultToolkit().addAWTEventListener(focusListener, AWTEvent.MOUSE_EVENT_MASK)
    val focusOwnerListener = PropertyChangeListener { event ->
      markWebViewFocusOutsideIfSwingFocusMovedOutside(event.newValue as? Component)
    }
    swingFocusOwnerListener = focusOwnerListener
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    focusManager.addPropertyChangeListener("permanentFocusOwner", focusOwnerListener)
    markWebViewFocusOutsideIfSwingFocusMovedOutside(focusManager.permanentFocusOwner)
    listenersInstalled = true
  }

  private fun uninstallListeners() {
    if (!listenersInstalled) return
    removeComponentListener(resizeListener)
    hierarchyListener?.let {
      removeHierarchyListener(it)
      hierarchyListener = null
    }
    hierarchyBoundsListener?.let {
      removeHierarchyBoundsListener(it)
      hierarchyBoundsListener = null
    }
    uninstallAncestorContainerListeners()
    focusTransferListener?.let {
      Toolkit.getDefaultToolkit().removeAWTEventListener(it)
      focusTransferListener = null
    }
    swingFocusOwnerListener?.let {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("permanentFocusOwner", it)
      swingFocusOwnerListener = null
    }
    listenersInstalled = false
  }

  private fun installAncestorContainerListeners() {
    val listener = object : ContainerAdapter() {
      override fun componentAdded(e: ContainerEvent) = nativeHostBoundsChanged()
      override fun componentRemoved(e: ContainerEvent) = nativeHostBoundsChanged()
    }
    ancestorContainerListener = listener
    generateSequence(this as Component?) { it.parent }
      .filterIsInstance<Container>()
      .forEach { container ->
        container.addContainerListener(listener)
        ancestorContainersWithListener.add(container)
      }
  }

  private fun uninstallAncestorContainerListeners() {
    val listener = ancestorContainerListener ?: return
    ancestorContainersWithListener.forEach { container ->
      container.removeContainerListener(listener)
    }
    ancestorContainersWithListener.clear()
    ancestorContainerListener = null
  }

  private fun markWebViewFocusOutsideIfSwingFocusMovedOutside(focusOwner: Component?) {
    if (focusOwner == null || containsFocusComponent(focusOwner)) return
    val hostWindow = SwingUtilities.getWindowAncestor(this) ?: return
    if (SwingUtilities.getWindowAncestor(focusOwner) != hostWindow) return

    logFocus(
      "swingFocus.outsideHost",
      "newOwner=${componentDiagnostics(focusOwner)}, ${focusDiagnostics()}",
    )
    markWebViewFocusOutsideHost()
  }

  private fun markWebViewFocusOutsideHost() {
    // Every WebView host installed in the same Swing window observes the global permanent-focus-owner
    // change. When focus moves into one WebView host, all sibling hosts see that owner as "outside".
    // Only the host that previously owned focus should clear native browser focus; an already-outside
    // host calling the Windows clear path would SetFocus(parent) and can blur the newly activated
    // WebView2 page while it is opening a pointer-triggered popup.
    if (!focusInsideHost && !pageFocusHandledForCurrentActivation && pendingExitDirection == null) {
      logFocus("mark.outsideHost.skipped", "reason=already-outside, ${focusDiagnostics()}")
      return
    }

    logFocus("mark.outsideHost", focusDiagnostics())
    focusInsideHost = false
    pendingExitDirection = null
    pageFocusHandledForCurrentActivation = false
    hostMouseActivationNanos = 0L
    leaveWebViewFocus()
    clearWebViewFocusForSwingFocusTransfer()
  }

  private fun leaveWebViewFocus() {
    logFocus("page.leave", focusDiagnostics())
    focusEntrySink?.leaveWebViewFocus()
  }

  internal fun requestSwingFocusForWebViewActivation(allowForcedFocusFallback: Boolean): Boolean {
    val requested = requestFocusInWindow()
    logFocus(
      "request.swingHostFocus",
      "requestFocusInWindow=$requested, allowForcedFocusFallback=$allowForcedFocusFallback, ${focusDiagnostics()}",
    )
    if (!requested && allowForcedFocusFallback) {
      // Keyboard/traversal entry starts from Swing and may need the IDE focus manager to make the
      // host the AWT focus owner before we explicitly move native focus into the WebView.
      IdeFocusManager.findInstanceByComponent(this).requestFocus(this, true)
      return true
    }
    if (!requested) {
      logFocus("request.swingHostFocus.skipped", "reason=forced-focus-disabled, ${focusDiagnostics()}")
    }
    return false
  }

  private fun requestSwingFocusForNativeWebViewFocusIfNeeded() {
    if (!isShowing || containsFocusComponent(KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner)) return

    // A native heavyweight WebView is not an AWT child component, so Windows can move focus into
    // the WebView2 HWND while Swing still believes the previous editor/toolwindow component owns
    // focus. That stale Swing focus owner breaks IDE-level focus traversal and shortcut routing.
    // Keep the Swing host panel as the AWT focus owner before asking the native engine for focus.
    requestSwingFocusForWebViewActivation(allowForcedFocusFallback = true)
  }

  private fun activateWebViewFocusFromHostMouse() {
    logFocus("mouseActivation", focusDiagnostics())
    activateWebViewFocusFromMouseOnEdt()
  }

  private fun activateWebViewFocusFromMouseOnEdt() {
    if (!isShowing) return
    rememberHostMouseActivation()
    activateWebViewFocusOnEdt()
  }

  private fun rememberHostMouseActivation() {
    hostMouseActivationNanos = System.nanoTime()
  }

  /**
   * Returns whether a Swing focus gain should be mirrored to the native WebView with an explicit
   * native focus request.
   *
   * There are two different focus-entry paths and they must not be handled the same way:
   *
   * 1. Keyboard/traversal entry starts in Swing. In that case the Swing host becomes focused first,
   *    while the browser/native child window may still not own native focus. We must call
   *    [requestWebViewFocus] so the next keyboard event is delivered to the WebView rather than to
   *    the surrounding IDE component.
   *
   * 2. Mouse entry starts in the native WebView window. The mouse event is already being processed by
   *    WebView2 and the browser will perform its normal focus/default-action handling for that click.
   *    Calling native focus again from Swing during the same pointer pipeline is observable by the
   *    page as a transient `window.blur`/`window.focus` bounce on Windows WebView2. Browser popups and
   *    Radix-style custom popups often close on `window.blur`, so that extra programmatic focus move
   *    makes a click-opened popup flash and immediately close.
   *
   * [FocusEvent.Cause.MOUSE_EVENT] covers the direct Swing focus event. [isHostMouseActivationInProgress]
   * covers the asynchronous case where the page-side pointerdown focus interop callback or native
   * focus-gained callback reaches Swing before/around the Swing focus event. The short timestamp
   * window lets us keep the whole mouse activation as one browser-owned operation without changing
   * keyboard/traversal behavior after the click has settled.
   */
  private fun shouldRequestNativeFocusOnSwingFocusGained(cause: FocusEvent.Cause): Boolean {
    return cause != FocusEvent.Cause.MOUSE_EVENT && !isHostMouseActivationInProgress()
  }

  private fun isHostMouseActivationInProgress(): Boolean {
    val activationNanos = hostMouseActivationNanos
    return activationNanos != 0L && System.nanoTime() - activationNanos <= HOST_MOUSE_NATIVE_FOCUS_SUPPRESSION_NANOS
  }

  private fun enterWebViewFocus(direction: WebViewFocusDirection) {
    pageFocusHandledForCurrentActivation = true
    logFocus("page.enter", "direction=$direction, ${focusDiagnostics()}")
    focusEntrySink?.enterWebViewFocus(direction)
  }

  private fun installComponentBackedFocusTraversal(component: Component) {
    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = SingleComponentFocusTraversalPolicy(component)
  }

  private fun containsFocusComponent(component: Component?): Boolean {
    return component === this || component != null && SwingUtilities.isDescendingFrom(component, this)
  }

  private fun logFocus(event: String, details: String = "") {
    val detailsWithHostId = if (details.isEmpty()) "hostId=$focusLogId" else "hostId=$focusLogId, $details"
    LOG.debug("[wvi-focus] host $event; $detailsWithHostId")
  }

  private fun focusDiagnostics(): String {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return "focusInsideHost=$focusInsideHost, pageFocusHandled=$pageFocusHandledForCurrentActivation, " +
           "mouseActivationInProgress=${isHostMouseActivationInProgress()}, " +
           "focusOwner=${componentDiagnostics(focusManager.focusOwner)}, " +
           "permanentFocusOwner=${componentDiagnostics(focusManager.permanentFocusOwner)}"
  }

  private fun componentDiagnostics(component: Component?): String {
    if (component == null) return "null"
    val relation = when {
      component === this -> "host"
      SwingUtilities.isDescendingFrom(component, this) -> "inside-host"
      else -> "outside-host"
    }
    return "${component.javaClass.name}@${Integer.toHexString(System.identityHashCode(component))}#$relation"
  }

  private fun FocusEvent.Cause.toWebViewFocusDirection(): WebViewFocusDirection? {
    return when (this) {
      FocusEvent.Cause.TRAVERSAL_FORWARD -> WebViewFocusDirection.FORWARD
      FocusEvent.Cause.TRAVERSAL_BACKWARD -> WebViewFocusDirection.BACKWARD
      else -> null
    }
  }

  private class SingleComponentFocusTraversalPolicy(
    private val component: Component,
  ) : FocusTraversalPolicy() {
    override fun getComponentAfter(aContainer: Container, aComponent: Component): Component = component

    override fun getComponentBefore(aContainer: Container, aComponent: Component): Component = component

    override fun getFirstComponent(aContainer: Container): Component = component

    override fun getLastComponent(aContainer: Container): Component = component

    override fun getDefaultComponent(aContainer: Container): Component = component
  }
}
