// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { createContext, useContext, useMemo, useState, type ComponentPropsWithoutRef, type ReactNode } from "react"
import { Popover } from "radix-ui"

interface ModelSelectorContextValue {
  value: string
  disabled: boolean
  query: string
  setQuery: (query: string) => void
  selectValue: (value: string) => void
}

const ModelSelectorContext = createContext<ModelSelectorContextValue | null>(null)

function useModelSelectorContext(): ModelSelectorContextValue {
  const context = useContext(ModelSelectorContext)
  if (!context) throw new Error("ModelSelector components must be used inside ModelSelector.Root")
  return context
}

interface ModelSelectorRootProps {
  value: string
  disabled?: boolean
  children: ReactNode
  onValueChange: (value: string) => void
}

function Root({ value, disabled, children, onValueChange }: ModelSelectorRootProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const context = useMemo<ModelSelectorContextValue>(() => ({
    value,
    disabled: disabled === true,
    query,
    setQuery,
    selectValue(nextValue) {
      onValueChange(nextValue)
      setQuery("")
      setOpen(false)
    },
  }), [disabled, onValueChange, query, value])

  function handleOpenChange(nextOpen: boolean): void {
    setOpen(nextOpen)
    if (!nextOpen) setQuery("")
  }

  return (
    <ModelSelectorContext.Provider value={context}>
      <Popover.Root open={open} onOpenChange={handleOpenChange}>
        {children}
      </Popover.Root>
    </ModelSelectorContext.Provider>
  )
}

interface ModelSelectorTriggerProps extends ComponentPropsWithoutRef<"button"> {
  placeholder?: string
}

function Trigger({ className, children, placeholder = "Select model", ...props }: ModelSelectorTriggerProps) {
  const context = useModelSelectorContext()
  return (
    <Popover.Trigger asChild>
      <button type="button" className={className ?? "acpModelSelectorTrigger"} disabled={context.disabled} {...props}>
        <span className="acpModelSelectorTriggerText">{children ?? placeholder}</span>
        <span className="acpModelSelectorChevron" aria-hidden="true"><ModelSelectorChevron /></span>
      </button>
    </Popover.Trigger>
  )
}

function ModelSelectorChevron() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M3 4.5L6 7.5L9 4.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function Content({ className, children, ...props }: ComponentPropsWithoutRef<typeof Popover.Content>) {
  return (
    <Popover.Portal>
      <Popover.Content align="start" sideOffset={6} className={className ?? "acpModelSelectorContent"} {...props}>
        {children}
      </Popover.Content>
    </Popover.Portal>
  )
}

function Search({ className, placeholder = "Search models...", ...props }: ComponentPropsWithoutRef<"input">) {
  const context = useModelSelectorContext()
  return (
    <input
      type="search"
      className={className ?? "acpModelSelectorSearch"}
      placeholder={placeholder}
      value={context.query}
      onChange={event => context.setQuery(event.currentTarget.value)}
      {...props}
    />
  )
}

function List({ className, ...props }: ComponentPropsWithoutRef<"div">) {
  return <div role="listbox" className={className ?? "acpModelSelectorList"} {...props} />
}

interface ModelSelectorGroupProps extends ComponentPropsWithoutRef<"div"> {
  label?: string
}

function Group({ className, label, children, ...props }: ModelSelectorGroupProps) {
  return (
    <div className={className ?? "acpModelSelectorGroup"} {...props}>
      {label ? <div className="acpModelSelectorGroupLabel">{label}</div> : null}
      {children}
    </div>
  )
}

interface ModelSelectorItemProps extends ComponentPropsWithoutRef<"button"> {
  value: string
  label: string
  description?: string
  searchValue?: string
}

function Item({ className, value, label, description, searchValue, disabled, ...props }: ModelSelectorItemProps) {
  const context = useModelSelectorContext()
  const query = context.query.trim().toLocaleLowerCase()
  const haystack = (searchValue ?? `${label} ${description ?? ""} ${value}`).toLocaleLowerCase()
  if (query && !haystack.includes(query)) return null

  const selected = context.value === value
  return (
    <button
      type="button"
      role="option"
      aria-selected={selected}
      className={className ?? "acpModelSelectorItem"}
      disabled={disabled}
      onClick={() => context.selectValue(value)}
      {...props}
    >
      <span className="acpModelSelectorItemText">
        <span className="acpModelSelectorItemName">{label}</span>
        {description ? <span className="acpModelSelectorItemDesc">{description}</span> : null}
      </span>
      {selected ? <span className="acpModelSelectorItemCheck" aria-hidden="true"><ModelSelectorCheck /></span> : null}
    </button>
  )
}

function ModelSelectorCheck() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      <path d="M2.5 6L5 8.5L9.5 3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export const ModelSelector = {
  Root,
  Trigger,
  Content,
  Search,
  List,
  Group,
  Item,
}
