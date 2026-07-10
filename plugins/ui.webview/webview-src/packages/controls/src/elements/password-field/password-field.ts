// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { TextInputBase } from "../text-field/text-input-base"

export class JbPasswordField extends TextInputBase {
  protected get inputType(): string {
    return "password"
  }
}

export function defineJbPasswordField(registry?: CustomElementRegistryLike): void {
  defineControl("jb-password-field", JbPasswordField, registry)
}
