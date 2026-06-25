// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { Switch } from "radix-ui"
import type { ConfigOptionView, ConfigSelectOptionView, SessionModeView } from "../model/types"
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
  const selectedMode = props.modes.find(mode => mode.id === props.currentModeId)
  const modelOption = props.configOptions.find(isModelSelectOption)
  const selectedModel = modelOption?.options.find(option => option.value === modelOption.currentValue)
  const otherConfigOptions = props.configOptions.filter(option => option !== modelOption)

  return (
    <div className="acpModelPicker">
      <label className="acpModelPickerControl" title={selectedMode?.description}>
        <span className="acpModelPickerLabel">Mode</span>
        <ModelSelector.Root
          value={props.currentModeId ?? ""}
          disabled={props.disabled || props.modes.length === 0}
          onValueChange={props.onSelectMode}
        >
          <ModelSelector.Trigger>
            {selectedMode?.name ?? (props.modes.length > 0 ? "Select mode..." : "No modes")}
          </ModelSelector.Trigger>
          {props.modes.length > 0 ? (
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
          ) : null}
        </ModelSelector.Root>
      </label>
      <label className="acpModelPickerControl" title={modelOption?.description}>
        <span className="acpModelPickerLabel">Model</span>
        <ModelSelector.Root
          value={modelOption?.currentValue ?? ""}
          disabled={props.disabled || !modelOption || modelOption.options.length === 0}
          onValueChange={value => {
            if (modelOption) props.onSelectConfigOption(modelOption, value)
          }}
        >
          <ModelSelector.Trigger>
            {selectedModel?.name ?? (modelOption ? "Select model..." : "No models")}
          </ModelSelector.Trigger>
          {modelOption ? (
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
          ) : null}
        </ModelSelector.Root>
      </label>
      {otherConfigOptions.map(option => (
        <ConfigOptionControl
          key={option.id}
          option={option}
          disabled={props.disabled}
          onChange={value => props.onSelectConfigOption(option, value)}
        />
      ))}
    </div>
  )
}

function ConfigOptionControl(props: {
  option: ConfigOptionView
  disabled: boolean
  onChange: (value: string | boolean) => void
}) {
  const { option } = props
  if (option.type === "select") {
    return (
      <label className="acpModelPickerControl" title={option.description}>
        <span className="acpModelPickerLabel">{option.name}</span>
        <Select
          className="acpConfigOptionSelect"
          value={option.currentValue}
          disabled={props.disabled || option.options.length === 0}
          placeholder="Select..."
          options={option.options.map(choice => ({
            value: choice.value,
            label: choice.name,
            textValue: `${choice.name} ${choice.value} ${choice.description ?? ""}`,
          }))}
          onValueChange={props.onChange}
        />
      </label>
    )
  }

  return (
    <label className="acpModelPickerControl acpConfigBooleanControl" title={option.description}>
      <span className="acpModelPickerLabel">{option.name}</span>
      <Switch.Root
        className="acpConfigSwitch"
        checked={option.currentValue}
        disabled={props.disabled}
        onCheckedChange={props.onChange}
      >
        <Switch.Thumb className="acpConfigSwitchThumb" />
      </Switch.Root>
    </label>
  )
}

function isModelSelectOption(option: ConfigOptionView): option is ConfigSelectOptionView {
  if (option.type !== "select") return false
  if (option.category === "model") return true
  if (isModelText(option.id) || isModelText(option.name)) return true
  return option.options.some(choice => isKnownModelText(choice.value) || isKnownModelText(choice.name))
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
