// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useMemo, useRef } from "react"
import {
  ComposerPrimitive,
  unstable_useSlashCommandAdapter,
  unstable_useTriggerPopoverScopeContext,
  useComposerRuntime,
  type Unstable_DirectiveFormatter,
  type Unstable_TriggerItem,
} from "@assistant-ui/react"
import type { CommandView } from "../model/types"

const slashCommandFormatter: Unstable_DirectiveFormatter = {
  serialize(item) {
    return commandPrefix(item.id)
  },
  parse(text) {
    return [{ kind: "text", text }]
  },
}

export function SlashCommandMenu(props: { commands: CommandView[] }) {
  const slashCommands = useMemo(() => props.commands.map(command => ({
    id: command.name,
    label: commandPrefix(command.name),
    description: command.description || command.inputHint,
    execute() {},
  })), [props.commands])
  const commandByName = useMemo(() => new Map(props.commands.map(command => [command.name, command])), [props.commands])
  const slash = unstable_useSlashCommandAdapter({ commands: slashCommands, removeOnExecute: false })

  if (props.commands.length === 0) return null

  return (
    <ComposerPrimitive.Unstable_TriggerPopover
      char="/"
      adapter={slash.adapter}
      className="acpSlashCommandMenu"
      aria-label="Slash commands"
    >
      <ComposerPrimitive.Unstable_TriggerPopover.Action {...slash.action} formatter={slashCommandFormatter} />
      <SlashCommandItems commandByName={commandByName} />
    </ComposerPrimitive.Unstable_TriggerPopover>
  )
}

function SlashCommandItems(props: { commandByName: Map<string, CommandView> }) {
  const popover = unstable_useTriggerPopoverScopeContext()
  const listRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const list = listRef.current
    const highlighted = list?.querySelector<HTMLElement>(".acpSlashCommandItem[data-highlighted]")
    if (!list || !highlighted) return
    scrollElementIntoNearestView(list, highlighted)
  }, [popover.highlightedIndex, popover.items])

  return (
    <ComposerPrimitive.Unstable_TriggerPopoverItems ref={listRef} className="acpSlashCommandItems">
      {items => items.length > 0 ? items.map((item, index) => (
        <SlashCommandItem key={item.id} item={item} index={index} command={props.commandByName.get(item.id)} />
      )) : <div className="acpSlashCommandEmpty">No commands</div>}
    </ComposerPrimitive.Unstable_TriggerPopoverItems>
  )
}

function SlashCommandItem(props: {
  item: Unstable_TriggerItem
  index: number
  command: CommandView | undefined
}) {
  const composer = useComposerRuntime()
  const popover = unstable_useTriggerPopoverScopeContext()

  function insertCommand(): void {
    composer.setText(replaceActiveSlashCommand(composer.getState().text, popover.query, props.item.id))
    popover.close()
  }

  return (
    <ComposerPrimitive.Unstable_TriggerPopoverItem
      item={props.item}
      index={props.index}
      className="acpSlashCommandItem"
      onMouseDown={event => {
        event.preventDefault()
        insertCommand()
      }}
    >
      <span className="acpSlashCommandText">
        <span className="acpSlashCommandName">{props.item.label}</span>
        {props.command?.description ? <span className="acpSlashCommandDesc">{props.command.description}</span> : null}
        {props.command?.inputHint ? <span className="acpSlashCommandHint">{props.command.inputHint}</span> : null}
      </span>
    </ComposerPrimitive.Unstable_TriggerPopoverItem>
  )
}

function commandPrefix(name: string): string {
  return name.startsWith("/") ? name : `/${name}`
}

function replaceActiveSlashCommand(text: string, query: string, commandName: string): string {
  const token = `/${query}`
  const index = text.lastIndexOf(token)
  if (index >= 0 && isTokenBoundary(text, index)) {
    const before = text.slice(0, index)
    const after = text.slice(index + token.length)
    return before + commandPrefix(commandName) + (after.startsWith(" ") ? after : ` ${after}`)
  }
  const separator = text.length === 0 || text.endsWith(" ") ? "" : " "
  return `${text}${separator}${commandPrefix(commandName)} `
}

function isTokenBoundary(text: string, index: number): boolean {
  return index === 0 || /\s/u.test(text[index - 1] ?? "")
}

function scrollElementIntoNearestView(container: HTMLElement, element: HTMLElement): void {
  const containerRect = container.getBoundingClientRect()
  const elementRect = element.getBoundingClientRect()
  if (elementRect.top < containerRect.top) {
    container.scrollTop -= containerRect.top - elementRect.top
  }
  else if (elementRect.bottom > containerRect.bottom) {
    container.scrollTop += elementRect.bottom - containerRect.bottom
  }
}
