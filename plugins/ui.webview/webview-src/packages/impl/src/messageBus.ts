// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {
  Callable,
  WebViewBridge,
  WebViewHostCallOptions,
  WebViewMessageRegistration,
  WebViewNotificationBinding,
  WebViewNotificationBindings,
  WebViewNotificationDescriptor,
  ValidWebViewApi,
  WebViewApiId,
  WebViewCallable,
  WebViewImplementable,
  WebViewImplementation,
  WebViewRuntimeOptions,
} from "@jetbrains/intellij-webview"
import {
  callKey,
  CANCEL_CALL_METHOD,
  CANCELLED,
  INVALID_FRAME,
  JsonRpcErrorFrame,
  JsonRpcErrorObject,
  JsonRpcId,
  JsonRpcNotificationFrame,
  JsonRpcRequestFrame,
  JsonRpcSuccessFrame,
  JSON_RPC_VERSION,
  makeError,
  METHOD_NOT_FOUND,
  PendingCall,
  RUNTIME_INFO_METHOD,
  RUNTIME_INFO_REQUEST_METHOD,
} from "./jsonRpc"
import { applyRuntimeInfo } from "./runtimeOverlay"
import { detectTransport, sendToHost } from "./nativeTransport"

const MAX_PENDING_INBOUND = 128

type WebViewHostApi = Record<string, (params?: unknown, options?: WebViewHostCallOptions) => Promise<unknown>>

interface WebViewBridgeRuntime extends WebViewBridge {
  hostApi<T extends object = WebViewHostApi>(namespace: string): T
  __deliver(raw: string): void
}

interface TypedApiMethodRegistration {
  readonly method: string
  readonly source: string
}

interface TypedImplementationMethod {
  readonly registration: TypedApiMethodRegistration
  readonly member: (params: unknown) => void
}

export function createWebViewBridge(): WebViewBridgeRuntime {
  let nextCallId = 1
  const pendingCalls: Record<string, PendingCall | undefined> = Object.create(null)
  const notificationHandlers: Record<string, Array<(params: unknown) => void> | undefined> = Object.create(null)
  const typedApiMethods: Record<string, TypedApiMethodRegistration | undefined> = Object.create(null)
  const pendingInboundNotifications: JsonRpcNotificationFrame[] = []

  function sendErrorResponse(id: JsonRpcId, code: number, message: string, data?: unknown): void {
    sendToHost({
      jsonrpc: JSON_RPC_VERSION,
      id,
      error: makeError(code, message, data),
    })
  }

  function requestRuntimeInfo(): void {
    sendToHost({
      jsonrpc: JSON_RPC_VERSION,
      method: RUNTIME_INFO_REQUEST_METHOD,
    })
  }

  function enqueueInboundNotification(frame: JsonRpcNotificationFrame): void {
    if (pendingInboundNotifications.length >= MAX_PENDING_INBOUND) {
      pendingInboundNotifications.shift()
      console.warn("[__WVI__] dropping queued inbound notification; queueLimit=" + MAX_PENDING_INBOUND)
    }
    pendingInboundNotifications.push(frame)
  }

  function dispatchNotification(method: string, params: unknown): boolean {
    const list = notificationHandlers[method]
    if (!list || list.length === 0) {
      return false
    }

    const snapshot = list.slice()
    for (const handler of snapshot) {
      try {
        handler(params)
      }
      catch (err) {
        console.error("[__WVI__] notification handler for \"" + method + "\" threw:", err)
      }
    }
    return true
  }

  function replayPendingNotifications(method: string): void {
    for (let i = 0; i < pendingInboundNotifications.length;) {
      const frame = pendingInboundNotifications[i]
      if (frame.method !== method) {
        i++
        continue
      }
      pendingInboundNotifications.splice(i, 1)
      dispatchNotification(frame.method, frame.params)
    }
  }

  function registerNotificationHandler(method: string, handler: (params: unknown) => void): () => void {
    if (typeof method !== "string" || typeof handler !== "function") {
      throw new Error("WebView notification registration requires a string method and function handler")
    }
    let list = notificationHandlers[method]
    if (!list) {
      list = []
      notificationHandlers[method] = list
    }
    list.push(handler)
    replayPendingNotifications(method)
    return function unsubscribe(): void {
      const current = notificationHandlers[method]
      if (!current) {
        return
      }
      const idx = current.indexOf(handler)
      if (idx >= 0) {
        current.splice(idx, 1)
      }
      if (current.length === 0) {
        delete notificationHandlers[method]
      }
    }
  }

  function sendCancel(id: JsonRpcId, message?: string): void {
    sendToHost({
      jsonrpc: JSON_RPC_VERSION,
      method: CANCEL_CALL_METHOD,
      params: { id, message: message || null },
    })
  }

  function rejectPendingCall(id: JsonRpcId, error: JsonRpcErrorObject): boolean {
    const key = callKey(id)
    const pending = pendingCalls[key]
    if (!pending) {
      return false
    }
    delete pendingCalls[key]
    if (pending.signal && pending.abortListener) {
      pending.signal.removeEventListener("abort", pending.abortListener)
    }
    pending.reject(error)
    return true
  }

  function handleCall(frame: JsonRpcRequestFrame): void {
    sendErrorResponse(frame.id, METHOD_NOT_FOUND, "JavaScript API calls are not supported: " + frame.method)
  }

  function handleResponse(frame: JsonRpcSuccessFrame | JsonRpcErrorFrame): void {
    const key = callKey(frame.id)
    const pending = pendingCalls[key]
    if (!pending) {
      console.warn("[__WVI__] dropping response for unknown call", frame.id)
      return
    }
    delete pendingCalls[key]
    if (pending.signal && pending.abortListener) {
      pending.signal.removeEventListener("abort", pending.abortListener)
    }
    if ("error" in frame) {
      const error = new Error(frame.error.message || "WebView RPC error") as Error & { code?: number; data?: unknown }
      error.code = frame.error.code
      error.data = frame.error.data
      pending.reject(error)
    }
    else {
      pending.resolve(frame.result)
    }
  }

  function handleNotification(frame: JsonRpcNotificationFrame): void {
    if (frame.method === CANCEL_CALL_METHOD) {
      const params = frame.params && typeof frame.params === "object" ? frame.params : undefined
      const id = params && "id" in params ? params.id as JsonRpcId : undefined
      if (typeof id !== "undefined") {
        const message = params && "message" in params && typeof params.message === "string" ? params.message : "Call cancelled"
        rejectPendingCall(id, makeError(CANCELLED, message))
      }
      return
    }
    if (frame.method === RUNTIME_INFO_METHOD) {
      applyRuntimeInfo(frame.params)
      return
    }
    if (!dispatchNotification(frame.method, frame.params)) {
      enqueueInboundNotification(frame)
    }
  }

  function handleInboundFrame(frame: unknown): void {
    if (!frame || typeof frame !== "object" || !("jsonrpc" in frame) || frame.jsonrpc !== JSON_RPC_VERSION) {
      console.warn("[__WVI__] dropping invalid JSON-RPC frame", frame)
      return
    }

    const candidate = frame as Record<string, unknown>
    const hasId = Object.prototype.hasOwnProperty.call(candidate, "id")
    const hasMethod = typeof candidate.method === "string"
    const hasResult = Object.prototype.hasOwnProperty.call(candidate, "result")
    const hasError = Object.prototype.hasOwnProperty.call(candidate, "error")

    if (hasId && hasMethod && !hasResult && !hasError) {
      handleCall(candidate as unknown as JsonRpcRequestFrame)
      return
    }
    if (hasId && !hasMethod && (hasResult !== hasError)) {
      handleResponse(candidate as unknown as JsonRpcSuccessFrame | JsonRpcErrorFrame)
      return
    }
    if (!hasId && hasMethod && !hasResult && !hasError) {
      handleNotification(candidate as unknown as JsonRpcNotificationFrame)
      return
    }

    if (hasId) {
      sendErrorResponse(candidate.id as JsonRpcId ?? null, INVALID_FRAME, "Invalid JSON-RPC frame shape")
    }
    else {
      console.warn("[__WVI__] dropping invalid JSON-RPC notification", frame)
    }
  }

  function callHostMethod(method: string, params?: unknown, options?: WebViewHostCallOptions): Promise<unknown> {
    if (typeof method !== "string") {
      throw new Error("WebView host API method must be a string")
    }

    const id = nextCallId++
    const signal = options?.signal
    return new Promise((resolve, reject) => {
      if (signal?.aborted) {
        reject(makeError(CANCELLED, "Call cancelled"))
        return
      }

      const key = callKey(id)
      let abortListener: (() => void) | undefined
      if (signal) {
        abortListener = function onAbort(): void {
          delete pendingCalls[key]
          sendCancel(id, "Call cancelled")
          reject(makeError(CANCELLED, "Call cancelled"))
        }
        signal.addEventListener("abort", abortListener, { once: true })
      }

      pendingCalls[key] = {
        resolve,
        reject,
        signal,
        abortListener,
      }

      const frame: JsonRpcRequestFrame = { jsonrpc: JSON_RPC_VERSION, id, method }
      if (typeof params !== "undefined") {
        frame.params = params
      }
      if (!sendToHost(frame)) {
        delete pendingCalls[key]
        if (signal && abortListener) {
          signal.removeEventListener("abort", abortListener)
        }
        reject(new Error("WebView native transport is unavailable"))
      }
    })
  }

  function sendNotification(method: string, params?: unknown): Promise<void> {
    const frame: JsonRpcNotificationFrame = { jsonrpc: JSON_RPC_VERSION, method }
    if (typeof params !== "undefined") {
      frame.params = params
    }
    if (!sendToHost(frame)) {
      return Promise.reject(new Error("WebView native transport is unavailable"))
    }
    return Promise.resolve()
  }

  function notificationMethod(descriptor: WebViewNotificationDescriptor<unknown>): string {
    const method = descriptor?.method
    if (typeof method !== "string" || method.length === 0) {
      throw new Error("WebView notification descriptor requires a non-empty method")
    }
    return method
  }

  function validateNamespace(namespace: unknown, owner: string): string {
    if (typeof namespace !== "string" || namespace.length === 0) {
      throw new Error(owner + " requires a non-empty namespace")
    }
    if (namespace.charAt(0) === "." || namespace.charAt(namespace.length - 1) === "." || namespace.charAt(0) === "/" || namespace.charAt(namespace.length - 1) === "/") {
      throw new Error(owner + " namespace must not start or end with '.' or '/': " + namespace)
    }
    if (!/^[A-Za-z0-9_.-]+$/.test(namespace)) {
      throw new Error(owner + " namespace contains unsupported characters: " + namespace)
    }
    return namespace
  }

  function apiIdNamespace(id: WebViewApiId): string {
    if (!id || typeof id !== "object") {
      throw new Error("__WVI__ typed API id is required")
    }
    return validateNamespace((id as { namespace?: unknown }).namespace, "__WVI__ typed API id")
  }

  function wireMethod(namespace: string, methodName: string): string {
    if (methodName.length === 0) {
      throw new Error("WebView typed API method name must not be empty: " + namespace)
    }
    return namespace + "/" + methodName
  }

  function typedApiMethodSource(method: string): string {
    const stack = new Error().stack
    if (typeof stack !== "string") {
      return method
    }
    const caller = stack.split("\n").slice(1).map((line) => line.trim()).find((line) => {
      return line.length > 0
        && !line.includes("typedApiMethodSource")
        && !line.includes("typedImplementationMethod")
        && !line.includes("typedImplementationMethods")
        && !line.includes("reserveTypedApiMethods")
        && !line.includes("implementApi")
        && !line.includes("Object.implement")
    })
    return caller ? method + " at " + caller : method
  }

  function typedImplementationMethod(namespace: string, methodName: string, member: unknown): TypedImplementationMethod | undefined {
    if (typeof member !== "function") {
      return undefined
    }
    const method = wireMethod(namespace, methodName)
    return {
      registration: {
        method,
        source: typedApiMethodSource(method),
      },
      member: member as (params: unknown) => void,
    }
  }

  function implementationMethodNames(implementation: object): string[] {
    const names: string[] = []
    const seen: Record<string, true | undefined> = Object.create(null)
    let current: object | null = implementation
    while (current && current !== Object.prototype) {
      for (const name of Object.getOwnPropertyNames(current)) {
        if (name === "constructor" || seen[name]) {
          continue
        }
        seen[name] = true
        names.push(name)
      }
      current = Object.getPrototypeOf(current)
    }
    return names
  }

  function typedImplementationMethods(namespace: string, implementation: object): TypedImplementationMethod[] {
    const methods: TypedImplementationMethod[] = []
    for (const methodName of implementationMethodNames(implementation)) {
      const method = typedImplementationMethod(namespace, methodName, (implementation as Record<string, unknown>)[methodName])
      if (method) {
        methods.push(method)
      }
    }
    return methods
  }

  function reserveTypedApiMethods(methods: TypedApiMethodRegistration[]): WebViewMessageRegistration {
    const reservedMethods: TypedApiMethodRegistration[] = []
    try {
      for (const method of methods) {
        const previous = typedApiMethods[method.method]
        if (previous) {
          throw new Error("WebView typed API method is already registered: " + method.method + ". Existing: " + previous.source + ". New: " + method.source)
        }
        typedApiMethods[method.method] = method
        reservedMethods.push(method)
      }
    }
    catch (err) {
      for (const method of reservedMethods) {
        if (typedApiMethods[method.method] === method) {
          delete typedApiMethods[method.method]
        }
      }
      throw err
    }

    let closed = false
    return {
      close() {
        if (closed) {
          return
        }
        closed = true
        for (const method of methods) {
          if (typedApiMethods[method.method] === method) {
            delete typedApiMethods[method.method]
          }
        }
      },
    }
  }

  function implementApi(namespace: string, implementation: object): WebViewMessageRegistration {
    const methods = typedImplementationMethods(namespace, implementation)
    const registrations: WebViewMessageRegistration[] = []
    try {
      registrations.push(reserveTypedApiMethods(methods.map((method) => method.registration)))
      for (const method of methods) {
        registrations.push(createNotificationBinding({ method: method.registration.method }).on(function dispatchTypedNotification(params: unknown): void {
          method.member.call(implementation, params)
        }))
      }
    }
    catch (err) {
      for (let i = registrations.length - 1; i >= 0; i--) {
        registrations[i].close()
      }
      throw err
    }

    let closed = false
    return {
      close() {
        if (closed) {
          return
        }
        closed = true
        for (let i = registrations.length - 1; i >= 0; i--) {
          registrations[i].close()
        }
      },
    }
  }

  function createNotificationBinding<Params>(descriptor: WebViewNotificationDescriptor<Params>): WebViewNotificationBinding<Params> {
    const method = notificationMethod(descriptor as WebViewNotificationDescriptor<unknown>)
    return {
      send(params?: Params) {
        return sendNotification(method, params)
      },
      on(handler: (params: Params) => void): WebViewMessageRegistration {
        if (typeof handler !== "function") {
          throw new Error("WebView notification handler must be a function: " + method)
        }
        return {
          close: registerNotificationHandler(method, handler as (params: unknown) => void),
        }
      },
    } as WebViewNotificationBinding<Params>
  }

  const api: WebViewBridgeRuntime = {
    __installed: true,

    transport() {
      return detectTransport()
    },

    hostApi<T extends object>(namespace: string): T {
      if (typeof Proxy !== "function") {
        throw new Error("__WVI__.hostApi requires JavaScript Proxy support")
      }
      const validatedNamespace = validateNamespace(namespace, "__WVI__.hostApi(namespace)")
      return new Proxy({}, {
        get(_target, property) {
          if (typeof property !== "string") {
            return undefined
          }
          return function proxyCall(params?: unknown, options?: WebViewHostCallOptions): Promise<unknown> {
            return callHostMethod(wireMethod(validatedNamespace, property), params, options)
          }
        },
      }) as T
    },

    callable<Api extends WebViewCallable>(id: WebViewApiId<Api> & ValidWebViewApi<Api>, options?: WebViewRuntimeOptions): Callable<Api> {
      if (options?.bridge && options.bridge !== api) {
        return options.bridge.callable(id)
      }
      return api.hostApi(apiIdNamespace(id)) as Callable<Api>
    },

    implement<Api extends WebViewImplementable>(id: WebViewApiId<Api> & ValidWebViewApi<Api>, implementation: WebViewImplementation<Api>, options?: WebViewRuntimeOptions): WebViewMessageRegistration {
      if (options?.bridge && options.bridge !== api) {
        return options.bridge.implement(id, implementation)
      }
      if (!implementation || typeof implementation !== "object") {
        throw new Error("__WVI__.implement(id, implementation) requires an implementation object")
      }
      return implementApi(apiIdNamespace(id), implementation)
    },

    notification<Params>(descriptor: WebViewNotificationDescriptor<Params>): WebViewNotificationBinding<Params> {
      return createNotificationBinding(descriptor)
    },

    notifications<Descriptors extends Record<string, WebViewNotificationDescriptor<unknown>>>(descriptors: Descriptors): WebViewNotificationBindings<Descriptors> {
      if (!descriptors || typeof descriptors !== "object") {
        throw new Error("__WVI__.notifications(descriptors) requires an object map")
      }
      const result = {} as WebViewNotificationBindings<Descriptors>
      Object.keys(descriptors).forEach((key) => {
        result[key as keyof Descriptors] = createNotificationBinding(descriptors[key]) as WebViewNotificationBindings<Descriptors>[keyof Descriptors]
      })
      return result
    },

    __deliver(raw: string) {
      if (typeof raw !== "string") {
        return
      }
      let frame: unknown
      try {
        frame = JSON.parse(raw)
      }
      catch (err) {
        console.error("[__WVI__] failed to parse inbound frame:", err)
        return
      }
      handleInboundFrame(frame)
    },
  }

  requestRuntimeInfo()
  return api
}
