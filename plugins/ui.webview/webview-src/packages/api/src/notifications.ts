// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export interface WebViewMessageRegistration {
  close(): void
}

export interface WebViewNotificationDescriptor<Params = void> {
  method: string
  readonly __params?: Params
}

export interface WebViewNotificationBinding<Params = void> {
  send(...params: [Params] extends [void] ? [] | [params: Params] : [params: Params]): Promise<void>
  on(handler: (params: Params) => void): WebViewMessageRegistration
}

export type WebViewNotificationParams<Descriptor> = Descriptor extends WebViewNotificationDescriptor<infer Params> ? Params : never

export type WebViewNotificationBindings<Descriptors extends Record<string, WebViewNotificationDescriptor<unknown>>> = {
  [Key in keyof Descriptors]: WebViewNotificationBinding<WebViewNotificationParams<Descriptors[Key]>>
}

export function defineWebViewNotification<Params = void>(method: string): WebViewNotificationDescriptor<Params> {
  return { method } as WebViewNotificationDescriptor<Params>
}
