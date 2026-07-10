// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { ComponentPropsWithoutRef, ReactNode } from "react"
import { Select as SelectPrimitive } from "radix-ui"

const SelectRoot = SelectPrimitive.Root
const SelectGroup = SelectPrimitive.Group
const SelectValue = SelectPrimitive.Value

function SelectTrigger({ className, children, ...props }: ComponentPropsWithoutRef<typeof SelectPrimitive.Trigger>) {
  return (
    <SelectPrimitive.Trigger data-slot="select-trigger" className={className} {...props}>
      {children}
      <SelectPrimitive.Icon className="acpSelectIcon">
        <SelectChevron direction="down" />
      </SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>
  )
}

function SelectScrollUpButton(props: ComponentPropsWithoutRef<typeof SelectPrimitive.ScrollUpButton>) {
  return (
    <SelectPrimitive.ScrollUpButton data-slot="select-scroll-up-button" className="acpSelectScrollButton" {...props}>
      <SelectChevron direction="up" />
    </SelectPrimitive.ScrollUpButton>
  )
}

function SelectScrollDownButton(props: ComponentPropsWithoutRef<typeof SelectPrimitive.ScrollDownButton>) {
  return (
    <SelectPrimitive.ScrollDownButton data-slot="select-scroll-down-button" className="acpSelectScrollButton" {...props}>
      <SelectChevron direction="down" />
    </SelectPrimitive.ScrollDownButton>
  )
}

function SelectChevron(props: { direction: "up" | "down" }) {
  return (
    <svg className={`acpSelectChevron acpSelectChevron--${props.direction}`} width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M3 4.5L6 7.5L9 4.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function SelectContent({ children, position = "popper", ...props }: ComponentPropsWithoutRef<typeof SelectPrimitive.Content>) {
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Content data-slot="select-content" className="acpSelectContent" position={position} sideOffset={6} {...props}>
        <SelectScrollUpButton />
        <SelectPrimitive.Viewport className="acpSelectViewport">
          {children}
        </SelectPrimitive.Viewport>
        <SelectScrollDownButton />
      </SelectPrimitive.Content>
    </SelectPrimitive.Portal>
  )
}

function SelectLabel(props: ComponentPropsWithoutRef<typeof SelectPrimitive.Label>) {
  return <SelectPrimitive.Label data-slot="select-label" className="acpSelectLabel" {...props} />
}

function SelectItem({ children, ...props }: ComponentPropsWithoutRef<typeof SelectPrimitive.Item>) {
  return (
    <SelectPrimitive.Item data-slot="select-item" className="acpSelectItem" {...props}>
      <span className="acpSelectItemIndicator">
        <SelectPrimitive.ItemIndicator>✓</SelectPrimitive.ItemIndicator>
      </span>
      <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
    </SelectPrimitive.Item>
  )
}

function SelectSeparator(props: ComponentPropsWithoutRef<typeof SelectPrimitive.Separator>) {
  return <SelectPrimitive.Separator data-slot="select-separator" className="acpSelectSeparator" {...props} />
}

export interface SelectOption {
  value: string
  label: ReactNode
  textValue?: string
  disabled?: boolean
}

export interface SelectProps extends Pick<ComponentPropsWithoutRef<typeof SelectPrimitive.Root>, "value" | "onValueChange" | "disabled"> {
  value: string
  onValueChange: (value: string) => void
  options: readonly SelectOption[]
  children?: ReactNode
  placeholder?: ReactNode
  className?: string
  triggerAriaLabel?: string
}

function Select({ options, children, placeholder, className, triggerAriaLabel, ...props }: SelectProps) {
  const selectedOption = options.find(option => option.value === props.value)

  return (
    <SelectRoot {...props}>
      <SelectTrigger className={className} aria-label={triggerAriaLabel}>
        <span>{selectedOption?.label ?? placeholder}</span>
      </SelectTrigger>
      <SelectContent>
        {children ?? options.map(({ label, disabled, textValue, value }) => (
          <SelectItem
            key={value}
            value={value}
            disabled={disabled}
            textValue={textValue ?? (typeof label === "string" ? label : value)}
          >
            {label}
          </SelectItem>
        ))}
      </SelectContent>
    </SelectRoot>
  )
}

export {
  Select,
  SelectRoot,
  SelectGroup,
  SelectValue,
  SelectTrigger,
  SelectContent,
  SelectLabel,
  SelectItem,
  SelectSeparator,
  SelectScrollUpButton,
  SelectScrollDownButton,
}
