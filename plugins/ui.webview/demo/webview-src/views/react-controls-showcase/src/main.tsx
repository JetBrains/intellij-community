// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import "@jetbrains/intellij-webview-react-controls/styles.css"
import "@jetbrains/intellij-webview-controls/define/all"
import "@jetbrains/intellij-webview-controls/jsx"
import React, { useEffect, useState } from "react"
import { createRoot } from "react-dom/client"
import type { JbControlOption } from "@jetbrains/intellij-webview-controls"
import {
  JbControlChrome,
  JbMenuButton,
  JbMenuCheckboxItem,
  JbMenuItem,
  JbMenuRadioItem,
  JbMenuSeparator,
  JbPopover,
  JbSelect,
  JbSelectItem,
  JbSelectSeparator,
  JbTooltip,
  MenuGroup,
  MenuGroupLabel,
  MenuRadioGroup,
  PopoverClose,
  PopoverDescription,
  PopoverTitle,
  SelectGroup,
  SelectGroupLabel,
  TooltipProvider,
} from "@jetbrains/intellij-webview-react-controls"

const root = document.getElementById("root")
if (!root) {
  throw new Error("#root missing")
}

interface SelectOption {
  value: string
  label: string
  textValue?: string
  disabled?: boolean
}

const projectScopes: SelectOption[] = [
  { value: "project", label: "Project" },
  { value: "module", label: "Module" },
  { value: "file", label: "Current file" },
]

const uiDslItems = ["Project", "Module", "File"].map(toOption)
const densityItems = ["Default", "Compact", "Toolbar"].map(toOption)
const statusItems = ["Problems", "Preview", "Events"].map(toOption)
const buildItems = ["Incremental", "Full", "Rebuild"].map(toOption)

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

function toOption(value: string): JbControlOption {
  return {
    value: value.toLowerCase().replace(/\s+/g, "-"),
    label: value,
  }
}

function ReactControlsShowcase() {
  const [scope, setScope] = useState("project")
  const [runtime, setRuntime] = useState("jbr")
  const [inspection, setInspection] = useState("syntax")
  const [autoSave, setAutoSave] = useState(true)
  const [highlightMode, setHighlightMode] = useState("changed")
  const [lastAction, setLastAction] = useState("No menu action yet")
  useItemsControl("react-lit-select", uiDslItems, "project")
  useItemsControl("react-lit-combobox", uiDslItems, "module")
  useItemsControl("react-lit-menu-button", buildItems, "incremental")
  useItemsControl("react-lit-dropdown-link", buildItems, "full")
  useItemsControl("react-lit-radio-group", densityItems, "default")
  useItemsControl("react-lit-segmented", densityItems, "compact")
  useItemsControl("react-lit-tabs", statusItems, "problems")

  return (
    <TooltipProvider delay={250} closeDelay={80}>
      <div className="reactShowcaseShell">
        <header className="reactShowcaseHeader">
          <h1>React controls</h1>
          <p>React consumption parity for the framework-neutral jb-* controls, plus Base UI-backed composite controls.</p>
        </header>

        <div className="reactShowcaseGrid">
          <section className="reactShowcasePanel reactShowcaseWidePanel">
            <p className="reactShowcasePanelTitle">Basic actions and toolbar controls from jb-*</p>
            <div className="reactShowcaseButtonRow">
              <jb-button variant="primary">Run</jb-button>
              <jb-button>Cancel</jb-button>
              <jb-button variant="danger">Delete</jb-button>
              <jb-button variant="link">Open Settings</jb-button>
              <jb-button size="small">Small</jb-button>
            </div>
            <div className="reactShowcaseToolbarRow">
              <jb-action-button label="Back"><span className="reactShowcaseIcon reactShowcaseIconBack" aria-hidden="true"></span></jb-action-button>
              <jb-action-button label="Refresh"><span className="reactShowcaseIcon reactShowcaseIconRefresh" aria-hidden="true"></span></jb-action-button>
              <jb-action-button label="Pinned" selected><span className="reactShowcaseIcon reactShowcaseIconPin" aria-hidden="true"></span></jb-action-button>
              <jb-menu-button id="react-lit-menu-button" label="Build"></jb-menu-button>
              <jb-dropdown-link id="react-lit-dropdown-link" label="Profile"></jb-dropdown-link>
              <jb-context-help text="Context help is a framework-neutral jb-* control consumed directly from React."></jb-context-help>
              <span className="reactShowcaseIconText"><jb-icon label="Settings"><span className="reactShowcaseIcon reactShowcaseIconSettings" aria-hidden="true"></span></jb-icon><jb-text>Icon</jb-text></span>
            </div>
          </section>

          <section className="reactShowcasePanel reactShowcaseWidePanel">
            <p className="reactShowcasePanelTitle">Fields, labels, help text, and text inputs from jb-*</p>
            <div className="reactShowcaseTwoColumns">
              <div className="reactShowcaseFormStack">
                <jb-field label="Name:" help="Text field with label and help text." required>
                  <jb-text-field value="WebView demo"></jb-text-field>
                </jb-field>
                <jb-field label="Password:">
                  <jb-password-field value="secret"></jb-password-field>
                </jb-field>
                <jb-field label="Arguments:" warning="Use warning state for recoverable configuration issues.">
                  <jb-text-area value="--stacktrace" rows="3"></jb-text-area>
                </jb-field>
              </div>
              <div className="reactShowcaseFormStack">
                <jb-field label="Scope:">
                  <jb-select id="react-lit-select" value="project"></jb-select>
                </jb-field>
                <jb-field label="Chooser:">
                  <jb-combobox id="react-lit-combobox" value="module"></jb-combobox>
                </jb-field>
                <jb-field label="Expandable:">
                  <jb-expandable-text-field value="-Didea.is.internal=true"></jb-expandable-text-field>
                </jb-field>
              </div>
            </div>
          </section>

          <section className="reactShowcasePanel reactShowcaseWidePanel">
            <p className="reactShowcasePanelTitle">Choice, numeric, and range controls from jb-*</p>
            <div className="reactShowcaseTwoColumns">
              <div className="reactShowcaseFormStack">
                <jb-checkbox checked>Enable preview</jb-checkbox>
                <jb-checkbox indeterminate>Partial selection</jb-checkbox>
                <jb-checkbox disabled>Disabled option</jb-checkbox>
                <jb-radio name="react-standalone-radio" value="one" checked>Standalone radio</jb-radio>
                <jb-radio-group id="react-lit-radio-group" label="Density" value="default"></jb-radio-group>
              </div>
              <div className="reactShowcaseFormStack">
                <jb-field label="Port:" error="Invalid values are rendered through shared field chrome.">
                  <jb-number-field invalid value="99" min="1024" max="65535"></jb-number-field>
                </jb-field>
                <jb-field label="Workers:">
                  <jb-spinner value="4" min="1" max="16"></jb-spinner>
                </jb-field>
                <jb-field label="Memory:">
                  <jb-slider value="64" min="0" max="128"></jb-slider>
                </jb-field>
                <jb-segmented-control id="react-lit-segmented" value="compact"></jb-segmented-control>
              </div>
            </div>
          </section>

          <section className="reactShowcasePanel reactShowcaseWidePanel">
            <p className="reactShowcasePanelTitle">Structure, text, tabs, and disclosure from jb-*</p>
            <div className="reactShowcaseTwoColumns">
              <jb-field-group label="Build options">
                <div className="reactShowcaseFieldGroupBody">
                  <jb-label required>Target:</jb-label>
                  <jb-help-text>Labels, help text, and regular text are framework-neutral custom elements.</jb-help-text>
                  <jb-text weight="medium">intellij.platform.ui.webview.demo</jb-text>
                  <jb-text tone="muted">Muted secondary text</jb-text>
                  <jb-help-text tone="warning">Warning help text</jb-help-text>
                  <jb-help-text tone="error">Error help text</jb-help-text>
                  <jb-separator></jb-separator>
                  <jb-disclosure label="Advanced options" open>
                    <div className="reactShowcaseDisclosureBody">
                      <jb-checkbox checked>Use remote cache</jb-checkbox>
                      <jb-text-field value="--keep-going"></jb-text-field>
                    </div>
                  </jb-disclosure>
                </div>
              </jb-field-group>
              <div className="reactShowcaseFormStack">
                <jb-tabs id="react-lit-tabs" value="problems">
                  <div className="reactShowcaseTabBody">
                    <jb-text weight="medium">Problems</jb-text>
                    <jb-text tone="muted">The active tab controls the content area.</jb-text>
                  </div>
                </jb-tabs>
                <div className="reactShowcaseSeparatorBlock">
                  <span>Before separator</span>
                  <jb-separator></jb-separator>
                  <span>After separator</span>
                </div>
              </div>
            </div>
          </section>

          <section className="reactShowcasePanel">
            <p className="reactShowcasePanelTitle">Select</p>
            <div className="reactShowcaseFormStack">
              <label className="reactShowcaseField">
                <span>Scope:</span>
                <JbSelect value={scope} onValueChange={value => setScope(value ?? "project")} options={projectScopes} triggerAriaLabel="Scope" />
              </label>
              <label className="reactShowcaseField">
                <span>Runtime:</span>
                <JbSelect value={runtime} onValueChange={value => setRuntime(value ?? "jbr")} triggerAriaLabel="Runtime">
                  <SelectGroup>
                    <SelectGroupLabel className="reactShowcaseGroupLabel">Bundled</SelectGroupLabel>
                    <JbSelectItem value="jbr" label="JetBrains Runtime">JetBrains Runtime</JbSelectItem>
                    <JbSelectItem value="webview2" label="WebView2">WebView2</JbSelectItem>
                  </SelectGroup>
                  <JbSelectSeparator />
                  <SelectGroup>
                    <SelectGroupLabel className="reactShowcaseGroupLabel">External</SelectGroupLabel>
                    <JbSelectItem value="system" label="System browser">System browser</JbSelectItem>
                    <JbSelectItem value="legacy" label="Legacy engine" disabled>Legacy engine</JbSelectItem>
                  </SelectGroup>
                </JbSelect>
              </label>
            </div>
          </section>

          <section className="reactShowcasePanel">
            <p className="reactShowcasePanelTitle">Menu</p>
            <div className="reactShowcaseFormStack">
              <div className="reactShowcaseInlineControls">
                <JbMenuButton label="Actions" triggerAriaLabel="Actions menu">
                  <JbMenuItem shortcut="Ctrl+R" onClick={() => setLastAction("Run inspection")}>Run inspection</JbMenuItem>
                  <JbMenuItem shortcut="Ctrl+Alt+L" onClick={() => setLastAction("Reformat selection")}>Reformat selection</JbMenuItem>
                  <JbMenuItem disabled>Attach debugger</JbMenuItem>
                  <JbMenuSeparator />
                  <JbMenuCheckboxItem checked={autoSave} onCheckedChange={setAutoSave}>Auto-save results</JbMenuCheckboxItem>
                  <JbMenuSeparator />
                  <MenuGroup>
                    <MenuGroupLabel className="reactShowcaseGroupLabel">Highlight</MenuGroupLabel>
                    <MenuRadioGroup value={highlightMode} onValueChange={setHighlightMode}>
                      <JbMenuRadioItem value="changed">Changed files</JbMenuRadioItem>
                      <JbMenuRadioItem value="all">All files</JbMenuRadioItem>
                      <JbMenuRadioItem value="none">None</JbMenuRadioItem>
                    </MenuRadioGroup>
                  </MenuGroup>
                </JbMenuButton>
                <JbMenuButton label="Compact" compact triggerAriaLabel="Compact menu">
                  <JbMenuItem onClick={() => setLastAction("Compact menu item")}>Compact action</JbMenuItem>
                  <JbMenuItem disabled>Disabled action</JbMenuItem>
                </JbMenuButton>
              </div>
              <p className="reactShowcaseStatus">{lastAction}; auto-save {autoSave ? "on" : "off"}; highlight {highlightMode}</p>
            </div>
          </section>

          <section className="reactShowcasePanel">
            <p className="reactShowcasePanelTitle">Popover</p>
            <div className="reactShowcaseInlineControls">
              <JbPopover trigger="Build details" triggerAriaLabel="Build details">
                <PopoverTitle>Build details</PopoverTitle>
                <PopoverDescription>Popover content can hold richer controls while sharing portal and focus-leave behavior.</PopoverDescription>
                <div className="reactShowcasePopoverRows">
                  <span>Target</span><strong>@community//plugins/ui.webview/demo:demo</strong>
                  <span>Status</span><strong>Up to date</strong>
                </div>
                <PopoverClose className="reactShowcaseTextButton">Close</PopoverClose>
              </JbPopover>
              <JbPopover trigger="?" compact triggerAriaLabel="Compact popover">
                <PopoverTitle>Compact trigger</PopoverTitle>
                <PopoverDescription>The focus ring still belongs to the shared chrome.</PopoverDescription>
              </JbPopover>
            </div>
          </section>

          <section className="reactShowcasePanel">
            <p className="reactShowcasePanelTitle">Tooltip</p>
            <div className="reactShowcaseInlineControls">
              <JbTooltip trigger="?">Tooltip popup in the shared portal root.</JbTooltip>
              <JbTooltip trigger="Top" side="top">Tooltip above the trigger.</JbTooltip>
              <JbTooltip trigger="Right" side="right">Tooltip on the right side.</JbTooltip>
              <JbTooltip trigger="Disabled" disabled>Disabled tooltip does not open.</JbTooltip>
            </div>
          </section>

          <section className="reactShowcasePanel">
            <p className="reactShowcasePanelTitle">States and chrome</p>
            <div className="reactShowcaseFormStack">
              <label className="reactShowcaseField">
                <span>Compact:</span>
                <JbSelect value={inspection} onValueChange={value => setInspection(value ?? "syntax")} compact triggerAriaLabel="Inspection">
                  <JbSelectItem value="syntax" label="Syntax">Syntax</JbSelectItem>
                  <JbSelectItem value="semantic" label="Semantic">Semantic</JbSelectItem>
                  <JbSelectItem value="whole-project" label="Whole project">Whole project</JbSelectItem>
                </JbSelect>
              </label>
              <label className="reactShowcaseField">
                <span>Disabled:</span>
                <JbSelect value="locked" disabled options={[{ value: "locked", label: "Locked by host state" }]} triggerAriaLabel="Disabled state" />
              </label>
              <label className="reactShowcaseField">
                <span>Invalid:</span>
                <JbSelect value="missing" invalid options={[{ value: "missing", label: "Missing SDK" }]} triggerAriaLabel="Invalid state" />
              </label>
              <label className="reactShowcaseField">
                <span>Chrome:</span>
                <JbControlChrome className="reactShowcaseChromeSample"><span>Custom trigger surface</span></JbControlChrome>
              </label>
            </div>
          </section>
        </div>
      </div>
    </TooltipProvider>
  )
}

createRoot(root).render(<ReactControlsShowcase />)
