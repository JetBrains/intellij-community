// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import "@jetbrains/intellij-webview-controls/define/all"
import "@jetbrains/intellij-webview-controls/jsx"
import React, { useEffect, useMemo, useRef, useState } from "react"
import { createRoot } from "react-dom/client"
import type { JbControlOption } from "@jetbrains/intellij-webview-controls"
import { AllIcons, defineWebViewNotification, webView } from "@jetbrains/intellij-webview"

const root = document.getElementById("root")
if (!root) {
  throw new Error("#root missing")
}

const openSourceNotification = defineWebViewNotification<Record<string, never>>("demo/uiDslShowcase/openSource")

const icons = {
  add: AllIcons.src("expui/general/add.svg"),
  bulb: AllIcons.src("expui/codeInsight/quickfixOffBulb.svg"),
  expand: AllIcons.src("expui/inline/expand.svg"),
  externalLink: AllIcons.src("expui/ide/externalLink.svg"),
  folder: AllIcons.src("expui/nodes/folder.svg"),
  gear: AllIcons.src("expui/general/moreVertical.svg"),
  info: AllIcons.src("expui/status/info.svg"),
}

const item1to3 = ["Item 1", "Item 2", "Item 3"].map(toOption)
const singleLineRadioItems = ["Option 1", "Option 2", "Option 3"].map(toOption)
const bindValueItems: JbControlOption[] = [
  { value: "value-1", label: "Value = 1" },
  { value: "value-2", label: "Value = 2" },
]
const componentsRadioItems: JbControlOption[] = [
  { value: "value-1", label: "Value 1" },
  { value: "value-2", label: "Value 2" },
  { value: "value-3", label: "Value 3" },
]
const actionsItems: JbControlOption[] = [
  { value: "action-1", label: "Action 1" },
  { value: "action-2", label: "Action 2" },
]
const tabItems: JbControlOption[] = [
  { value: "tab-1", label: "Tab 1" },
  { value: "tab-2", label: "Tab 2" },
  { value: "last-tab", label: "Last Tab" },
]
const comboItems: JbControlOption[] = [
  { value: "item-1", label: "Item 1" },
  { value: "item-2", label: "Item 2" },
]

interface ItemsElement extends HTMLElement {
  items: JbControlOption[]
  value: string
}

function useItemsControl(id: string, items: JbControlOption[], value: string): void {
  useEffect(() => {
    const element = document.getElementById(id) as ItemsElement | null
    if (!element) return
    element.items = items
    element.value = value
  }, [id, items, value])
}

function toOption(label: string): JbControlOption {
  return {
    label,
    value: label.toLowerCase().replace(/\s+/g, "-"),
  }
}

function boolAttr(value: boolean): "" | undefined {
  return value ? "" : undefined
}

function eventValue(event: Event): string {
  return (event as CustomEvent<{ value?: string }>).detail?.value ?? ""
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function formatDecimal(value: number): string {
  return value.toFixed(2)
}

function notifyOpenSource(): void {
  try {
    void webView.notification(openSourceNotification).send({})
  }
  catch (error) {
    console.warn("UI DSL showcase source navigation is unavailable", error)
  }
}

function Icon(props: { src: string, label?: string, className?: string }) {
  return <jb-icon className={props.className ?? "dslSmallIcon"} src={props.src} label={props.label ?? ""}></jb-icon>
}

function DslSection(props: { title: string, children: React.ReactNode }) {
  return (
    <section className="dslSection" aria-labelledby={`${props.title.toLowerCase()}-section-title`}>
      <h2 className="dslSectionTitle" id={`${props.title.toLowerCase()}-section-title`}>{props.title}</h2>
      {props.children}
    </section>
  )
}

function DslGroup(props: { title: string, children: React.ReactNode }) {
  return (
    <section className="dslGroup" aria-label={props.title}>
      <div className="dslGroupTitle">{props.title}</div>
      <div className="dslRows">{props.children}</div>
    </section>
  )
}

function DslLabel(props: { children?: React.ReactNode, empty?: boolean }) {
  return <div className={props.empty ? "dslLabel dslLabelEmpty" : "dslLabel"}>{props.children}</div>
}

function DslCell(props: { children: React.ReactNode, column?: boolean, top?: boolean, className?: string }) {
  const classes = ["dslCell", "webviewDemoNoWrapControls", props.column ? "dslCellColumn" : "", props.top ? "dslCellTop" : "", props.className ?? ""]
    .filter(Boolean)
    .join(" ")
  return <div className={classes}>{props.children}</div>
}

function DslRightComment(props: { children: React.ReactNode }) {
  return <div className="dslRightComment">{props.children}</div>
}

function DslRowComment(props: { children: React.ReactNode }) {
  return <div className="dslRowComment">{props.children}</div>
}

function DslInlineHelp(props: { children?: React.ReactNode, text: string }) {
  return (
    <span className="dslInlineHelp">
      {props.children}
      <jb-context-help text={props.text}></jb-context-help>
    </span>
  )
}

function DslRow(props: {
  label?: React.ReactNode
  children: React.ReactNode
  rightComment?: React.ReactNode
  rowComment?: React.ReactNode
  noLabel?: boolean
  independent?: boolean
  fullWidth?: boolean
  top?: boolean
  cellClassName?: string
}) {
  const classes = [
    "dslRow",
    props.noLabel ? "dslRowNoLabel" : "",
    props.independent ? "dslRowIndependent" : "",
    props.fullWidth ? "dslRowFullWidth" : "",
    props.top ? "dslRowTop" : "",
  ].filter(Boolean).join(" ")

  return (
    <div className={classes}>
      <DslLabel empty={props.noLabel}>{props.label}</DslLabel>
      <DslCell top={props.top} className={props.cellClassName}>{props.children}</DslCell>
      {props.rightComment ? <DslRightComment>{props.rightComment}</DslRightComment> : null}
      {props.rowComment ? <DslRowComment>{props.rowComment}</DslRowComment> : null}
    </div>
  )
}

function BrowserLink(props: { children: React.ReactNode, href: string }) {
  return (
    <a className="dslBrowserLink" href={props.href} target="_blank" rel="noreferrer">
      {props.children}
      <Icon className="dslExternalIcon" src={icons.externalLink} label="External link" />
    </a>
  )
}

function SegmentedButton(props: { value: string, onChange: (value: string) => void }) {
  return (
    <div className="dslSegmented" role="radiogroup" aria-label="Segmented button">
      {[
        { value: "button-1", label: "Button 1" },
        { value: "button-2", label: "Button 2" },
        { value: "button-last", label: "Button Last", info: true },
      ].map(item => (
        <button
          aria-checked={props.value === item.value}
          className={props.value === item.value ? "dslSegment isSelected" : "dslSegment"}
          key={item.value}
          onClick={() => props.onChange(item.value)}
          role="radio"
          type="button"
        >
          <span>{item.label}</span>
          {item.info ? <Icon src={icons.info} label="Info" /> : null}
        </button>
      ))}
    </div>
  )
}

function TabbedPaneHeader(props: { value: string, onChange: (value: string) => void }) {
  const activeLabel = tabItems.find(item => item.value === props.value)?.label ?? "Tab 1"
  return (
    <div className="dslTabs">
      <div className="dslTabList" role="tablist" aria-label="Tabbed pane header">
        {tabItems.map(item => (
          <button
            aria-selected={props.value === item.value}
            className={props.value === item.value ? "dslTab isSelected" : "dslTab"}
            key={item.value}
            onClick={() => props.onChange(item.value)}
            role="tab"
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="dslTabPanel" role="tabpanel">Selected: {activeLabel}</div>
    </div>
  )
}

function TextFieldWithBrowseButton() {
  return (
    <div className="dslCompositeField">
      <jb-text-field value="C:/workspace/project" aria-label="Path"></jb-text-field>
      <jb-action-button className="dslBrowseButton" label="Browse">
        <Icon src={icons.folder} label="Browse" />
      </jb-action-button>
    </div>
  )
}

function ExpandableTextField() {
  const [expanded, setExpanded] = useState(false)
  const [value, setValue] = useState("one line expandable text")

  useEffect(() => {
    const element = document.getElementById("components-expandable-field") as HTMLElement | null
    if (!element) return
    const listener = (event: Event) => setValue(eventValue(event))
    element.addEventListener("jb-change", listener)
    return () => element.removeEventListener("jb-change", listener)
  }, [])

  return (
    <div className={expanded ? "dslCompositeFieldExpanded" : "dslCompositeField"}>
      <jb-text-field id="components-expandable-field" value={value} aria-label="Expandable text"></jb-text-field>
      <jb-action-button
        className="dslBrowseButton"
        expanded={boolAttr(expanded)}
        label={expanded ? "Collapse" : "Expand"}
        onClick={() => setExpanded(!expanded)}
      >
        <Icon src={icons.expand} label={expanded ? "Collapse" : "Expand"} />
      </jb-action-button>
      {expanded ? <jb-text-area className="dslExpandedTextArea" rows="4" value={value}></jb-text-area> : null}
    </div>
  )
}

function ExtendableTextField() {
  return (
    <div className="dslCompositeField dslExtendableField">
      <jb-text-field value="Text with extension" aria-label="Extendable text"></jb-text-field>
      <jb-action-button className="dslExtendButton" label="Add">
        <Icon src={icons.add} label="Add" />
      </jb-action-button>
    </div>
  )
}

function DslSpinner(props: {
  id: string
  value: string
  min: number
  max: number
  step: number
  onStep: (direction: -1 | 1) => void
}) {
  return (
    <div className="dslSpinner">
      <jb-number-field id={props.id} value={props.value} min={props.min} max={props.max} step={props.step}></jb-number-field>
      <span className="dslSpinnerButtons">
        <button className="dslSpinnerButton" type="button" aria-label="Increment" onClick={() => props.onStep(1)}>
          <span className="dslArrowUp" aria-hidden="true"></span>
        </button>
        <button className="dslSpinnerButton" type="button" aria-label="Decrement" onClick={() => props.onStep(-1)}>
          <span className="dslArrowDown" aria-hidden="true"></span>
        </button>
      </span>
    </div>
  )
}

function UiDslShowcase() {
  const pageRef = useRef<HTMLDivElement>(null)
  const [optionEnabled, setOptionEnabled] = useState(false)
  const [singleLineRadio, setSingleLineRadio] = useState("option-1")
  const [bindValueRadio, setBindValueRadio] = useState("value-outside-model")
  const [componentsRadio, setComponentsRadio] = useState("value-2")
  const [segmentedValue, setSegmentedValue] = useState("button-1")
  const [tabValue, setTabValue] = useState("tab-1")
  const [dropdownValue, setDropdownValue] = useState("item-1")
  const [comboValue, setComboValue] = useState("item-1")
  const [spinnerInt, setSpinnerInt] = useState("42")
  const [spinnerDouble, setSpinnerDouble] = useState("42.00")
  const [sliderValue, setSliderValue] = useState("5")
  const [commentClickStatus, setCommentClickStatus] = useState("No comment link clicked")
  const [intTextValue, setIntTextValue] = useState("42")
  const [actionsStatus, setActionsStatus] = useState("No action selected")

  useItemsControl("examples-single-line-radio", singleLineRadioItems, singleLineRadio)
  useItemsControl("examples-bind-value-radio", bindValueItems, bindValueRadio)
  useItemsControl("components-radio-group", componentsRadioItems, componentsRadio)
  useItemsControl("components-actions-button", actionsItems, "")
  useItemsControl("components-dropdown-link", item1to3, dropdownValue)
  useItemsControl("components-combo-box", comboItems, comboValue)

  const intTextInvalid = useMemo(() => {
    if (intTextValue.trim() === "") return true
    const numeric = Number(intTextValue)
    return Number.isNaN(numeric) || numeric < 0 || numeric > 100
  }, [intTextValue])

  useEffect(() => {
    const element = pageRef.current
    if (!element) return

    const onJbChange = (event: Event) => {
      const target = event.target as HTMLElement | null
      if (!target) return
      const value = eventValue(event)
      switch (target.id) {
        case "examples-single-line-radio":
          setSingleLineRadio(value)
          break
        case "examples-bind-value-radio":
          setBindValueRadio(value)
          break
        case "examples-option-checkbox":
          setOptionEnabled(value !== "")
          break
        case "components-radio-group":
          setComponentsRadio(value)
          break
        case "components-dropdown-link":
          setDropdownValue(value)
          break
        case "components-combo-box":
          setComboValue(value)
          break
        case "components-int-field":
          setIntTextValue(normalizeIntegerText(value))
          break
        case "components-spinner-int":
          setSpinnerInt(normalizeIntegerText(value))
          break
        case "components-spinner-double":
          setSpinnerDouble(normalizeDecimalText(value))
          break
        case "components-slider":
          setSliderValue(value)
          break
      }
    }

    const onJbSelect = (event: Event) => {
      const target = event.target as HTMLElement | null
      if (!target) return
      const value = eventValue(event)
      if (target.id === "components-actions-button") {
        setActionsStatus(value === "action-2" ? "Action 2 selected" : "Action 1 selected")
      }
      else if (target.id === "components-dropdown-link") {
        setDropdownValue(value)
      }
    }

    element.addEventListener("jb-change", onJbChange)
    element.addEventListener("jb-select", onJbSelect)
    return () => {
      element.removeEventListener("jb-change", onJbChange)
      element.removeEventListener("jb-select", onJbSelect)
    }
  }, [])

  function stepIntSpinner(direction: -1 | 1): void {
    setSpinnerInt(current => String(clamp((Number(current) || 0) + direction, 0, 100)))
  }

  function stepDoubleSpinner(direction: -1 | 1): void {
    setSpinnerDouble(current => formatDecimal(clamp((Number(current) || 0) + direction * 0.01, 0, 100)))
  }

  return (
    <div className="uiDslShowcasePage webviewDemoFixedCanvas" ref={pageRef}>
      <header className="uiDslHeader">
        <div className="uiDslHeaderTitle">
          <h1>UI DSL Showcase</h1>
          <p>WebView implementation of UI DSL Examples, Components, and Comments</p>
        </div>
        <button className="uiDslSourceLink" type="button" onClick={notifyOpenSource}>View source</button>
      </header>

      <div className="uiDslSections">
        <ExamplesSection
          bindValueRadio={bindValueRadio}
          optionEnabled={optionEnabled}
        />
        <ComponentsSection
          actionsStatus={actionsStatus}
          comboValue={comboValue}
          componentsRadio={componentsRadio}
          dropdownValue={dropdownValue}
          intTextInvalid={intTextInvalid}
          intTextValue={intTextValue}
          segmentedValue={segmentedValue}
          setSegmentedValue={setSegmentedValue}
          setTabValue={setTabValue}
          sliderValue={sliderValue}
          spinnerDouble={spinnerDouble}
          spinnerInt={spinnerInt}
          stepDoubleSpinner={stepDoubleSpinner}
          stepIntSpinner={stepIntSpinner}
          tabValue={tabValue}
        />
        <CommentsSection
          commentClickStatus={commentClickStatus}
          setCommentClickStatus={setCommentClickStatus}
        />
      </div>
    </div>
  )
}

function normalizeIntegerText(value: string): string {
  if (value.trim() === "") return value
  const numeric = Number(value)
  if (Number.isNaN(numeric)) return value
  return String(clamp(Math.trunc(numeric), 0, 100))
}

function normalizeDecimalText(value: string): string {
  if (value.trim() === "") return value
  const numeric = Number(value)
  if (Number.isNaN(numeric)) return value
  return formatDecimal(clamp(numeric, 0, 100))
}

function ExamplesSection(props: { optionEnabled: boolean, bindValueRadio: string }) {
  return (
    <DslSection title="Examples">
      <DslGroup title="Initializing components with extension functions">
        <DslRow noLabel rightComment="'bold()' works for any component">
          <span className="dslTextStrong">Bold text</span>
        </DslRow>
        <DslRow noLabel rightComment="'selected(true)'">
          <jb-checkbox checked="">Selected CheckBox</jb-checkbox>
        </DslRow>
        <DslRow noLabel rightComment="'selected(true)'">
          <jb-radio name="examples-initial-radio" value="radio">RadioButton</jb-radio>
          <jb-radio name="examples-initial-radio" value="selected" checked="">Selected RadioButton</jb-radio>
        </DslRow>
        <DslRow noLabel rightComment="'text(&quot;Initial text&quot;)'"><jb-text-field className="dslFieldMedium" value="Initial text"></jb-text-field></DslRow>
      </DslGroup>

      <DslGroup title="CheckBox/RadioButton examples">
        <DslRow label="CheckBox/RadioButton Group:" cellClassName="dslCellColumn">
          <div className="dslControlLine dslIndented webviewDemoNoWrapControls">
            <jb-checkbox checked="">CheckBox 1</jb-checkbox>
            <DslInlineHelp text="Context help popup bound to CheckBox 1." />
          </div>
          <div className="dslControlLine dslIndented webviewDemoNoWrapControls">
            <jb-checkbox>CheckBox 2</jb-checkbox>
            <BrowserLink href="https://www.jetbrains.com/help/idea/settings.html">How it works</BrowserLink>
          </div>
        </DslRow>
        <DslRow label="" noLabel rightComment="External links must be marked with the external link icon">
          <span aria-hidden="true"></span>
        </DslRow>
        <DslRow label="Single line:">
          <jb-radio-group id="examples-single-line-radio" value="option-1"></jb-radio-group>
          <DslInlineHelp text="Radio buttons in one row are mutually exclusive." />
        </DslRow>
        <DslRow label="Bind value:" rightComment="'buttonsGroup.bind'">
          <jb-radio-group id="examples-bind-value-radio" value={props.bindValueRadio}></jb-radio-group>
        </DslRow>
        <DslRow label="Option:" rightComment="'enabledIf'">
          <jb-checkbox id="examples-option-checkbox" checked={boolAttr(props.optionEnabled)}>Option</jb-checkbox>
          <jb-text-field
            className="dslFieldMedium"
            disabled={boolAttr(!props.optionEnabled)}
            value="Enabled by option"
          ></jb-text-field>
          <DslInlineHelp text="The text field follows the checkbox state live." />
        </DslRow>
      </DslGroup>

      <DslGroup title="TextField">
        <DslRow label="Default field:">
          <jb-text-field className="dslFieldMedium" value="Text"></jb-text-field>
          <DslInlineHelp text="Default text field with context help." />
        </DslRow>
        <DslRow noLabel>
          <jb-text-field className="dslFieldFillHost" value="Text field fills the remaining row width"></jb-text-field>
          <DslInlineHelp text="Empty label row keeps shared alignment." />
        </DslRow>
        <DslRow label="Don't align very long labels with short ones:" independent>
          <jb-text-field className="dslFieldMedium" value="15"></jb-text-field>
          <span>seconds</span>
          <DslInlineHelp text="Independent rows can opt out of the shared label column." />
        </DslRow>
        <div className="dslParentGrid">
          <DslRow label="Row 1:" rightComment="RowLayout.PARENT_GRID">
            <jb-text-field value="Parent grid field"></jb-text-field>
            <jb-button className="dslEqualButton dslTextButton">Test</jb-button>
            <DslInlineHelp text="Rows share the same inner column sizing." />
          </DslRow>
          <DslRow label="Row 2:">
            <jb-text-field value="Second parent grid field"></jb-text-field>
            <span className="dslReservedButtonSlot" aria-hidden="true"></span>
          </DslRow>
        </div>
      </DslGroup>

      <DslGroup title="Comments">
        <DslRow
          noLabel
          rightComment="Requires restart"
          rowComment="It's important to connect comments to the related cells: they are displayed in the correct location with appropriate styling and used by the accessibility framework"
        >
          <jb-checkbox>A very complex option</jb-checkbox>
          <DslInlineHelp text="Context help belongs to the option cell." />
        </DslRow>
      </DslGroup>

      <DslGroup title="Buttons">
        <DslRow noLabel rightComment="'widthGroup'">
          <jb-button className="dslEqualButton dslTextButton" variant="primary">Default Button</jb-button>
          <jb-button className="dslEqualButton dslTextButton">Button</jb-button>
          <DslInlineHelp text="Width group keeps related buttons equal." />
        </DslRow>
      </DslGroup>
    </DslSection>
  )
}

function ComponentsSection(props: {
  actionsStatus: string
  comboValue: string
  componentsRadio: string
  dropdownValue: string
  intTextInvalid: boolean
  intTextValue: string
  segmentedValue: string
  setSegmentedValue: (value: string) => void
  setTabValue: (value: string) => void
  sliderValue: string
  spinnerDouble: string
  spinnerInt: string
  stepDoubleSpinner: (direction: -1 | 1) => void
  stepIntSpinner: (direction: -1 | 1) => void
  tabValue: string
}) {
  return (
    <DslSection title="Components">
      <DslGroup title="Basic components">
        <DslRow label="checkBox:"><jb-checkbox checked="">checkBox</jb-checkbox></DslRow>
        <DslRow label="threeStateCheckBox:"><jb-checkbox indeterminate="">threeStateCheckBox</jb-checkbox></DslRow>
        <DslRow label="radioButton:">
          <jb-radio-group id="components-radio-group" value={props.componentsRadio}></jb-radio-group>
        </DslRow>
        <DslRow label="button:"><jb-button>button</jb-button></DslRow>
        <DslRow label="actionButton:">
          <jb-action-button className="dslIconButton" label="Quick fix off bulb">
            <Icon src={icons.bulb} label="Quick fix off bulb" />
          </jb-action-button>
        </DslRow>
        <DslRow label="actionsButton:" rowComment={props.actionsStatus}>
          <jb-menu-button id="components-actions-button" label="Actions">
            <Icon src={icons.gear} label="Actions" />
          </jb-menu-button>
        </DslRow>
        <DslRow label="segmentedButton:">
          <SegmentedButton value={props.segmentedValue} onChange={props.setSegmentedValue} />
        </DslRow>
        <DslRow label="tabbedPaneHeader:" top>
          <TabbedPaneHeader value={props.tabValue} onChange={props.setTabValue} />
        </DslRow>
        <DslRow label="label:"><jb-label>label</jb-label></DslRow>
        <DslRow label="text:" top>
          <div className="dslCommonInfo">
            <p className="dslCommonComment">
              Regular text with <button className="dslTextLink" type="button">link</button>, line break,<br />
              and <Icon src={icons.info} label="Info" /> bundled info icon.
            </p>
          </div>
        </DslRow>
        <DslRow label="link:">
          <button className="dslTextLink" type="button">Focusable link</button>
        </DslRow>
        <DslRow label="browserLink:">
          <BrowserLink href="https://www.jetbrains.com/help/idea/">Browser link</BrowserLink>
        </DslRow>
        <DslRow label="dropDownLink:">
          <jb-dropdown-link id="components-dropdown-link" label="Item 1" value={props.dropdownValue}></jb-dropdown-link>
        </DslRow>
        <DslRow label="icon:">
          <span className="dslInlineIconText"><Icon src={icons.info} label="Info" /> icon</span>
        </DslRow>
        <DslRow label="contextHelp:">
          <jb-context-help text="Context help related to the component, displayed in a popup"></jb-context-help>
        </DslRow>
      </DslGroup>

      <DslGroup title="Input components">
        <DslRow label="textField:"><jb-text-field className="dslFieldMedium" value="text"></jb-text-field></DslRow>
        <DslRow label="passwordField:"><jb-password-field className="dslFieldMedium" value="password"></jb-password-field></DslRow>
        <DslRow label="textFieldWithBrowseButton:"><TextFieldWithBrowseButton /></DslRow>
        <DslRow label="expandableTextField:"><ExpandableTextField /></DslRow>
        <DslRow label="extendableTextField:"><ExtendableTextField /></DslRow>
        <DslRow label="intTextField(0..100):">
          <jb-number-field
            className="dslFieldShort"
            id="components-int-field"
            invalid={boolAttr(props.intTextInvalid)}
            max="100"
            min="0"
            value={props.intTextValue}
          ></jb-number-field>
        </DslRow>
        <DslRow label="spinner(0..100):">
          <DslSpinner id="components-spinner-int" value={props.spinnerInt} min={0} max={100} step={1} onStep={props.stepIntSpinner} />
        </DslRow>
        <DslRow label="spinner(0.0..100.0, 0.01):">
          <DslSpinner id="components-spinner-double" value={props.spinnerDouble} min={0} max={100} step={0.01} onStep={props.stepDoubleSpinner} />
        </DslRow>
        <DslRow label="slider(0, 10, 1, 5):">
          <div className="dslSliderBlock">
            <jb-slider id="components-slider" min="0" max="10" step="1" value={props.sliderValue}></jb-slider>
            <div className="dslSliderLabels"><span>0</span><span>5</span><span>10</span></div>
          </div>
        </DslRow>
        <DslRow label="textArea:" top fullWidth>
          <jb-text-area
            className="dslTextAreaWide"
            rows="5"
            value="Text area\nwith several lines\nand top-aligned label"
          ></jb-text-area>
        </DslRow>
        <DslRow label="comboBox:">
          <jb-select className="dslSelectMedium" id="components-combo-box" value={props.comboValue}></jb-select>
        </DslRow>
      </DslGroup>
    </DslSection>
  )
}

function CommentsSection(props: { commentClickStatus: string, setCommentClickStatus: (value: string) => void }) {
  const longString = "A very long string ".repeat(16).trim()
  return (
    <DslSection title="Comments">
      <DslGroup title="Cell Comment">
        <DslRow fullWidth noLabel>
          <p className="dslArbitraryComment">
            Comments related to a cell must be assigned directly to that cell. This ensures proper layout placement and improves support for the accessibility framework
          </p>
        </DslRow>
        <DslRow label="Cells:" top fullWidth>
          <div className="dslThreeFields">
            <div className="dslCellInlineComment">
              <jb-text-field value="textField1"></jb-text-field>
              <span className="dslRightComment">Right comment to textField1</span>
            </div>
            <div className="dslCellBlock">
              <jb-text-field value="textField2"></jb-text-field>
              <span className="dslBottomComment">Bottom comment to textField2</span>
            </div>
            <div className="dslControlLine webviewDemoNoWrapControls">
              <jb-text-field value="textField3"></jb-text-field>
              <jb-context-help text="Context help related to the component, displayed in a popup"></jb-context-help>
            </div>
          </div>
        </DslRow>
      </DslGroup>

      <DslGroup title="Row Comment">
        <DslRow label="Text field:" rowComment="A row comment is placed below the row">
          <jb-text-field className="dslFieldMedium" value="textField"></jb-text-field>
        </DslRow>
      </DslGroup>

      <DslGroup title="Arbitrary Comment">
        <DslRow fullWidth noLabel>
          <p className="dslArbitraryComment">Arbitrary comments can be placed anywhere. They are not related to any cell or row</p>
        </DslRow>
      </DslGroup>

      <DslGroup title="Common Info">
        <DslRow top fullWidth noLabel>
          <div className="dslCommonInfo">
            <p className="dslCommonComment">
              Comments can be an html text with some clickable&nbsp;
              <button className="dslTextLink" type="button" onClick={() => props.setCommentClickStatus("First comment link clicked")}>link</button>
              &nbsp;and even several&nbsp;
              <button className="dslTextLink" type="button" onClick={() => props.setCommentClickStatus("Second comment link clicked")}>links</button>.
            </p>
            <p className="dslStatusText" aria-live="polite">{props.commentClickStatus}</p>
            <p className="dslCommonComment dslInfoLine">
              It's possible to use line breaks and bundled icons<br />
              <Icon src={icons.info} label="Info" /> bundled info icon
            </p>
            <p className="dslCommonComment dslWrappedComment">{longString}</p>
            <p className="dslCommonComment dslNoWrapComment">{longString}</p>
            <p className="dslCommonComment dslMaxLineComment">{longString}</p>
          </div>
        </DslRow>
      </DslGroup>
    </DslSection>
  )
}

createRoot(root).render(<UiDslShowcase />)
