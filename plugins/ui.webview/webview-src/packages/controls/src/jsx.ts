// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { JbControlOption } from "./foundation"

type JbBooleanAttribute = boolean | "true" | "false" | ""
type JbNumberAttribute = number | string

export interface JbCustomElementProps {
  children?: unknown
  class?: string
  className?: string
  draggable?: JbBooleanAttribute
  hidden?: JbBooleanAttribute
  id?: string
  part?: string
  role?: string
  slot?: string
  style?: unknown
  tabIndex?: number
  title?: string
  onBlur?: (event: FocusEvent) => void
  onChange?: (event: Event) => void
  onClick?: (event: MouseEvent) => void
  onFocus?: (event: FocusEvent) => void
  onInput?: (event: Event) => void
  onKeyDown?: (event: KeyboardEvent) => void
  onKeyUp?: (event: KeyboardEvent) => void
  [ariaAttribute: `aria-${string}`]: string | number | boolean | undefined
  [dataAttribute: `data-${string}`]: string | number | boolean | undefined
}

interface JbActionButtonProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  expanded?: JbBooleanAttribute
  label?: string
  selected?: JbBooleanAttribute
}

interface JbButtonProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  pressed?: JbBooleanAttribute
  selected?: JbBooleanAttribute
  size?: string
  type?: string
  variant?: string
}

interface JbChoiceProps extends JbCustomElementProps {
  checked?: JbBooleanAttribute
  disabled?: JbBooleanAttribute
  name?: string
  readOnly?: JbBooleanAttribute
  readonly?: JbBooleanAttribute
  value?: string
}

interface JbInputProps extends JbCustomElementProps {
  autocomplete?: string
  disabled?: JbBooleanAttribute
  inputMode?: string
  inputmode?: string
  invalid?: JbBooleanAttribute
  maxLength?: JbNumberAttribute
  maxlength?: JbNumberAttribute
  name?: string
  placeholder?: string
  readOnly?: JbBooleanAttribute
  readonly?: JbBooleanAttribute
  required?: JbBooleanAttribute
  value?: string
}

interface JbItemsProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  items?: JbControlOption[]
  value?: string
}

interface JbContextHelpProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  open?: JbBooleanAttribute
  text?: string
}

interface JbDisclosureProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  label?: string
  open?: JbBooleanAttribute
}

interface JbExpandableTextFieldProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  expanded?: JbBooleanAttribute
  placeholder?: string
  readOnly?: JbBooleanAttribute
  readonly?: JbBooleanAttribute
  value?: string
}

interface JbFieldProps extends JbCustomElementProps {
  error?: string
  help?: string
  label?: string
  required?: JbBooleanAttribute
  warning?: string
}

interface JbFieldGroupProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  label?: string
}

interface JbHelpTextProps extends JbCustomElementProps {
  tone?: string
}

interface JbIconProps extends JbCustomElementProps {
  label?: string
  name?: string
  size?: string
  src?: string
}

interface JbLabelProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  for?: string
  htmlFor?: string
  required?: JbBooleanAttribute
}

interface JbMenuButtonProps extends JbItemsProps {
  label?: string
  open?: JbBooleanAttribute
  variant?: string
}

interface JbNumberInputProps extends JbInputProps {
  max?: JbNumberAttribute
  min?: JbNumberAttribute
  step?: JbNumberAttribute
}

interface JbSelectProps extends JbItemsProps {
  invalid?: JbBooleanAttribute
  name?: string
  placeholder?: string
  required?: JbBooleanAttribute
}

interface JbTextAreaProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  invalid?: JbBooleanAttribute
  name?: string
  placeholder?: string
  readOnly?: JbBooleanAttribute
  readonly?: JbBooleanAttribute
  required?: JbBooleanAttribute
  rows?: JbNumberAttribute
  value?: string
}

interface JbTextProps extends JbCustomElementProps {
  size?: string
  tone?: string
  weight?: string
}

interface JbValueRangeProps extends JbCustomElementProps {
  disabled?: JbBooleanAttribute
  max?: JbNumberAttribute
  min?: JbNumberAttribute
  step?: JbNumberAttribute
  value?: string
}

declare global {
  namespace JSX {
    interface IntrinsicElements {
      "jb-action-button": JbActionButtonProps
      "jb-button": JbButtonProps
      "jb-checkbox": JbChoiceProps & { indeterminate?: JbBooleanAttribute }
      "jb-combobox": JbInputProps & { items?: JbControlOption[] }
      "jb-context-help": JbContextHelpProps
      "jb-disclosure": JbDisclosureProps
      "jb-dropdown-link": JbMenuButtonProps
      "jb-expandable-text-field": JbExpandableTextFieldProps
      "jb-field": JbFieldProps
      "jb-field-group": JbFieldGroupProps
      "jb-help-text": JbHelpTextProps
      "jb-icon": JbIconProps
      "jb-label": JbLabelProps
      "jb-menu-button": JbMenuButtonProps
      "jb-number-field": JbNumberInputProps
      "jb-password-field": JbInputProps
      "jb-radio": JbChoiceProps
      "jb-radio-group": JbItemsProps & { label?: string }
      "jb-segmented-control": JbItemsProps
      "jb-select": JbSelectProps
      "jb-separator": JbCustomElementProps & { orientation?: string }
      "jb-slider": JbValueRangeProps
      "jb-spinner": JbValueRangeProps
      "jb-tabs": JbItemsProps
      "jb-text": JbTextProps
      "jb-text-area": JbTextAreaProps
      "jb-text-field": JbInputProps
    }
  }
}
