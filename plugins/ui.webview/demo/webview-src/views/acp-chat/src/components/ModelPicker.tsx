// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { ReactNode } from "react"
import { Switch, Tooltip } from "radix-ui"
import type { ConfigOptionView, ConfigSelectOptionView, SessionModeView } from "../model/types"
import { acpControlIconPath, acpIconSrc, type AcpControlIconKind } from "./icons/AcpChatIconSet"
import { ModelSelector } from "./ModelSelector"
import { Select } from "./Select"

export function ModelPicker(props: {
  modes: SessionModeView[]
  configOptions: ConfigOptionView[]
  currentModeId: string | null
  disabled: boolean
  onSelectMode: (modeId: string) => void
  onSelectConfigOption: (option: ConfigOptionView, value: string | boolean) => void
}) {
  const hasSessionModes = props.modes.length > 0
  const selectedMode = props.modes.find(mode => mode.id === props.currentModeId)
  const modelOption = props.configOptions.find(isModelSelectOption)
  const selectedModel = modelOption?.options.find(option => option.value === modelOption.currentValue)
  const otherConfigOptions = props.configOptions.filter(option => option !== modelOption && isRenderableConfigOption(option))

  if (!hasSessionModes && !modelOption && otherConfigOptions.length === 0) return null

  return (
    <Tooltip.Provider delayDuration={250}>
      <div className="acpModelPicker">
        {hasSessionModes ? (
          <ControlHint className="acpModelPickerControl acpControlWithHint" hint="Mode" controlId="legacy-mode">
            <ControlIcon kind="mode" hint="Mode" />
            <ModelSelector.Root
              value={props.currentModeId ?? ""}
              disabled={props.disabled}
              onValueChange={props.onSelectMode}
            >
              <ModelSelector.Trigger aria-label={`Mode: ${selectedMode?.name ?? "Select mode"}`}>
                {selectedMode?.name ?? "Select mode..."}
              </ModelSelector.Trigger>
              <ModelSelector.Content>
                <ModelSelector.Search placeholder="Search modes..." />
                <ModelSelector.List>
                  <ModelSelector.Group label="Modes">
                    {props.modes.map(mode => (
                      <ModelSelector.Item
                        key={mode.id}
                        value={mode.id}
                        label={mode.name}
                        description={mode.description}
                        searchValue={`${mode.name} ${mode.id} ${mode.description ?? ""}`}
                      />
                    ))}
                  </ModelSelector.Group>
                </ModelSelector.List>
              </ModelSelector.Content>
            </ModelSelector.Root>
          </ControlHint>
        ) : null}
        {modelOption ? (
          <ControlHint className="acpModelPickerControl acpControlWithHint" hint="Model" configId={modelOption.id}>
            <ControlIcon kind="model" hint="Model" />
            <ModelSelector.Root
              value={modelOption.currentValue}
              disabled={props.disabled}
              onValueChange={value => props.onSelectConfigOption(modelOption, value)}
            >
              <ModelSelector.Trigger aria-label={`Model: ${selectedModel?.name ?? "Select model"}`}>
                {selectedModel?.name ?? "Select model..."}
              </ModelSelector.Trigger>
              <ModelSelector.Content>
                <ModelSelector.Search placeholder="Search models..." />
                <ModelSelector.List>
                  <ModelSelector.Group label={modelOption.name}>
                    {modelOption.options.map(option => (
                      <ModelSelector.Item
                        key={option.value}
                        value={option.value}
                        label={option.name}
                        description={option.description}
                        searchValue={`${option.name} ${option.value} ${option.description ?? ""}`}
                      />
                    ))}
                  </ModelSelector.Group>
                </ModelSelector.List>
              </ModelSelector.Content>
            </ModelSelector.Root>
          </ControlHint>
        ) : null}
        {otherConfigOptions.map(option => (
          <ConfigOptionControl
            key={option.id}
            option={option}
            disabled={props.disabled}
            onChange={value => props.onSelectConfigOption(option, value)}
          />
        ))}
      </div>
    </Tooltip.Provider>
  )
}

function ConfigOptionControl(props: {
  option: ConfigOptionView
  disabled: boolean
  onChange: (value: string | boolean) => void
}) {
  const { option } = props
  if (option.type === "select") {
    if (option.options.length === 0) return null
    const booleanSelect = toBooleanSelectOption(option)
    if (booleanSelect) {
      return (
        <ConfigToggleControl
          option={option}
          checked={booleanSelect.checked}
          disabled={props.disabled}
          onCheckedChange={checked => props.onChange(checked ? booleanSelect.trueValue : booleanSelect.falseValue)}
        />
      )
    }
    const selectedChoice = option.options.find(choice => choice.value === option.currentValue)
    const displayName = configOptionDisplayName(option)
    return (
      <ControlHint className="acpModelPickerControl acpControlWithHint" hint={displayName} configId={option.id}>
        <ControlIcon kind={configOptionIconKind(option)} hint={displayName} />
        <Select
          className="acpConfigOptionSelect"
          value={option.currentValue}
          disabled={props.disabled}
          placeholder="Select..."
          triggerAriaLabel={`${displayName}: ${selectedChoice?.name ?? "Select"}`}
          options={option.options.map(choice => ({
            value: choice.value,
            label: choice.name,
            textValue: `${choice.name} ${choice.value} ${choice.description ?? ""}`,
          }))}
          onValueChange={props.onChange}
        />
      </ControlHint>
    )
  }

  return (
    <ConfigToggleControl
      option={option}
      checked={option.currentValue}
      disabled={props.disabled}
      onCheckedChange={props.onChange}
    />
  )
}

function ConfigToggleControl(props: {
  option: ConfigOptionView
  checked: boolean
  disabled: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <ControlHint className="acpConfigToggle acpControlWithHint" hint={props.option.name} configId={props.option.id}>
      <ControlIcon kind={configOptionIconKind(props.option)} hint={props.option.name} />
      <Switch.Root
        className="acpConfigSwitch"
        aria-label={props.option.name}
        checked={props.checked}
        disabled={props.disabled}
        onCheckedChange={props.onCheckedChange}
      >
        <Switch.Thumb className="acpConfigSwitchThumb" />
      </Switch.Root>
    </ControlHint>
  )
}

function ControlHint(props: {
  className: string
  hint: string
  children: ReactNode
  configId?: string
  controlId?: string
}) {
  return (
    <div className={props.className} data-hint={props.hint} data-config-id={props.configId} data-control-id={props.controlId}>
      {props.children}
    </div>
  )
}

function isModelSelectOption(option: ConfigOptionView): option is ConfigSelectOptionView {
  if (option.type !== "select") return false
  if (option.options.length === 0) return false
  if (option.category === "model") return true
  if (isModelText(option.id) || isModelText(option.name)) return true
  return option.options.some(choice => isKnownModelText(choice.value) || isKnownModelText(choice.name))
}

function isRenderableConfigOption(option: ConfigOptionView): boolean {
  return option.type === "boolean" || option.options.length > 0
}

function configOptionDisplayName(option: ConfigSelectOptionView): string {
  return option.id === "mode" ? "Session mode" : option.name
}

function toBooleanSelectOption(option: ConfigSelectOptionView): { checked: boolean; trueValue: string; falseValue: string } | null {
  if (option.options.length !== 2) return null
  const trueChoice = option.options.find(choice => isTrueChoice(choice.value) || isTrueChoice(choice.name))
  const falseChoice = option.options.find(choice => isFalseChoice(choice.value) || isFalseChoice(choice.name))
  if (!trueChoice || !falseChoice || trueChoice.value === falseChoice.value) return null
  return {
    checked: option.currentValue === trueChoice.value,
    trueValue: trueChoice.value,
    falseValue: falseChoice.value,
  }
}

function isTrueChoice(value: string): boolean {
  const normalized = normalizeBooleanChoice(value)
  return normalized === "true"
    || normalized === "on"
    || normalized === "yes"
    || normalized === "enabled"
    || normalized === "enable"
    || normalized === "1"
}

function isFalseChoice(value: string): boolean {
  const normalized = normalizeBooleanChoice(value)
  return normalized === "false"
    || normalized === "off"
    || normalized === "no"
    || normalized === "disabled"
    || normalized === "disable"
    || normalized === "0"
}

function normalizeBooleanChoice(value: string): string {
  return value.trim().toLocaleLowerCase()
}

function configOptionIconKind(option: ConfigOptionView): AcpControlIconKind {
  const id = option.id.toLocaleLowerCase()
  const name = option.name.toLocaleLowerCase()
  const category = option.category?.toLocaleLowerCase()
  if (id === "mode") return "mode"
  if (id === "model" || category === "model") return "model"
  if (id === "effort" || category === "thought_level") return "effort"
  if (id === "brave_mode" || name.includes("brave") || name.includes("safe") || name.includes("security")) return "shield"
  if (id === "debug_mode" || name.includes("debug")) return "debug"
  if (name.includes("think") || name.includes("reason")) return "brain"
  return "toggle"
}

function ControlIcon(props: { kind: AcpControlIconKind; hint: string }) {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger asChild>
        <span className={`acpControlIcon acpControlIcon--${props.kind}`} aria-hidden="true">
          <jb-icon src={acpIconSrc(acpControlIconPath(props.kind))} />
        </span>
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content className="acpControlTooltip" side="top" align="center" sideOffset={6}>
          {props.hint}
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  )
}

function isModelText(value: string | undefined): boolean {
  return value?.toLocaleLowerCase().includes("model") === true
}

function isKnownModelText(value: string | undefined): boolean {
  const normalized = value?.toLocaleLowerCase() ?? ""
  return normalized.includes("gemini")
    || normalized.includes("claude")
    || normalized.includes("gpt")
    || normalized.includes("llama")
    || normalized.includes("mistral")
    || normalized.includes("sonnet")
    || normalized.includes("opus")
    || normalized.includes("haiku")
}
