// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

declare const WEBVIEW_API: unique symbol
declare const WEBVIEW_CALLABLE: unique symbol
declare const WEBVIEW_IMPLEMENTABLE: unique symbol
declare const WEBVIEW_API_ID: unique symbol

export interface WebViewApi {
  readonly [WEBVIEW_API]: true
}

export interface WebViewCallable extends WebViewApi {
  readonly [WEBVIEW_CALLABLE]: true
}

export interface WebViewImplementable extends WebViewApi {
  readonly [WEBVIEW_IMPLEMENTABLE]: true
}

export interface WebViewApiId<Api extends WebViewApi = WebViewApi, Namespace extends string = string> {
  readonly namespace: Namespace
  readonly [WEBVIEW_API_ID]: Api
}

export type ApiOf<Id> = Id extends WebViewApiId<infer Api> ? Api : never

type WebViewStringKey<Api> = Extract<keyof Api, string>
type AnyWebViewMethod = (...args: any[]) => void | Promise<unknown>

export type InvalidWebViewMethodKeys<Api> = {
  [Key in WebViewStringKey<Api>]: Api[Key] extends AnyWebViewMethod ? never : Key
}[WebViewStringKey<Api>]

export type ValidWebViewApi<Api extends WebViewApi> = InvalidWebViewMethodKeys<Api> extends never
  ? unknown
  : { ERROR_invalid_webview_methods: InvalidWebViewMethodKeys<Api> }

export type Callable<Api extends WebViewCallable> = {
  [Key in WebViewStringKey<Api> as Api[Key] extends AnyWebViewMethod ? Key : never]: Api[Key]
}

export type WebViewImplementation<Api extends WebViewImplementable> = {
  [Key in WebViewStringKey<Api> as Api[Key] extends AnyWebViewMethod ? Key : never]: Api[Key]
}

export function apiId<Api extends WebViewApi>(): <const Namespace extends string>(namespace: Namespace) => WebViewApiId<Api, Namespace> {
  return function createApiId<const Namespace extends string>(namespace: Namespace): WebViewApiId<Api, Namespace> {
    validateApiNamespace(namespace)
    return { namespace } as WebViewApiId<Api, Namespace>
  }
}

function validateApiNamespace(namespace: string): void {
  if (typeof namespace !== "string" || namespace.length === 0) {
    throw new Error("WebView API namespace must be a non-empty string")
  }
  if (namespace.startsWith(".") || namespace.endsWith(".") || namespace.startsWith("/") || namespace.endsWith("/")) {
    throw new Error("WebView API namespace must not start or end with '.' or '/': " + namespace)
  }
  if (!/^[A-Za-z0-9_.-]+$/.test(namespace)) {
    throw new Error("WebView API namespace contains unsupported characters: " + namespace)
  }
}
