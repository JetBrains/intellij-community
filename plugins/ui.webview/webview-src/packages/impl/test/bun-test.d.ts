// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

declare module "bun:test" {
  export interface Matchers<T = unknown> {
    readonly not: Matchers<T>
    toBe(expected: unknown): void
    toBeNull(): void
    toBeUndefined(): void
    toContainEqual(expected: unknown): void
    toEqual(expected: unknown): void
  }

  export function afterEach(callback: () => void | Promise<void>): void
  export function beforeEach(callback: () => void | Promise<void>): void
  export function describe(name: string, callback: () => void): void
  export function expect<T = unknown>(actual: T): Matchers<T>
  export function test(name: string, callback: () => void | Promise<void>): void
}
