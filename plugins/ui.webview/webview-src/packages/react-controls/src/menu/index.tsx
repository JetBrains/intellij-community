// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { Menu as BaseMenu } from "@base-ui/react/menu"
import { useState, type ComponentPropsWithoutRef, type ReactNode } from "react"
import { JbControlChrome } from "../chrome"
import { useWebViewFocusLeave } from "../focus"
import { ensureReactControlsPortalRoot } from "../portal"

export const MenuRoot = BaseMenu.Root
export const MenuGroup = BaseMenu.Group
export const MenuGroupLabel = BaseMenu.GroupLabel
export const MenuRadioGroup = BaseMenu.RadioGroup

export interface JbMenuButtonProps {
  children: ReactNode
  className?: string
  compact?: boolean
  contentClassName?: string
  disabled?: boolean
  icon?: ReactNode
  label: ReactNode
  modal?: boolean
  open?: boolean
  triggerAriaLabel?: string
  onOpenChange?: (open: boolean) => void
}

export function JbMenuButton({
  children,
  className,
  compact,
  contentClassName,
  disabled,
  icon,
  label,
  modal = false,
  onOpenChange,
  open,
  triggerAriaLabel,
}: JbMenuButtonProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const isOpen = open ?? uncontrolledOpen

  useWebViewFocusLeave(() => updateOpen(false), isOpen)

  function updateOpen(nextOpen: boolean): void {
    setUncontrolledOpen(nextOpen)
    onOpenChange?.(nextOpen)
  }

  return (
    <BaseMenu.Root disabled={disabled} modal={modal} open={isOpen} onOpenChange={updateOpen}>
      <JbControlChrome className={className} compact={compact} disabled={disabled}>
        <BaseMenu.Trigger className="jbReactMenuTrigger" aria-label={triggerAriaLabel} disabled={disabled}>
          {icon ? <span className="jbReactMenuIcon" aria-hidden="true">{icon}</span> : null}
          <span className="jbReactMenuTriggerText">{label}</span>
          <span className="jbReactMenuIndicator" aria-hidden="true"><MenuChevron /></span>
        </BaseMenu.Trigger>
      </JbControlChrome>
      <JbMenuContent className={contentClassName}>{children}</JbMenuContent>
    </BaseMenu.Root>
  )
}

export interface JbMenuContentProps extends ComponentPropsWithoutRef<typeof BaseMenu.Popup> {
  positionerClassName?: string
}

export function JbMenuContent({ className, children, positionerClassName, ...props }: JbMenuContentProps) {
  return (
    <BaseMenu.Portal container={ensureReactControlsPortalRoot()}>
      <BaseMenu.Positioner className={["jbReactMenuPositioner", positionerClassName].filter(Boolean).join(" ")} sideOffset={4}>
        <BaseMenu.Popup {...props} className={["jbReactMenuPopup", className].filter(Boolean).join(" ")}>
          <BaseMenu.Viewport className="jbReactMenuViewport">
            {children}
          </BaseMenu.Viewport>
        </BaseMenu.Popup>
      </BaseMenu.Positioner>
    </BaseMenu.Portal>
  )
}

export interface JbMenuItemProps extends ComponentPropsWithoutRef<typeof BaseMenu.Item> {
  shortcut?: ReactNode
}

export function JbMenuItem({ className, children, shortcut, ...props }: JbMenuItemProps) {
  return (
    <BaseMenu.Item {...props} className={["jbReactMenuItem", className].filter(Boolean).join(" ")}>
      <span className="jbReactMenuItemIndicator" aria-hidden="true" />
      <span className="jbReactMenuItemText">{children}</span>
      {shortcut ? <JbMenuShortcut>{shortcut}</JbMenuShortcut> : null}
    </BaseMenu.Item>
  )
}

export function JbMenuCheckboxItem({ className, children, ...props }: ComponentPropsWithoutRef<typeof BaseMenu.CheckboxItem>) {
  return (
    <BaseMenu.CheckboxItem {...props} className={["jbReactMenuItem", className].filter(Boolean).join(" ")}>
      <span className="jbReactMenuItemIndicator" aria-hidden="true">
        <BaseMenu.CheckboxItemIndicator><MenuCheckIcon /></BaseMenu.CheckboxItemIndicator>
      </span>
      <span className="jbReactMenuItemText">{children}</span>
    </BaseMenu.CheckboxItem>
  )
}

export function JbMenuRadioItem({ className, children, ...props }: ComponentPropsWithoutRef<typeof BaseMenu.RadioItem>) {
  return (
    <BaseMenu.RadioItem {...props} className={["jbReactMenuItem", className].filter(Boolean).join(" ")}>
      <span className="jbReactMenuItemIndicator" aria-hidden="true">
        <BaseMenu.RadioItemIndicator><MenuCheckIcon /></BaseMenu.RadioItemIndicator>
      </span>
      <span className="jbReactMenuItemText">{children}</span>
    </BaseMenu.RadioItem>
  )
}

export function JbMenuSeparator(props: ComponentPropsWithoutRef<typeof BaseMenu.Separator>) {
  return <BaseMenu.Separator {...props} className={["jbReactMenuSeparator", props.className].filter(Boolean).join(" ")} />
}

export function JbMenuShortcut(props: ComponentPropsWithoutRef<"span">) {
  return <span {...props} className={["jbReactMenuShortcut", props.className].filter(Boolean).join(" ")} />
}

function MenuChevron() {
  return (
    <svg className="jbReactMenuChevron" width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M3 4.5L6 7.5L9 4.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function MenuCheckIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M2.5 6L5 8.5L9.5 3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
