// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {
  WEBVIEW_FOCUS_LEAVE_EVENT,
  type Callable,
  type WebViewApiId,
  type WebViewBridge,
  type WebViewFocusDirection,
  type WebViewFocusHostApi,
  type WebViewFocusPageApi,
} from "@jetbrains/intellij-webview"

const FOCUS_API_NAMESPACE = "webview.focus"
const FOCUS_BOUNDARY_ATTRIBUTE = "data-webview-focus-boundary"
const webViewFocusPageApiId = {namespace: FOCUS_API_NAMESPACE} as WebViewApiId<WebViewFocusPageApi>
const webViewFocusHostApiId = {namespace: FOCUS_API_NAMESPACE} as WebViewApiId<WebViewFocusHostApi>
type WebViewFocusHostCallable = Callable<WebViewFocusHostApi>

const installedBridges = new WeakSet<WebViewBridge>()
let focusTraceEventListenersInstalled = false
let hostFocusInsidePage = false

export function installWebViewFocusInterop(bridge: WebViewBridge): void {
  if (installedBridges.has(bridge)) {
    return
  }
  installedBridges.add(bridge)

  const hostApi = bridge.callable(webViewFocusHostApiId)
  logFocusEvent("install", () => ({activeElement: summarizeElement(activeElementDeep(document))}))
  installFocusTraceEventLogging()
  bridge.implement(webViewFocusPageApiId, {
    enter(params) {
      enterDocumentFocus(params.direction, hostApi)
    },
    leave() {
      leaveDocumentFocus()
    },
  })
  document.addEventListener("pointerdown", (event) => handlePointerActivation(event, hostApi), true)
  document.addEventListener("keydown", (event) => handleFocusBoundaryKey(event, hostApi), true)
}

function enterDocumentFocus(direction: WebViewFocusDirection, hostApi: WebViewFocusHostCallable): void {
  hostFocusInsidePage = true
  const tabbableElements = collectTabbableElements()
  logFocusEvent(`enter(${direction})`, () => ({
    tabbableCount: tabbableElements.length,
    activeElementBefore: summarizeElement(activeElementDeep(document)),
  }))
  if (tabbableElements.length === 0) {
    logFocusEvent("enter exits empty page", () => ({direction}))
    hostApi.exit({direction})
    return
  }

  const target = direction === "forward" ? tabbableElements[0] : tabbableElements[tabbableElements.length - 1]
  target.focus()
  logFocusEvent("enter applied", () => ({
    direction,
    target: summarizeElement(target),
    activeElementAfter: summarizeElement(activeElementDeep(document)),
  }))
}

function leaveDocumentFocus(): void {
  const activeBefore = activeElementDeep(document)
  const shouldDispatchWindowBlur = hostFocusInsidePage
  hostFocusInsidePage = false
  const blurredTargets = blurDocumentFocusTargets(activeBefore)
  logFocusEvent("leave", () => ({
    syntheticWindowBlur: shouldDispatchWindowBlur,
    activeElementBefore: summarizeElement(activeBefore),
    activeElementAfter: summarizeElement(activeElementDeep(document)),
    blurredTargets: blurredTargets.map(summarizeElement),
  }))
  dispatchFocusLeaveEvent()
  if (shouldDispatchWindowBlur && typeof window.dispatchEvent === "function") {
    window.dispatchEvent(createWindowBlurEvent())
  }
}

function dispatchFocusLeaveEvent(): void {
  if (typeof window.dispatchEvent === "function") {
    window.dispatchEvent(createFocusLeaveEvent())
  }
}

function createFocusLeaveEvent(): Event {
  return typeof CustomEvent === "function"
    ? new CustomEvent(WEBVIEW_FOCUS_LEAVE_EVENT)
    : new Event(WEBVIEW_FOCUS_LEAVE_EVENT)
}

function createWindowBlurEvent(): Event {
  return typeof FocusEvent === "function"
    ? new FocusEvent("blur", {relatedTarget: null})
    : new Event("blur")
}

function blurActiveElement(element: Element | null): void {
  if (!element || element === document.body || element === document.documentElement) {
    return
  }
  const blur = (element as HTMLElement).blur
  if (typeof blur === "function") {
    blur.call(element)
  }
}

function blurDocumentFocusTargets(activeElement: Element | null): Element[] {
  const targets = new Set<Element>()
  if (activeElement) {
    targets.add(activeElement)
  }
  collectFocusLeaveBlurTargets(document.body || document.documentElement, targets)

  const blurredTargets: Element[] = []
  for (const target of targets) {
    blurActiveElement(target)
    blurredTargets.push(target)
  }
  return blurredTargets
}

function collectFocusLeaveBlurTargets(root: ParentNode, targets: Set<Element>): void {
  for (const child of Array.from(root.children)) {
    if (isFocusLeaveBlurTarget(child)) {
      targets.add(child)
    }
    const shadowRoot = (child as HTMLElement).shadowRoot
    if (shadowRoot) {
      collectFocusLeaveBlurTargets(shadowRoot, targets)
    }
    collectFocusLeaveBlurTargets(child, targets)
  }
}

function isFocusLeaveBlurTarget(element: Element): boolean {
  const tagName = element.tagName.toLowerCase()
  return tagName === "select" || tagName === "input" || tagName === "textarea" || tagName === "button" || element.hasAttribute("tabindex")
}

function handlePointerActivation(event: PointerEvent, hostApi: WebViewFocusHostCallable): void {
  const focusTarget = findPointerFocusTarget(event)
  hostFocusInsidePage = true
  logFocusEvent("pointerdown", () => ({
    defaultPrevented: event.defaultPrevented,
    target: summarizeEventTarget(event),
    focusTarget: summarizeElement(focusTarget),
    activeElementBefore: summarizeElement(activeElementDeep(document)),
  }))
  hostApi.activated()
  logFocusEvent("host activated", () => ({
    defaultPrevented: event.defaultPrevented,
    activeElementAfter: summarizeElement(activeElementDeep(document)),
  }))
  if (focusTarget) {
    schedulePointerFocus(focusTarget, event)
  }
}

function findPointerFocusTarget(event: Event): HTMLElement | null {
  const path: readonly unknown[] = typeof event.composedPath === "function" ? event.composedPath() : [event.target]
  for (const item of path) {
    const element = asElement(item)
    if (!element) continue
    if (isInsideNativeFocusBoundary(element)) {
      return null
    }
    if (sequentialTabIndex(element) >= 0 && isRendered(element)) {
      return element as HTMLElement
    }
  }
  return null
}

function schedulePointerFocus(target: HTMLElement, event: PointerEvent): void {
  const focusTarget = () => {
    if (event.defaultPrevented) {
      logFocusEvent("pointer focus skipped: defaultPrevented", () => ({
        target: summarizeElement(target),
        activeElement: summarizeElement(activeElementDeep(document)),
      }))
      return
    }
    const activeBefore = activeElementDeep(document)
    const targetIsRendered = isRendered(target)
    const insideNativeBoundary = isInsideNativeFocusBoundary(target)
    if (targetIsRendered && !insideNativeBoundary && activeBefore !== target) {
      target.focus()
      logFocusEvent("pointer focus applied", () => ({
        target: summarizeElement(target),
        activeElementBefore: summarizeElement(activeBefore),
        activeElementAfter: summarizeElement(activeElementDeep(document)),
      }))
      return
    }

    logFocusEvent("pointer focus skipped", () => ({
      target: summarizeElement(target),
      targetIsRendered,
      insideNativeBoundary,
      activeElement: summarizeElement(activeBefore),
    }))
  }
  queueMicrotask(focusTarget)
}

function asElement(value: unknown): Element | null {
  if (typeof value !== "object" || value === null) {
    return null
  }
  return typeof (value as Element).tagName === "string" ? value as Element : null
}

function handleFocusBoundaryKey(event: KeyboardEvent, hostApi: WebViewFocusHostCallable): void {
  const isTab = event.key === "Tab"
  if (isTab) {
    logFocusEvent("keydown Tab", () => ({
      shiftKey: event.shiftKey,
      altKey: event.altKey,
      ctrlKey: event.ctrlKey,
      metaKey: event.metaKey,
      isComposing: event.isComposing,
      defaultPrevented: event.defaultPrevented,
      activeElement: summarizeElement(activeElementDeep(document)),
    }))
  }

  const plainTab = isPlainTabEvent(event)
  if (!plainTab || event.isComposing) {
    if (isTab) {
      logFocusEvent("keydown Tab ignored", () => ({reason: event.isComposing ? "composing" : "modified"}))
    }
    return
  }

  const activeElement = activeElementDeep(document)
  if (activeElement && isInsideNativeFocusBoundary(activeElement)) {
    logFocusEvent("keydown Tab stays in native boundary", () => ({activeElement: summarizeElement(activeElement)}))
    return
  }

  const tabbableElements = collectTabbableElements()
  const direction: WebViewFocusDirection = event.shiftKey ? "backward" : "forward"
  if (tabbableElements.length === 0) {
    logFocusEvent("keydown Tab exits empty page", () => ({direction}))
    event.preventDefault()
    hostApi.exit({direction})
    return
  }

  const boundary = direction === "forward" ? tabbableElements[tabbableElements.length - 1] : tabbableElements[0]
  if (activeElement !== boundary) {
    logFocusEvent("keydown Tab stays in page", () => ({
      direction,
      activeElement: summarizeElement(activeElement),
      boundary: summarizeElement(boundary),
      tabbableCount: tabbableElements.length,
    }))
    return
  }

  logFocusEvent("keydown Tab exits page", () => ({
    direction,
    boundary: summarizeElement(boundary),
    tabbableCount: tabbableElements.length,
  }))
  event.preventDefault()
  hostApi.exit({direction})
}

function installFocusTraceEventLogging(): void {
  if (focusTraceEventListenersInstalled) {
    return
  }
  focusTraceEventListenersInstalled = true

  const win = typeof window === "undefined" ? null : window
  if (win && typeof win.addEventListener === "function") {
    win.addEventListener("focus", (event) => {
      hostFocusInsidePage = true
      logFocusEvent("window focus", () => ({
        target: summarizeEventTarget(event),
        activeElement: summarizeElement(activeElementDeep(document)),
      }))
    }, true)
    win.addEventListener("blur", (event) => {
      hostFocusInsidePage = false
      logFocusEvent("window blur", () => ({
        target: summarizeEventTarget(event),
        activeElement: summarizeElement(activeElementDeep(document)),
      }))
    }, true)
  }

  document.addEventListener("focusin", (event) => {
    logFocusEvent("document focusin", () => ({
      target: summarizeEventTarget(event),
      activeElement: summarizeElement(activeElementDeep(document)),
    }))
  }, true)
  document.addEventListener("focusout", (event) => {
    logFocusEvent("document focusout", () => ({
      target: summarizeEventTarget(event),
      activeElement: summarizeElement(activeElementDeep(document)),
    }))
  }, true)
}

function logFocusEvent(eventName: string, details: () => Record<string, unknown> = () => ({})): void {
  if (typeof console === "undefined" || typeof console.debug !== "function") {
    return
  }
  const renderedDetails = formatFocusDetails(details())
  const suffix = renderedDetails ? `; ${renderedDetails}` : ""
  console.debug(`[wvi-focus] page ${eventName}${suffix}`)
}

function formatFocusDetails(details: Record<string, unknown>): string {
  return Object.entries(details)
    .map(([key, value]) => `${key}=${formatFocusValue(value)}`)
    .join(", ")
}

function formatFocusValue(value: unknown): string {
  if (value == null) {
    return String(value)
  }
  if (typeof value === "string") {
    return JSON.stringify(value)
  }
  if (typeof value === "number" || typeof value === "boolean" || typeof value === "bigint") {
    return String(value)
  }
  try {
    const json = JSON.stringify(value)
    return json === undefined ? String(value) : json
  }
  catch {
    return String(value)
  }
}

function summarizeEventTarget(event: Event): string {
  const target = asElement(event.target)
  if (target) {
    return summarizeElement(target)
  }
  if (typeof event.composedPath === "function") {
    for (const item of event.composedPath()) {
      const element = asElement(item)
      if (element) {
        return summarizeElement(element)
      }
    }
  }
  return "null"
}

function summarizeElement(element: Element | null | undefined): string {
  if (!element) {
    return "null"
  }

  const htmlElement = element as HTMLElement
  const id = htmlElement.id ? `#${htmlElement.id}` : ""
  const className = typeof htmlElement.className === "string" && htmlElement.className.trim()
    ? `.${htmlElement.className.trim().split(/\s+/).join(".")}`
    : ""
  const role = element.getAttribute("role")
  const ariaLabel = element.getAttribute("aria-label")
  const roleSummary = role ? `[role=${role}]` : ""
  const ariaSummary = ariaLabel ? `[aria-label=${ariaLabel}]` : ""
  return `${element.tagName.toLowerCase()}${id}${className}${roleSummary}${ariaSummary}`
}

function isPlainTabEvent(event: KeyboardEvent): boolean {
  return event.key === "Tab" && !event.altKey && !event.ctrlKey && !event.metaKey
}

export function collectTabbableElements(root: ParentNode = document.body || document.documentElement): HTMLElement[] {
  const candidates: TabbableCandidate[] = []
  let documentOrder = 0

  function visitChildren(parent: ParentNode): void {
    for (const child of Array.from(parent.children)) {
      visitElement(child)
    }
  }

  function visitElement(element: Element): void {
    const tabIndex = sequentialTabIndex(element)
    if (tabIndex >= 0 && isRendered(element)) {
      candidates.push({element: element as HTMLElement, tabIndex, documentOrder})
    }
    documentOrder++

    const shadowRoot = (element as HTMLElement).shadowRoot
    if (shadowRoot) {
      visitChildren(shadowRoot)
    }
    visitChildren(element)
  }

  visitChildren(root)
  return candidates
    .sort(compareTabbableCandidates)
    .map((candidate) => candidate.element)
}

interface TabbableCandidate {
  readonly element: HTMLElement
  readonly tabIndex: number
  readonly documentOrder: number
}

function compareTabbableCandidates(left: TabbableCandidate, right: TabbableCandidate): number {
  const leftPositive = left.tabIndex > 0
  const rightPositive = right.tabIndex > 0
  if (leftPositive && rightPositive && left.tabIndex !== right.tabIndex) {
    return left.tabIndex - right.tabIndex
  }
  if (leftPositive !== rightPositive) {
    return leftPositive ? -1 : 1
  }
  return left.documentOrder - right.documentOrder
}

function sequentialTabIndex(element: Element): number {
  if (isDisabledControl(element) || hasHiddenOrInertAncestor(element)) {
    return -1
  }

  const declaredTabIndex = parseDeclaredTabIndex(element)
  if (declaredTabIndex !== undefined) {
    return declaredTabIndex
  }
  return isNaturallyFocusable(element) ? 0 : -1
}

function parseDeclaredTabIndex(element: Element): number | undefined {
  const value = element.getAttribute("tabindex")
  if (value == null) {
    return undefined
  }
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) ? parsed : undefined
}

function isNaturallyFocusable(element: Element): boolean {
  const tagName = element.tagName.toLowerCase()
  if (tagName === "input") {
    return (element.getAttribute("type") || "").toLowerCase() !== "hidden"
  }
  if (
    tagName === "button" || tagName === "select" || tagName === "textarea" || tagName === "iframe" ||
    tagName === "object" || tagName === "embed" || tagName === "summary"
  ) {
    return true
  }
  if ((tagName === "a" || tagName === "area") && element.hasAttribute("href")) {
    return true
  }
  if ((tagName === "audio" || tagName === "video") && element.hasAttribute("controls")) {
    return true
  }
  return isContentEditable(element)
}

function isContentEditable(element: Element): boolean {
  const value = element.getAttribute("contenteditable")
  return value != null && value.toLowerCase() !== "false"
}

function isDisabledControl(element: Element): boolean {
  const tagName = element.tagName.toLowerCase()
  return (tagName === "button" || tagName === "input" || tagName === "select" || tagName === "textarea") &&
    element.hasAttribute("disabled")
}

function hasHiddenOrInertAncestor(element: Element): boolean {
  for (let current: Element | null = element; current; current = parentElementOrShadowHost(current)) {
    if (current.hasAttribute("hidden") || current.hasAttribute("inert")) {
      return true
    }
  }
  return false
}

function isRendered(element: Element): boolean {
  for (let current: Element | null = element; current; current = parentElementOrShadowHost(current)) {
    const style = window.getComputedStyle(current)
    if (style.display === "none" || style.visibility === "hidden" || style.visibility === "collapse") {
      return false
    }
  }

  const htmlElement = element as HTMLElement
  return htmlElement.offsetWidth > 0 || htmlElement.offsetHeight > 0 || htmlElement.getClientRects().length > 0
}

function parentElementOrShadowHost(element: Element): Element | null {
  if (element.parentElement) {
    return element.parentElement
  }

  const root = element.getRootNode()
  return typeof ShadowRoot === "function" && root instanceof ShadowRoot ? root.host : null
}

function activeElementDeep(root: Document | ShadowRoot): Element | null {
  let activeElement = root.activeElement
  while (activeElement?.shadowRoot?.activeElement) {
    activeElement = activeElement.shadowRoot.activeElement
  }
  return activeElement
}

function isInsideNativeFocusBoundary(element: Element): boolean {
  for (let current: Element | null = element; current; current = parentElementOrShadowHost(current)) {
    if (current.getAttribute(FOCUS_BOUNDARY_ATTRIBUTE) === "native") {
      return true
    }
  }
  return false
}
