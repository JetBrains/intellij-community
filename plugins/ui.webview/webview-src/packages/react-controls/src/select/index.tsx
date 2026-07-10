// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { Select as BaseSelect } from "@base-ui/react/select"
import { useState, type ComponentPropsWithoutRef, type ReactNode } from "react"
import { JbControlChrome } from "../chrome"
import { useWebViewFocusLeave } from "../focus"
import { ensureReactControlsPortalRoot } from "../portal"

export const SelectRoot = BaseSelect.Root
export const SelectGroup = BaseSelect.Group
export const SelectGroupLabel = BaseSelect.GroupLabel
export const SelectValue = BaseSelect.Value

export interface JbSelectOption {
  value: string
  label: ReactNode
  textValue?: string
  disabled?: boolean
}

export interface JbSelectProps extends Omit<ComponentPropsWithoutRef<typeof BaseSelect.Root<string>>, "children" | "items" | "multiple" | "onOpenChange" | "onValueChange" | "open"> {
  children?: ReactNode
  className?: string
  compact?: boolean
  contentClassName?: string
  icon?: ReactNode
  invalid?: boolean
  options?: readonly JbSelectOption[]
  placeholder?: ReactNode
  triggerAriaLabel?: string
  open?: boolean
  onOpenChange?: (open: boolean) => void
  onValueChange?: (value: string | null) => void
}

export function JbSelect({
  children,
  className,
  compact,
  contentClassName,
  disabled,
  icon,
  invalid,
  modal = false,
  onOpenChange,
  onValueChange,
  open,
  options = [],
  placeholder,
  triggerAriaLabel,
  ...props
}: JbSelectProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const isOpen = open ?? uncontrolledOpen

  useWebViewFocusLeave(() => updateOpen(false), isOpen)

  function updateOpen(nextOpen: boolean): void {
    setUncontrolledOpen(nextOpen)
    onOpenChange?.(nextOpen)
  }

  return (
    <BaseSelect.Root
      {...props}
      disabled={disabled}
      modal={modal}
      open={isOpen}
      onOpenChange={updateOpen}
      onValueChange={value => onValueChange?.(value)}
    >
      <JbControlChrome className={className} compact={compact} disabled={disabled} invalid={invalid}>
        <BaseSelect.Trigger className="jbReactSelectTrigger" aria-label={triggerAriaLabel} aria-invalid={invalid ? "true" : undefined}>
          {icon ? <span className="jbReactSelectIcon" aria-hidden="true">{icon}</span> : null}
          <BaseSelect.Value className="jbReactSelectValue" placeholder={placeholder} />
          <BaseSelect.Icon className="jbReactSelectIndicator" aria-hidden="true">
            <SelectChevron />
          </BaseSelect.Icon>
        </BaseSelect.Trigger>
      </JbControlChrome>
      <JbSelectContent className={contentClassName}>
        {children ?? options.map(option => (
          <JbSelectItem key={option.value} value={option.value} disabled={option.disabled} label={option.textValue}>
            {option.label}
          </JbSelectItem>
        ))}
      </JbSelectContent>
    </BaseSelect.Root>
  )
}

export interface JbSelectContentProps extends ComponentPropsWithoutRef<typeof BaseSelect.Popup> {
  positionerClassName?: string
}

export function JbSelectContent({ className, children, positionerClassName, ...props }: JbSelectContentProps) {
  return (
    <BaseSelect.Portal container={ensureReactControlsPortalRoot()}>
      <BaseSelect.Positioner className={["jbReactSelectPositioner", positionerClassName].filter(Boolean).join(" ")} sideOffset={4} alignItemWithTrigger={false}>
        <BaseSelect.Popup {...props} className={["jbReactSelectPopup", className].filter(Boolean).join(" ")}>
          <BaseSelect.ScrollUpArrow className="jbReactSelectScrollButton">
            <SelectChevron direction="up" />
          </BaseSelect.ScrollUpArrow>
          <BaseSelect.List className="jbReactSelectList">
            {children}
          </BaseSelect.List>
          <BaseSelect.ScrollDownArrow className="jbReactSelectScrollButton">
            <SelectChevron />
          </BaseSelect.ScrollDownArrow>
        </BaseSelect.Popup>
      </BaseSelect.Positioner>
    </BaseSelect.Portal>
  )
}

export function JbSelectItem({ className, children, ...props }: ComponentPropsWithoutRef<typeof BaseSelect.Item>) {
  return (
    <BaseSelect.Item {...props} className={["jbReactSelectItem", className].filter(Boolean).join(" ")}>
      <span className="jbReactSelectItemIndicator" aria-hidden="true">
        <BaseSelect.ItemIndicator><SelectCheckIcon /></BaseSelect.ItemIndicator>
      </span>
      <BaseSelect.ItemText>{children}</BaseSelect.ItemText>
    </BaseSelect.Item>
  )
}

export function JbSelectSeparator(props: ComponentPropsWithoutRef<typeof BaseSelect.Separator>) {
  return <BaseSelect.Separator {...props} className={["jbReactSelectSeparator", props.className].filter(Boolean).join(" ")} />
}

function SelectChevron({ direction = "down" }: { direction?: "up" | "down" }) {
  return (
    <svg className={`jbReactSelectChevron jbReactSelectChevron--${direction}`} width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M3 4.5L6 7.5L9 4.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function SelectCheckIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M2.5 6L5 8.5L9.5 3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
