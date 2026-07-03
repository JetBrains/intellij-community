// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { Popover as BasePopover } from "@base-ui/react/popover"
import { useState, type ComponentPropsWithoutRef, type ReactNode } from "react"
import { JbControlChrome } from "../chrome"
import { useWebViewFocusLeave } from "../focus"
import { ensureReactControlsPortalRoot } from "../portal"

export const PopoverRoot = BasePopover.Root
export const PopoverTitle = BasePopover.Title
export const PopoverDescription = BasePopover.Description
export const PopoverClose = BasePopover.Close

export interface JbPopoverProps {
  children: ReactNode
  className?: string
  compact?: boolean
  contentClassName?: string
  disabled?: boolean
  modal?: boolean | "trap-focus"
  open?: boolean
  trigger: ReactNode
  triggerAriaLabel?: string
  onOpenChange?: (open: boolean) => void
}

export function JbPopover({
  children,
  className,
  compact,
  contentClassName,
  disabled,
  modal = false,
  onOpenChange,
  open,
  trigger,
  triggerAriaLabel,
}: JbPopoverProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const isOpen = open ?? uncontrolledOpen

  useWebViewFocusLeave(() => updateOpen(false), isOpen)

  function updateOpen(nextOpen: boolean): void {
    setUncontrolledOpen(nextOpen)
    onOpenChange?.(nextOpen)
  }

  return (
    <BasePopover.Root modal={modal} open={isOpen} onOpenChange={updateOpen}>
      <JbControlChrome className={className} compact={compact} disabled={disabled}>
        <BasePopover.Trigger className="jbReactPopoverTrigger" aria-label={triggerAriaLabel} disabled={disabled}>
          {trigger}
        </BasePopover.Trigger>
      </JbControlChrome>
      <JbPopoverContent className={contentClassName}>{children}</JbPopoverContent>
    </BasePopover.Root>
  )
}

export interface JbPopoverContentProps extends ComponentPropsWithoutRef<typeof BasePopover.Popup> {
  positionerClassName?: string
}

export function JbPopoverContent({ className, children, positionerClassName, ...props }: JbPopoverContentProps) {
  return (
    <BasePopover.Portal container={ensureReactControlsPortalRoot()}>
      <BasePopover.Positioner className={["jbReactPopoverPositioner", positionerClassName].filter(Boolean).join(" ")} sideOffset={4}>
        <BasePopover.Popup {...props} className={["jbReactPopoverPopup", className].filter(Boolean).join(" ")}>
          {children}
        </BasePopover.Popup>
      </BasePopover.Positioner>
    </BasePopover.Portal>
  )
}

