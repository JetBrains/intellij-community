// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { Tooltip as BaseTooltip } from "@base-ui/react/tooltip"
import { useState, type ComponentPropsWithoutRef, type ReactNode } from "react"
import { useWebViewFocusLeave } from "../focus"
import { ensureReactControlsPortalRoot } from "../portal"

export const TooltipProvider = BaseTooltip.Provider
export const TooltipRoot = BaseTooltip.Root

export interface JbTooltipProps {
  children: ReactNode
  className?: string
  contentClassName?: string
  disabled?: boolean
  open?: boolean
  side?: ComponentPropsWithoutRef<typeof BaseTooltip.Positioner>["side"]
  trigger: ReactNode
  onOpenChange?: (open: boolean) => void
}

export function JbTooltip({ children, className, contentClassName, disabled, onOpenChange, open, side = "top", trigger }: JbTooltipProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const isOpen = open ?? uncontrolledOpen

  useWebViewFocusLeave(() => updateOpen(false), isOpen)

  function updateOpen(nextOpen: boolean): void {
    setUncontrolledOpen(nextOpen)
    onOpenChange?.(nextOpen)
  }

  return (
    <BaseTooltip.Root disabled={disabled} open={isOpen} onOpenChange={updateOpen}>
      <BaseTooltip.Trigger className={["jbReactTooltipTrigger", className].filter(Boolean).join(" ")} disabled={disabled}>
        {trigger}
      </BaseTooltip.Trigger>
      <JbTooltipContent className={contentClassName} side={side}>{children}</JbTooltipContent>
    </BaseTooltip.Root>
  )
}

export interface JbTooltipContentProps extends ComponentPropsWithoutRef<typeof BaseTooltip.Popup> {
  positionerClassName?: string
  side?: ComponentPropsWithoutRef<typeof BaseTooltip.Positioner>["side"]
}

export function JbTooltipContent({ className, children, positionerClassName, side = "top", ...props }: JbTooltipContentProps) {
  return (
    <BaseTooltip.Portal container={ensureReactControlsPortalRoot()}>
      <BaseTooltip.Positioner className={["jbReactTooltipPositioner", positionerClassName].filter(Boolean).join(" ")} side={side} sideOffset={4}>
        <BaseTooltip.Popup {...props} className={["jbReactTooltipPopup", className].filter(Boolean).join(" ")}>
          {children}
        </BaseTooltip.Popup>
      </BaseTooltip.Positioner>
    </BaseTooltip.Portal>
  )
}

