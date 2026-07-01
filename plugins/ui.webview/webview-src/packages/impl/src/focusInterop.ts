// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {
  Callable,
  WebViewApiId,
  WebViewBridge,
  WebViewFocusDirection,
  WebViewFocusHostApi,
  WebViewFocusPageApi,
} from "@jetbrains/intellij-webview"

const FOCUS_API_NAMESPACE = "webview.focus"
const FOCUS_BOUNDARY_ATTRIBUTE = "data-webview-focus-boundary"
const webViewFocusPageApiId = { namespace: FOCUS_API_NAMESPACE } as WebViewApiId<WebViewFocusPageApi>
const webViewFocusHostApiId = { namespace: FOCUS_API_NAMESPACE } as WebViewApiId<WebViewFocusHostApi>
type WebViewFocusHostCallable = Callable<WebViewFocusHostApi>

const installedBridges = new WeakSet<WebViewBridge>()

export function installWebViewFocusInterop(bridge: WebViewBridge): void {
  if (installedBridges.has(bridge)) {
    return
  }
  installedBridges.add(bridge)

  const hostApi = bridge.callable(webViewFocusHostApiId)
  bridge.implement(webViewFocusPageApiId, {
    enter(params) {
      enterDocumentFocus(params.direction, hostApi)
    },
  })
  document.addEventListener("pointerdown", (event) => handlePointerActivation(event, hostApi), true)
  document.addEventListener("keydown", (event) => handleFocusBoundaryKey(event, hostApi), true)
}

function enterDocumentFocus(direction: WebViewFocusDirection, hostApi: WebViewFocusHostCallable): void {
  const tabbableElements = collectTabbableElements()
  if (tabbableElements.length === 0) {
    hostApi.exit({ direction })
    return
  }

  const target = direction === "forward" ? tabbableElements[0] : tabbableElements[tabbableElements.length - 1]
  target.focus()
}

function handlePointerActivation(event: PointerEvent, hostApi: WebViewFocusHostCallable): void {
  const focusTarget = findPointerFocusTarget(event)
  hostApi.activated()
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
      return
    }
    if (isRendered(target) && !isInsideNativeFocusBoundary(target) && activeElementDeep(document) !== target) {
      target.focus()
    }
  }
  queueMicrotask(focusTarget)
  setTimeout(focusTarget, 0)
}

function asElement(value: unknown): Element | null {
  if (typeof value !== "object" || value === null) {
    return null
  }
  return typeof (value as Element).tagName === "string" ? value as Element : null
}

function handleFocusBoundaryKey(event: KeyboardEvent, hostApi: WebViewFocusHostCallable): void {
  if (!isPlainTabEvent(event) || event.isComposing) {
    return
  }

  const activeElement = activeElementDeep(document)
  if (activeElement && isInsideNativeFocusBoundary(activeElement)) {
    return
  }

  const tabbableElements = collectTabbableElements()
  const direction: WebViewFocusDirection = event.shiftKey ? "backward" : "forward"
  if (tabbableElements.length === 0) {
    event.preventDefault()
    hostApi.exit({ direction })
    return
  }

  const boundary = direction === "forward" ? tabbableElements[tabbableElements.length - 1] : tabbableElements[0]
  if (activeElement !== boundary) {
    return
  }

  event.preventDefault()
  hostApi.exit({ direction })
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
      candidates.push({ element: element as HTMLElement, tabIndex, documentOrder })
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
