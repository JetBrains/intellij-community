// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.ide.KeyboardAwareFocusOwner
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.webview.impl.engine.WebViewFocusDirection
import com.intellij.ui.webview.impl.host.NativeWebViewHostPeer
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
import javax.swing.Timer

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
    private const val PAGE_FOCUS_ENTRY_FALLBACK_DELAY_MS = 100

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
    // Forward webview-owned editing shortcuts INTO the native web view: stop the IDE from consuming them
    // so the real keystroke reaches the focused WebView (browser default / page JS handles copy/paste/etc.).
    val skip = forwardsShortcutsToWebView && focusInsideHost &&
               WebViewEditCommand.matchingCommand(event.keyCode, event.modifiersEx, WebViewEditCommand.DEFAULTS) != null
    if (event.id == KeyEvent.KEY_PRESSED && event.modifiersEx != 0) {
      // TEMP diagnostic: pinpoint whether the IDE consults us for ⌘C and what we decide.
      WebViewLogger.LOG.info(
        "WebView skipKeyEventDispatcher: keyCode=${event.keyCode} modsEx=${event.modifiersEx} " +
        "forwards=$forwardsShortcutsToWebView focusInside=$focusInsideHost skip=$skip"
      )
    }
    return skip
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
  // IDE-first native backends (macOS WKWebView, Linux WebKitGTK) forward webview-owned shortcuts into the
  // web view via skipKeyEventDispatcher. Windows WebView2 is browser-first (forwards unhandled keys out
  // itself), so it is excluded.
  private val forwardsShortcutsToWebView: Boolean = nativePeer != null && !SystemInfo.isWindows
  private var focusInsideHost = false
  private var pendingExitDirection: WebViewFocusDirection? = null
  private var pageFocusHandledForCurrentActivation = false
  private var nativeFocusRestoreRequestedForCurrentActivation = false
  private var pageFocusFallbackTimer: Timer? = null
  private var nativePeerAttached = false
  private var heavyweightRegistration: Disposable? = null

  private val webViewFocusListener = object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      if (e.isTemporary) return
      val wasFocusInside = focusInsideHost
      focusInsideHost = true
      pendingExitDirection = null
      requestWebViewFocus()
      if (wasFocusInside) return

      val direction = e.cause.toWebViewFocusDirection() ?: return
      WebViewLogger.LOG.debug("WebView focus entered host: direction=$direction, cause=${e.cause}")
      enterWebViewFocus(direction)
    }

    override fun focusLost(e: FocusEvent) {
      if (e.isTemporary || containsFocusComponent(e.oppositeComponent)) return
      focusInsideHost = false
      pendingExitDirection = null
      pageFocusHandledForCurrentActivation = false
      nativeFocusRestoreRequestedForCurrentActivation = false
      cancelPageFocusEntryFallback()
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
    componentBackedEngine?.requestWebViewFocus() ?: nativePeer?.requestFocus()
  }

  override fun clearWebViewFocus() {
    componentBackedEngine?.clearWebViewFocus() ?: nativePeer?.clearFocus()
  }

  internal fun clearWebViewFocusForSwingFocusTransfer() {
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
      pageFocusHandledForCurrentActivation = true
      cancelPageFocusEntryFallback()
      activateWebViewFocusOnEdt(requestNativeFocus = true)
    }
  }

  internal fun nativeWebViewFocusGained() {
    runOnEdt {
      if (!isShowing) {
        WebViewLogger.LOG.debug("Ignoring native WebView focus gain because host is not showing")
        return@runOnEdt
      }
      activateWebViewFocusOnEdt(requestNativeFocus = false)
      if (!pageFocusHandledForCurrentActivation) {
        schedulePageFocusEntryFallback()
      }
      restoreNativeFocusAfterSwingActivation()
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

  private fun activateWebViewFocusOnEdt(requestNativeFocus: Boolean) {
    if (!isShowing) {
      WebViewLogger.LOG.debug("Ignoring WebView focus activation because host is not showing")
      return
    }

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    pendingExitDirection = null
    focusInsideHost = true
    if (!containsFocusComponent(focusManager.permanentFocusOwner)) {
      requestSwingFocusForWebViewActivation()
      if (requestNativeFocus) {
        SwingUtilities.invokeLater {
          if (isShowing && focusInsideHost) {
            requestWebViewFocus()
          }
        }
      }
    }
    else {
      if (requestNativeFocus) {
        requestWebViewFocus()
      }
    }
    WebViewLogger.LOG.debug("Applied WebView focus activation")
  }

  private fun exitWebViewFocusOnEdt(direction: WebViewFocusDirection) {
    if (!isShowing) {
      WebViewLogger.LOG.debug("Ignoring WebView focus exit because host is not showing: direction=$direction")
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
    WebViewLogger.LOG.debug("Applied WebView focus exit: direction=$direction")
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

    markWebViewFocusOutsideHost()
  }

  private fun markWebViewFocusOutsideHost() {
    focusInsideHost = false
    pendingExitDirection = null
    pageFocusHandledForCurrentActivation = false
    nativeFocusRestoreRequestedForCurrentActivation = false
    cancelPageFocusEntryFallback()
    clearWebViewFocusForSwingFocusTransfer()
  }

  private fun requestSwingFocusForWebViewActivation() {
    if (!requestFocusInWindow()) {
      IdeFocusManager.findInstanceByComponent(this).requestFocus(this, true)
    }
  }

  private fun activateWebViewFocusFromHostMouse() {
    if (!isShowing) return
    activateWebViewFocusOnEdt(requestNativeFocus = true)
    schedulePageFocusEntryFallback()
  }

  private fun enterWebViewFocus(direction: WebViewFocusDirection) {
    pageFocusHandledForCurrentActivation = true
    cancelPageFocusEntryFallback()
    focusEntrySink?.enterWebViewFocus(direction)
  }

  private fun schedulePageFocusEntryFallback() {
    if (pageFocusHandledForCurrentActivation || pageFocusFallbackTimer?.isRunning == true) return
    pageFocusFallbackTimer = Timer(PAGE_FOCUS_ENTRY_FALLBACK_DELAY_MS) {
      pageFocusFallbackTimer = null
      if (isShowing && focusInsideHost && !pageFocusHandledForCurrentActivation) {
        enterWebViewFocus(WebViewFocusDirection.FORWARD)
      }
    }.apply {
      isRepeats = false
      start()
    }
  }

  private fun cancelPageFocusEntryFallback() {
    pageFocusFallbackTimer?.stop()
    pageFocusFallbackTimer = null
  }

  private fun restoreNativeFocusAfterSwingActivation() {
    if (nativeFocusRestoreRequestedForCurrentActivation) return
    nativeFocusRestoreRequestedForCurrentActivation = true
    SwingUtilities.invokeLater {
      if (isShowing && focusInsideHost) {
        requestWebViewFocus()
      }
    }
  }

  private fun installComponentBackedFocusTraversal(component: Component) {
    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = SingleComponentFocusTraversalPolicy(component)
  }

  private fun containsFocusComponent(component: Component?): Boolean {
    return component === this || component != null && SwingUtilities.isDescendingFrom(component, this)
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
