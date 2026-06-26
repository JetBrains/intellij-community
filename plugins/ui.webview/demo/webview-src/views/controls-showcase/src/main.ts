// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import "@jetbrains/intellij-webview-controls/define/all"
import { IconSet } from "@jetbrains/intellij-webview"
import type { JbControlOption } from "@jetbrains/intellij-webview-controls"

interface ItemsControl extends HTMLElement {
  items: JbControlOption[]
  value: string
}

const root = document.getElementById("root")
if (!root) {
  throw new Error("#root missing")
}

const AllIcons = IconSet.define("AllIcons")

interface IconSample {
  name: string
  path: string
}

const iconSamples: IconSample[] = [
  { name: "Run", path: "expui/run/run.svg" },
  { name: "Stop", path: "expui/run/stop.svg" },
  { name: "Pause", path: "expui/run/pause.svg" },
  { name: "Gutter Run", path: "expui/gutter/run.svg" },
  { name: "Refresh", path: "expui/actions/forceRefresh.svg" },
  { name: "Play First", path: "expui/actions/playFirst.svg" },
  { name: "Play Back", path: "expui/actions/playBack.svg" },
  { name: "Play Forward", path: "expui/actions/playForward.svg" },
  { name: "Play Last", path: "expui/actions/playLast.svg" },
  { name: "Add", path: "expui/general/add.svg" },
  { name: "Remove", path: "expui/general/remove.svg" },
  { name: "Delete", path: "expui/general/delete.svg" },
  { name: "Edit", path: "expui/general/edit.svg" },
  { name: "Save", path: "expui/general/save.svg" },
  { name: "Close", path: "expui/general/close.svg" },
  { name: "Search", path: "expui/general/search.svg" },
  { name: "Filter", path: "expui/general/filter.svg" },
  { name: "Settings", path: "expui/general/settings.svg" },
  { name: "Help", path: "expui/general/help.svg" },
  { name: "Export", path: "expui/general/export.svg" },
  { name: "Layout", path: "expui/general/layout.svg" },
  { name: "User", path: "expui/general/user.svg" },
  { name: "Locked", path: "expui/general/locked.svg" },
  { name: "Commit", path: "expui/vcs/commit.svg" },
  { name: "Update", path: "expui/vcs/update.svg" },
  { name: "Diff", path: "expui/vcs/diff.svg" },
  { name: "VCS Remove", path: "expui/vcs/remove.svg" },
  { name: "Breakpoint", path: "expui/breakpoints/breakpoint.svg" },
  { name: "Info", path: "expui/status/info.svg" },
  { name: "Success", path: "expui/status/success.svg" },
  { name: "Warning", path: "expui/status/warning.svg" },
  { name: "Error", path: "expui/status/error.svg" },
  { name: "Folder", path: "expui/nodes/folder.svg" },
  { name: "Package", path: "expui/nodes/package.svg" },
  { name: "Function", path: "expui/nodes/function.svg" },
  { name: "Plugin", path: "expui/nodes/plugin.svg" },
  { name: "Unknown Node", path: "expui/nodes/unknown.svg" },
  { name: "YAML", path: "expui/fileTypes/yaml.svg" },
  { name: "Gradle", path: "expui/fileTypes/gradle.svg" },
  { name: "Docker", path: "expui/fileTypes/docker.svg" },
  { name: "SQL", path: "expui/fileTypes/sql.svg" },
  { name: "Properties", path: "expui/fileTypes/properties.svg" },
  { name: "Run Tool Window", path: "expui/toolwindows/run.svg" },
  { name: "Commit Tool Window", path: "expui/toolwindows/commit.svg" },
  { name: "Profiler Tool Window", path: "expui/toolwindows/profiler.svg" },
  { name: "Structure Tool Window", path: "expui/toolwindows/structure.svg" },
  { name: "Palette Tool Window", path: "expui/toolwindows/palette.svg" },
  { name: "External Link", path: "expui/ide/externalLink.svg" },
]

const sections = {
  "components": {
    title: "Components",
    note: "Batch 1 primitives and first batch 2 composites rendered with Int UI Kit: Islands mapping.",
    render: renderComponents,
  },
  "icons": {
    title: "AllIcons",
    note: "Classpath icon resources rendered through IconSet.define(\"AllIcons\") and the WebView icon asset route.",
    render: renderIcons,
  },
  "labels-help": {
    title: "Labels and help text",
    note: "Labeled input anatomy with inline help and context help affordances.",
    render: renderLabelsAndHelp,
  },
  "validation": {
    title: "Validation",
    note: "Field states for immediate error, warning, and required-input handling.",
    render: renderValidation,
  },
  "states": {
    title: "Enabled, disabled, readonly, hidden",
    note: "Primitive attributes reflected to Web Component hosts and native controls.",
    render: renderStates,
  },
  "groups-disclosure": {
    title: "Groups and disclosure",
    note: "Related fields, radio groups, separators, and progressive disclosure surfaces.",
    render: renderGroupsAndDisclosure,
  },
  "tabs-segmented": {
    title: "Tabs and segmented controls",
    note: "Selection patterns for compact mode switches and tabbed surfaces.",
    render: renderTabsAndSegmented,
  },
  "spacing-density": {
    title: "Spacing, density, responsive layout",
    note: "Default and compact control sizes with responsive wrapping.",
    render: renderSpacingDensity,
  },
  "theme-rendering": {
    title: "Theme rendering",
    note: "Controls consume semantic --jb-* tokens injected by the WebView runtime.",
    render: renderThemeRendering,
  },
} as const

type SectionId = keyof typeof sections

const params = new URLSearchParams(window.location.search)
const sectionId = normalizeSection(params.get("section"))
const section = sections[sectionId]

document.body.dataset.section = sectionId
root.innerHTML = `
  <header class="section-header">
    <h1 class="section-title">${section.title}</h1>
    <p class="section-note">${section.note}</p>
  </header>
  ${section.render()}
`
hydrateControls(root)

function normalizeSection(value: string | null): SectionId {
  return value && value in sections ? value as SectionId : "components"
}

function renderIcons(): string {
  const iconItems = iconSamples.map((icon) => `
    <div class="icon-sample">
      <span class="icon-preview">${renderIconImage(icon.path)}</span>
      <span class="icon-name">${icon.name}</span>
      <code class="icon-path">${icon.path}</code>
    </div>
  `).join("")

  return `
    <div class="showcase-grid icons-showcase-grid">
      <section class="panel icons-panel">
        <p class="panel-title">AllIcons resource paths</p>
        <div class="icon-grid">
          ${iconItems}
        </div>
      </section>
      <section class="panel icons-panel">
        <p class="panel-title">Inline usage</p>
        <div class="form-stack">
          <div class="inline-icon-row">${renderIconImage("expui/run/run.svg")}<jb-text>Run configuration</jb-text></div>
          <div class="inline-icon-row">${renderIconImage("expui/vcs/update.svg")}<jb-text>Update project</jb-text></div>
          <div class="inline-icon-row">${renderIconImage("expui/breakpoints/breakpoint.svg")}<jb-text>Line breakpoint</jb-text></div>
          <jb-help-text>Switch the IDE theme to verify that dark icon variants are requested through a different URL.</jb-help-text>
        </div>
      </section>
    </div>
  `
}

function renderIconImage(path: string): string {
  return `<jb-icon src="${AllIcons.src(path)}"></jb-icon>`
}

function renderComponents(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Buttons and toolbar actions</p>
        <div class="row">
          <jb-button variant="primary">Run</jb-button>
          <jb-button>Cancel</jb-button>
          <jb-button variant="danger">Delete</jb-button>
          <jb-button variant="link">Open settings</jb-button>
        </div>
        <div class="toolbar-row">
          <jb-action-button label="Back">&lt;</jb-action-button>
          <jb-action-button label="Refresh">R</jb-action-button>
          <jb-action-button label="Pinned" selected>P</jb-action-button>
          <jb-menu-button id="toolbar-filter" label="Filter"></jb-menu-button>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Inputs</p>
        <div class="form-stack">
          <jb-field label="Name:" help="Use sentence-style capitalization for labels."><jb-text-field value="Island controls"></jb-text-field></jb-field>
          <jb-field label="Type:"><jb-select id="component-type" value="field"></jb-select></jb-field>
          <jb-field label="Search:"><jb-combobox id="component-search" value="Button"></jb-combobox></jb-field>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Selection</p>
        <div class="form-stack">
          <jb-checkbox checked>Enable preview</jb-checkbox>
          <jb-checkbox indeterminate>Partial selection</jb-checkbox>
          <jb-radio-group id="density-group" label="Density" value="default"></jb-radio-group>
        </div>
      </section>
    </div>
  `
}

function renderLabelsAndHelp(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Inline anatomy</p>
        <div class="form-stack">
          <jb-field label="Output path:" help="The field width follows the expected value length." required><jb-text-field placeholder="Select a directory"></jb-text-field></jb-field>
          <jb-field label="Arguments:" help="Examples belong below the field, not in the placeholder."><jb-text-area value="--stacktrace"></jb-text-area></jb-field>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Text roles</p>
        <div class="form-stack">
          <jb-label required>Project SDK:</jb-label>
          <jb-help-text>Choose a configured SDK or add one from the project structure dialog.</jb-help-text>
          <jb-help-text tone="warning">The selected SDK is deprecated.</jb-help-text>
          <jb-help-text tone="error">The selected SDK is missing.</jb-help-text>
          <div class="row"><jb-context-help text="Context help opens lightweight guidance without leaving the current control."></jb-context-help><jb-text tone="muted">Context help</jb-text></div>
        </div>
      </section>
    </div>
  `
}

function renderValidation(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Error and warning</p>
        <div class="form-stack">
          <jb-field label="Port:" error="Enter a port from 1024 to 65535."><jb-number-field invalid value="99"></jb-number-field></jb-field>
          <jb-field label="Host:" warning="The host responds slowly."><jb-text-field value="staging.internal"></jb-text-field></jb-field>
          <jb-field label="Token:" help="Required fields can keep the confirm action disabled in host UI." required><jb-password-field required placeholder="Required"></jb-password-field></jb-field>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Validation-friendly controls</p>
        <div class="form-stack">
          <jb-field label="Memory:"><jb-slider value="64" min="0" max="128"></jb-slider></jb-field>
          <jb-field label="Workers:"><jb-spinner value="4" min="1" max="16"></jb-spinner></jb-field>
          <jb-field label="Mode:"><jb-select id="validation-mode" value="strict"></jb-select></jb-field>
        </div>
      </section>
    </div>
  `
}

function renderStates(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Primitive attrs</p>
        <div class="form-stack">
          <jb-field label="Enabled:"><jb-text-field value="Editable"></jb-text-field></jb-field>
          <jb-field label="Readonly:"><jb-text-field readonly value="Read-only value"></jb-text-field></jb-field>
          <jb-field label="Disabled:"><jb-text-field disabled value="Disabled value"></jb-text-field></jb-field>
          <jb-button hidden>Hidden action</jb-button>
          <p class="source-row">Hidden controls remain in markup but do not render.</p>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Selection states</p>
        <div class="form-stack">
          <jb-checkbox checked>Checked</jb-checkbox>
          <jb-checkbox readonly checked>Readonly checked</jb-checkbox>
          <jb-checkbox disabled>Disabled unchecked</jb-checkbox>
          <div class="row"><jb-button selected>Selected</jb-button><jb-button pressed>Pressed</jb-button><jb-action-button selected label="Selected">S</jb-action-button></div>
        </div>
      </section>
    </div>
  `
}

function renderGroupsAndDisclosure(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <jb-field-group label="Build options">
          <jb-field label="Target:"><jb-combobox id="build-target" value="intellij.platform.ui.webview"></jb-combobox></jb-field>
          <jb-checkbox checked>Use remote cache</jb-checkbox>
          <jb-separator></jb-separator>
          <jb-radio-group id="build-kind" label="Build kind" value="incremental"></jb-radio-group>
        </jb-field-group>
      </section>
      <section class="panel">
        <p class="panel-title">Disclosure</p>
        <jb-disclosure label="Advanced options" open>
          <div class="form-stack">
            <jb-field label="VM options:"><jb-expandable-text-field value="-Xmx4g"></jb-expandable-text-field></jb-field>
            <jb-checkbox>Keep build logs</jb-checkbox>
          </div>
        </jb-disclosure>
      </section>
    </div>
  `
}

function renderTabsAndSegmented(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Segmented control</p>
        <jb-segmented-control id="view-mode" value="preview"></jb-segmented-control>
        <div class="form-stack">
          <jb-field label="Scope:"><jb-select id="scope-select" value="project"></jb-select></jb-field>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Tabs</p>
        <jb-tabs id="result-tabs" value="problems">
          <div class="form-stack">
            <jb-text weight="medium">Problems</jb-text>
            <jb-text tone="muted">The active tab drives the content area below the tab bar.</jb-text>
          </div>
        </jb-tabs>
      </section>
    </div>
  `
}

function renderSpacingDensity(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Density</p>
        <div class="density-demo">
          <div class="form-stack"><jb-text weight="medium">Default</jb-text><jb-button>Default Button</jb-button><jb-text-field value="Default field"></jb-text-field></div>
          <div class="form-stack"><jb-text weight="medium">Compact</jb-text><jb-button size="small">Small Button</jb-button><div class="toolbar-row"><jb-action-button label="Run">R</jb-action-button><jb-action-button label="Stop">S</jb-action-button><jb-menu-button id="compact-menu" label="More"></jb-menu-button></div></div>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Responsive wrap</p>
        <div class="row">
          <jb-button>Analyze</jb-button>
          <jb-button>Inspect</jb-button>
          <jb-button>Refactor</jb-button>
          <jb-button variant="link">View source</jb-button>
        </div>
      </section>
    </div>
  `
}

function renderThemeRendering(): string {
  return `
    <div class="showcase-grid">
      <section class="panel">
        <p class="panel-title">Token swatches</p>
        <div class="token-grid">
          <div class="token-swatch"><span class="swatch accent"></span><jb-text>Accent</jb-text></div>
          <div class="token-swatch"><span class="swatch selected"></span><jb-text>Selected</jb-text></div>
          <div class="token-swatch"><span class="swatch danger"></span><jb-text>Danger</jb-text></div>
          <div class="token-swatch"><span class="swatch warning"></span><jb-text>Warning</jb-text></div>
          <div class="token-swatch"><span class="swatch panel-bg"></span><jb-text>Panel</jb-text></div>
          <div class="token-swatch"><span class="swatch input-bg"></span><jb-text>Input</jb-text></div>
        </div>
      </section>
      <section class="panel">
        <p class="panel-title">Controls on current theme</p>
        <div class="form-stack">
          <jb-field label="Theme name:"><jb-text-field value="Runtime current theme" readonly></jb-text-field></jb-field>
          <div class="row"><jb-button variant="primary">Primary</jb-button><jb-button>Default</jb-button><jb-checkbox checked>Checked</jb-checkbox></div>
        </div>
      </section>
    </div>
  `
}

function hydrateControls(container: HTMLElement): void {
  setItems(container, "#toolbar-filter", ["All", "Enabled", "Invalid"].map(toOption), "All")
  setItems(container, "#component-type", ["field", "button", "selection", "popup"].map(toOption), "field")
  setItems(container, "#component-search", ["Button", "Checkbox", "Input Field", "Segmented Control"].map(toOption), "Button")
  setItems(container, "#density-group", ["default", "compact"].map(toOption), "default")
  setItems(container, "#validation-mode", ["strict", "lenient"].map(toOption), "strict")
  setItems(container, "#build-target", ["intellij.platform.ui.webview", "intellij.platform.ui.webview.demo"].map(toOption), "intellij.platform.ui.webview")
  setItems(container, "#build-kind", ["incremental", "full"].map(toOption), "incremental")
  setItems(container, "#view-mode", ["preview", "source", "diff"].map(toOption), "preview")
  setItems(container, "#scope-select", ["project", "module", "file"].map(toOption), "project")
  setItems(container, "#result-tabs", ["problems", "preview", "events"].map(toOption), "problems")
  setItems(container, "#compact-menu", ["Pin", "Detach", "Close"].map(toOption), "Pin")
}

function setItems(container: HTMLElement, selector: string, items: JbControlOption[], value: string): void {
  const element = container.querySelector<ItemsControl>(selector)
  if (!element) {
    return
  }
  element.items = items
  element.value = value
}

function toOption(value: string): JbControlOption {
  return {
    value,
    label: value.charAt(0).toUpperCase() + value.slice(1),
  }
}
