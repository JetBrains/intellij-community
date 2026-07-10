// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export type JsonRpcId = string | number | null

export interface JsonRpcErrorObject {
  code: number
  message: string
  data?: unknown
}

export interface JsonRpcBaseFrame {
  jsonrpc: "2.0"
}

export interface JsonRpcRequestFrame extends JsonRpcBaseFrame {
  id: JsonRpcId
  method: string
  params?: unknown
}

export interface JsonRpcNotificationFrame extends JsonRpcBaseFrame {
  method: string
  params?: unknown
}

export interface JsonRpcSuccessFrame extends JsonRpcBaseFrame {
  id: JsonRpcId
  result?: unknown
}

export interface JsonRpcErrorFrame extends JsonRpcBaseFrame {
  id: JsonRpcId
  error: JsonRpcErrorObject
}

export type JsonRpcFrame = JsonRpcRequestFrame | JsonRpcNotificationFrame | JsonRpcSuccessFrame | JsonRpcErrorFrame

export interface PendingCall {
  resolve(value: unknown): void
  reject(reason: unknown): void
  signal?: AbortSignal
  abortListener?: () => void
}

export const JSON_RPC_VERSION = "2.0"
export const CANCEL_CALL_METHOD = "$/cancelRequest"
export const RUNTIME_INFO_REQUEST_METHOD = "$/webview/runtimeInfoRequest"
export const RUNTIME_INFO_METHOD = "$/webview/runtimeInfo"
export const INVALID_FRAME = -32600
export const METHOD_NOT_FOUND = -32601
export const CANCELLED = -32800

export function callKey(id: JsonRpcId): string {
  return JSON.stringify(id)
}

export function makeError(code: number, message: string, data?: unknown): JsonRpcErrorObject {
  const error: JsonRpcErrorObject = { code, message }
  if (typeof data !== "undefined") {
    error.data = data
  }
  return error
}
