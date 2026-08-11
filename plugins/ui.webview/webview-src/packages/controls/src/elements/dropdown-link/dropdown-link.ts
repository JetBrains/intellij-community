// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { JbMenuButton } from "../menu-button"

export class JbDropdownLink extends JbMenuButton {
  variant = "link"
}

export function defineJbDropdownLink(registry?: CustomElementRegistryLike): void {
  defineControl("jb-dropdown-link", JbDropdownLink, registry)
}
