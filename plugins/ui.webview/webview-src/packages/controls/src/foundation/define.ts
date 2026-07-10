// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export type CustomElementRegistryLike = Pick<CustomElementRegistry, "define" | "get">

export function defineControl(tagName: string, constructor: CustomElementConstructor, registry: CustomElementRegistryLike = customElements): void {
  if (!registry.get(tagName)) {
    registry.define(tagName, constructor)
  }
}
