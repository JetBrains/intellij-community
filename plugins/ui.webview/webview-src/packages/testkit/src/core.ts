// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {
  Callable,
  ValidWebViewApi,
  WebViewApi,
  WebViewApiId,
  WebViewBridge,
  WebViewCallable,
  WebViewImplementation,
  WebViewImplementable,
  WebViewMessageRegistration,
  WebViewNotificationBinding,
  WebViewNotificationBindings,
  WebViewNotificationDescriptor,
  WebViewTheme,
  WebViewThemeChangedParams,
} from "../../api/src/index"

export type MockWebViewSide = "host" | "page"
type WebViewStringKey<Api> = Extract<keyof Api, string>
type AnyWebViewMethod = (...args: any[]) => void | Promise<unknown>

export type MockCallable<Api extends WebViewApi> = {
  [Key in WebViewStringKey<Api> as Api[Key] extends AnyWebViewMethod ? Key : never]: Api[Key]
}

export type MockWebViewImplementation<Api extends WebViewApi> = MockCallable<Api>

export interface MockWebViewCall {
  readonly side: MockWebViewSide
  readonly method: string
  readonly params: unknown
}

export interface MockWebViewCalls {
  all(): readonly MockWebViewCall[]
  byMethod(method: string): readonly MockWebViewCall[]
  clear(): void
}

export interface MockWebViewTheme {
  set(theme: WebViewTheme, fonts?: WebViewThemeChangedParams["fonts"]): void
}

export interface MockWebViewHost {
  implement<Api extends WebViewCallable>(
    id: WebViewApiId<Api> & ValidWebViewApi<Api>,
    implementation: MockWebViewImplementation<Api>,
  ): WebViewMessageRegistration
}

export interface MockWebViewPage {
  callable<Api extends WebViewImplementable>(id: WebViewApiId<Api> & ValidWebViewApi<Api>): MockCallable<Api>
  whenImplemented<Api extends WebViewImplementable>(
    id: WebViewApiId<Api> & ValidWebViewApi<Api>,
    callback: (api: MockCallable<Api>) => void,
  ): WebViewMessageRegistration
}

export interface MockWebViewContext {
  readonly bridge: WebViewBridge
  readonly host: MockWebViewHost
  readonly page: MockWebViewPage
  readonly calls: MockWebViewCalls
  readonly theme: MockWebViewTheme
  apply(mock: WebViewMock): unknown
}

export type WebViewMockSetup = (context: MockWebViewContext) => unknown

export interface WebViewMock {
  setup(context: MockWebViewContext): unknown
}

export interface InstallMockWebViewBridgeOptions {
  exposeGlobal?: boolean
  theme?: WebViewTheme
  fonts?: WebViewThemeChangedParams["fonts"]
}

export interface StartWebViewMockPreviewOptions {
  webviewSrcDir: string
  viewId: string
  mock: string
  port?: number
}

export interface WebViewMockPreviewServer {
  readonly url: string
  close(): Promise<void>
}

const defaultMockFonts: NonNullable<WebViewThemeChangedParams["fonts"]> = {
  ui: {
    families: ["Inter", "Segoe UI"],
    size: 13,
    lineHeight: 16,
    sizes: {
      h0: 25,
      h1: 22,
      h2: 18,
      h3: 16,
      h4: 14,
      regular: 13,
      medium: 12,
      small: 11,
      mini: 9,
    },
  },
  editor: {
    families: ["JetBrains Mono"],
    size: 13,
    lineHeight: 1.2,
    ligatures: true,
    fontFeatureSettings: [],
  },
}

type MethodImplementation = (params?: unknown) => unknown
type MethodMap = Map<string, MethodImplementation>
type NotificationHandler = (params: unknown) => void

interface MockWebViewWindow extends Window {
  __WVI__?: WebViewBridge
  __WVI_MOCK__?: MockWebViewContext
}

export function defineWebViewMock(setup: WebViewMockSetup): WebViewMock {
  return { setup }
}

export function installMockWebViewBridge(options: InstallMockWebViewBridgeOptions = {}): MockWebViewContext {
  const targetWindow = window as MockWebViewWindow
  const existing = targetWindow.__WVI_MOCK__
  if (existing) {
    return existing
  }

  const context = createMockWebViewContext(options)
  targetWindow.__WVI__ = context.bridge
  if (options.exposeGlobal !== false) {
    targetWindow.__WVI_MOCK__ = context
  }
  return context
}

export async function startWebViewMockPreview(options: StartWebViewMockPreviewOptions): Promise<WebViewMockPreviewServer> {
  const preview = await import("./preview")
  return preview.startWebViewMockPreview(options)
}

function createMockWebViewContext(options: InstallMockWebViewBridgeOptions): MockWebViewContext {
  const hostMethods: MethodMap = new Map()
  const pageMethods: MethodMap = new Map()
  const pageImplementationWaiters: Map<string, Set<() => void>> = new Map()
  const notificationHandlers: Map<string, Set<NotificationHandler>> = new Map()
  const callLog: MockWebViewCall[] = []
  let currentTheme: WebViewThemeChangedParams = {
    theme: options.theme ?? "dark",
    fonts: options.fonts ?? defaultMockFonts,
  }

  const calls: MockWebViewCalls = {
    all() {
      return callLog.slice()
    },
    byMethod(method) {
      return callLog.filter(call => call.method === method)
    },
    clear() {
      callLog.length = 0
    },
  }

  function recordCall(side: MockWebViewSide, method: string, params: unknown): void {
    callLog.push({ side, method, params })
  }

  function registerMethods<Api extends WebViewApi>(
    target: MethodMap,
    id: WebViewApiId<Api>,
    implementation: MockWebViewImplementation<Api>,
  ): WebViewMessageRegistration {
    const namespace = validateNamespace(id)
    const registeredMethods: string[] = []
    for (const methodName of implementationMethodNames(implementation)) {
      const member = (implementation as Record<string, unknown>)[methodName]
      if (typeof member !== "function") {
        continue
      }
      const method = wireMethod(namespace, methodName)
      if (target.has(method)) {
        throw new Error("Mock WebView API method is already registered: " + method)
      }
      target.set(method, (params?: unknown) => member.call(implementation, params))
      registeredMethods.push(method)
    }
    notifyPageImplementationWaiters(namespace)
    return {
      close() {
        for (const method of registeredMethods) {
          target.delete(method)
        }
      },
    }
  }

  function createCallable<Api extends WebViewApi>(target: MethodMap, side: MockWebViewSide, id: WebViewApiId<Api>): MockCallable<Api> {
    const namespace = validateNamespace(id)
    return new Proxy({}, {
      get(_target, property) {
        if (typeof property !== "string") {
          return undefined
        }
        return async function mockWebViewCall(params?: unknown): Promise<unknown> {
          const method = wireMethod(namespace, property)
          recordCall(side, method, params)
          const implementation = target.get(method)
          if (!implementation) {
            throw mockRpcError(-32601, "Method not found: " + method)
          }
          return implementation(params)
        }
      },
    }) as MockCallable<Api>
  }

  function createNotificationBinding<Params>(descriptor: WebViewNotificationDescriptor<Params>): WebViewNotificationBinding<Params> {
    const method = notificationMethod(descriptor)
    return {
      send(params?: Params) {
        const handlers = notificationHandlers.get(method)
        if (handlers) {
          for (const handler of Array.from(handlers)) {
            handler(params)
          }
        }
        return Promise.resolve()
      },
      on(handler: (params: Params) => void) {
        let handlers = notificationHandlers.get(method)
        if (!handlers) {
          handlers = new Set()
          notificationHandlers.set(method, handlers)
        }
        handlers.add(handler as NotificationHandler)
        return {
          close() {
            handlers?.delete(handler as NotificationHandler)
          },
        }
      },
    }
  }

  function notifyPageImplementationWaiters(namespace: string): void {
    const waiters = pageImplementationWaiters.get(namespace)
    if (!waiters) {
      return
    }
    for (const waiter of Array.from(waiters)) {
      waiter()
    }
  }

  function sendThemeChanged(): void {
    sendPageThemeChanged(context, currentTheme)
  }

  const bridge: WebViewBridge = {
    __installed: true,
    transport() {
      return "browser-test"
    },
    callable<Api extends WebViewCallable>(id: WebViewApiId<Api> & ValidWebViewApi<Api>): Callable<Api> {
      return createCallable(hostMethods, "host", id) as unknown as Callable<Api>
    },
    implement<Api extends WebViewImplementable>(
      id: WebViewApiId<Api> & ValidWebViewApi<Api>,
      implementation: WebViewImplementation<Api>,
    ): WebViewMessageRegistration {
      return registerMethods(pageMethods, id, implementation as unknown as MockWebViewImplementation<Api>)
    },
    notification: createNotificationBinding,
    notifications<Descriptors extends Record<string, WebViewNotificationDescriptor<unknown>>>(descriptors: Descriptors): WebViewNotificationBindings<Descriptors> {
      const result: Partial<WebViewNotificationBindings<Descriptors>> = {}
      for (const key of Object.keys(descriptors)) {
        result[key as keyof Descriptors] = createNotificationBinding(descriptors[key]) as WebViewNotificationBindings<Descriptors>[keyof Descriptors]
      }
      return result as WebViewNotificationBindings<Descriptors>
    },
  }

  const context: MockWebViewContext = {
    bridge,
    host: {
      implement(id, implementation) {
        return registerMethods(hostMethods, id, implementation)
      },
    },
    page: {
      callable(id) {
        return createCallable(pageMethods, "page", id)
      },
      whenImplemented(id, callback) {
        const namespace = validateNamespace(id)
        let closed = false
        const notify = () => {
          if (!closed) {
            callback(createCallable(pageMethods, "page", id))
          }
        }
        let waiters = pageImplementationWaiters.get(namespace)
        if (!waiters) {
          waiters = new Set()
          pageImplementationWaiters.set(namespace, waiters)
        }
        waiters.add(notify)
        if (hasImplementationForNamespace(pageMethods, namespace)) {
          queueMicrotask(notify)
        }
        return {
          close() {
            closed = true
            waiters?.delete(notify)
          },
        }
      },
    },
    calls,
    theme: {
      set(theme, fonts) {
        currentTheme = { theme, fonts: fonts ?? defaultMockFonts }
        sendThemeChanged()
      },
    },
    apply(mock) {
      return mock.setup(context)
    },
  }

  installPlatformDefaults(context, hostMethods, () => currentTheme, sendThemeChanged)
  return context
}

function installPlatformDefaults(
  context: MockWebViewContext,
  hostMethods: MethodMap,
  currentTheme: () => WebViewThemeChangedParams,
  sendThemeChanged: () => void,
): void {
  hostMethods.set("webview.theme/themeRequest", () => {
    sendPageThemeChanged(context, currentTheme())
  })
  hostMethods.set("webview.focus/activated", () => {})
  hostMethods.set("webview.focus/exit", () => {})
  hostMethods.set("$/webview/runtimeInfoRequest", () => {})
  context.page.whenImplemented(apiId<WebViewThemeHostEvents>("webview.theme"), sendThemeChanged)
}

function validateNamespace(id: { namespace?: unknown }): string {
  if (!id || typeof id.namespace !== "string" || id.namespace.length === 0) {
    throw new Error("Mock WebView API id requires a non-empty namespace")
  }
  return id.namespace
}

function wireMethod(namespace: string, methodName: string): string {
  return namespace + "/" + methodName
}

function notificationMethod(descriptor: WebViewNotificationDescriptor<unknown>): string {
  const method = descriptor?.method
  if (typeof method !== "string" || method.length === 0) {
    throw new Error("Mock WebView notification descriptor requires a non-empty method")
  }
  return method
}

function implementationMethodNames(implementation: object): string[] {
  const names: string[] = []
  const seen = new Set<string>()
  let current: object | null = implementation
  while (current && current !== Object.prototype) {
    for (const name of Object.getOwnPropertyNames(current)) {
      if (name === "constructor" || seen.has(name)) {
        continue
      }
      seen.add(name)
      names.push(name)
    }
    current = Object.getPrototypeOf(current)
  }
  return names
}

function hasImplementationForNamespace(methods: MethodMap, namespace: string): boolean {
  const prefix = namespace + "/"
  for (const method of methods.keys()) {
    if (method.startsWith(prefix)) {
      return true
    }
  }
  return false
}

function mockRpcError(code: number, message: string): Error & { code: number } {
  const error = new Error(message) as Error & { code: number }
  error.code = code
  return error
}

function apiId<Api extends WebViewApi = WebViewApi>(namespace: string): WebViewApiId<Api> {
  return { namespace } as WebViewApiId<Api>
}

interface WebViewThemeHostEvents extends WebViewImplementable {
  themeChanged(params: WebViewThemeChangedParams): void
}

interface AsyncWebViewThemeHostEvents {
  themeChanged(params: WebViewThemeChangedParams): Promise<void>
}

function sendPageThemeChanged(context: MockWebViewContext, params: WebViewThemeChangedParams): void {
  const pageApi = context.page.callable<WebViewThemeHostEvents>(apiId<WebViewThemeHostEvents>("webview.theme")) as unknown as AsyncWebViewThemeHostEvents
  void pageApi.themeChanged(params).catch(() => {})
}

declare global {
  interface Window {
    __WVI_MOCK__?: MockWebViewContext
  }
}
