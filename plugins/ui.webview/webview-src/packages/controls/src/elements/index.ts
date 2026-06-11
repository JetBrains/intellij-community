// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export * from "../foundation/types"
export * from "./action-button"
export * from "./button"
export * from "./checkbox"
export * from "./combobox"
export * from "./context-help"
export * from "./disclosure"
export * from "./dropdown-link"
export * from "./expandable-text-field"
export * from "./field"
export * from "./field-group"
export * from "./help-text"
export * from "./icon"
export * from "./label"
export * from "./menu-button"
export * from "./number-field"
export * from "./password-field"
export * from "./radio"
export * from "./radio-group"
export * from "./segmented-control"
export * from "./select"
export * from "./separator"
export * from "./slider"
export * from "./spinner"
export * from "./tabs"
export * from "./text"
export * from "./text-area"
export * from "./text-field"

import { ensureControlsTokensInstalled } from "../tokens"
import { defineControl, type CustomElementRegistryLike } from "../foundation/define"
import { JbActionButton } from "./action-button"
import { JbButton } from "./button"
import { JbCheckbox } from "./checkbox"
import { JbCombobox } from "./combobox"
import { JbContextHelp } from "./context-help"
import { JbDisclosure } from "./disclosure"
import { JbDropdownLink } from "./dropdown-link"
import { JbExpandableTextField } from "./expandable-text-field"
import { JbField } from "./field"
import { JbFieldGroup } from "./field-group"
import { JbHelpText } from "./help-text"
import { JbIcon } from "./icon"
import { JbLabel } from "./label"
import { JbMenuButton } from "./menu-button"
import { JbNumberField } from "./number-field"
import { JbPasswordField } from "./password-field"
import { JbRadio } from "./radio"
import { JbRadioGroup } from "./radio-group"
import { JbSegmentedControl } from "./segmented-control"
import { JbSelect } from "./select"
import { JbSeparator } from "./separator"
import { JbSlider } from "./slider"
import { JbSpinner } from "./spinner"
import { JbTabs } from "./tabs"
import { JbText } from "./text"
import { JbTextArea } from "./text-area"
import { JbTextField } from "./text-field"

export const allControlDefinitions = {
  "jb-action-button": JbActionButton,
  "jb-button": JbButton,
  "jb-checkbox": JbCheckbox,
  "jb-combobox": JbCombobox,
  "jb-context-help": JbContextHelp,
  "jb-disclosure": JbDisclosure,
  "jb-dropdown-link": JbDropdownLink,
  "jb-expandable-text-field": JbExpandableTextField,
  "jb-field": JbField,
  "jb-field-group": JbFieldGroup,
  "jb-help-text": JbHelpText,
  "jb-icon": JbIcon,
  "jb-label": JbLabel,
  "jb-menu-button": JbMenuButton,
  "jb-number-field": JbNumberField,
  "jb-password-field": JbPasswordField,
  "jb-radio": JbRadio,
  "jb-radio-group": JbRadioGroup,
  "jb-segmented-control": JbSegmentedControl,
  "jb-select": JbSelect,
  "jb-separator": JbSeparator,
  "jb-slider": JbSlider,
  "jb-spinner": JbSpinner,
  "jb-tabs": JbTabs,
  "jb-text": JbText,
  "jb-text-area": JbTextArea,
  "jb-text-field": JbTextField,
} as const

export function defineAllControls(registry: CustomElementRegistryLike = customElements): void {
  ensureControlsTokensInstalled()
  for (const [tagName, constructor] of Object.entries(allControlDefinitions)) {
    defineControl(tagName, constructor, registry)
  }
}

declare global {
  interface HTMLElementTagNameMap {
    "jb-action-button": JbActionButton
    "jb-button": JbButton
    "jb-checkbox": JbCheckbox
    "jb-combobox": JbCombobox
    "jb-context-help": JbContextHelp
    "jb-disclosure": JbDisclosure
    "jb-dropdown-link": JbDropdownLink
    "jb-expandable-text-field": JbExpandableTextField
    "jb-field": JbField
    "jb-field-group": JbFieldGroup
    "jb-help-text": JbHelpText
    "jb-icon": JbIcon
    "jb-label": JbLabel
    "jb-menu-button": JbMenuButton
    "jb-number-field": JbNumberField
    "jb-password-field": JbPasswordField
    "jb-radio": JbRadio
    "jb-radio-group": JbRadioGroup
    "jb-segmented-control": JbSegmentedControl
    "jb-select": JbSelect
    "jb-separator": JbSeparator
    "jb-slider": JbSlider
    "jb-spinner": JbSpinner
    "jb-tabs": JbTabs
    "jb-text": JbText
    "jb-text-area": JbTextArea
    "jb-text-field": JbTextField
  }
}
