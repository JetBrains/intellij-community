// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { TextInputBase } from "./text-input-base"

export class JbTextField extends TextInputBase {}

export function defineJbTextField(registry?: CustomElementRegistryLike): void {
  defineControl("jb-text-field", JbTextField, registry)
}
