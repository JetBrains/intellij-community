# WebView Control Parity Design

Status: ⬜ **DESIGN ONLY**. No `@jetbrains/intellij-webview-controls` package, no `jb-*` custom elements, no `--jb-*` token CSS file. This doc is the v1 spec when work starts.

## Goal

Provide reusable controls for WebView-based IntelliJ UI that let plugin and platform authors build the same kinds of settings and tool panels that are currently built with Swing controls.

The parity target is the user-facing control surface from `uiDslShowcase`: available controls, important states, validation states, labels, help text, grouping, and common form composition patterns.

The parity target is not the Kotlin UI DSL coding model. The WebView API should feel like a normal modern web component library.

## Non-Goals

- Do not port the Kotlin `panel { row { ... } }` DSL to TypeScript as the primary API.
- Do not expose Swing layout concepts such as `RowLayout`, `Cell`, `RightGap`, or `DialogPanel` in the public WebView API.
- Do not require React, Preact, Vue, Svelte, or another application framework to use the shared controls.
- Do not make global CSS classes the customization contract.

## Public Package Shape

```text
@jetbrains/intellij-webview-controls
  define/all                 registers all custom elements
  elements/button            exports one element class and registration helper
  elements/text-field
  elements/select
  tokens.css                 CSS custom properties for runtime styling
  tokens.json                design-token source for tooling
  custom-elements.json       Custom Elements Manifest for IDEs and docs
```

The package should be consumed with package-style imports, both inside the monorepo and in external plugin projects.

```ts
import "@jetbrains/intellij-webview-controls/define/all"
```

```html
<jb-field label="Project name">
  <jb-text-field value="IntelliJ IDEA"></jb-text-field>
</jb-field>

<jb-button appearance="primary">Apply</jb-button>
```

## Web-Native Component Contract

Controls should be autonomous custom elements implemented with Lit unless plain custom elements are clearly simpler.

Component APIs should follow web platform conventions:

- Primitive configuration is exposed through reflected attributes and properties where practical: `disabled`, `checked`, `value`, `appearance`, `size`.
- Rich configuration is property-only: option arrays, icon descriptors, validation objects, renderer functions.
- User edits emit standard events where possible: `input`, `change`, `click`.
- Custom events are used only for non-standard state transitions, are named in kebab-case, and use `bubbles: true` and `composed: true`.
- Programmatic property assignment does not emit user-change events.
- Slotted content is preferred for labels, icons, prefix/suffix content, and action content when markup composition is natural.
- Shadow DOM is the default encapsulation boundary.
- Stable styling hooks are CSS custom properties and documented `part` names.
- Internal DOM class names are private implementation details.

Form controls should use `ElementInternals` where supported and useful:

- form value participation;
- disabled propagation;
- validity state and validation message;
- accessible labels and descriptions;
- `:state(...)` only after validating browser support in IDE WebView engines.

## Control Inventory

The initial parity set should cover these controls and states.

- `button`, action button: `jb-button`, `jb-action-button`.
- `actionsButton`: `jb-menu-button`.
- `checkBox`: `jb-checkbox`.
- `threeStateCheckBox`: `jb-tristate-checkbox`.
- `radioButton`, `buttonsGroup`: `jb-radio-group`, `jb-radio`.
- `segmentedButton`: `jb-segmented-control`.
- `tabbedPaneHeader`: `jb-tabs`.
- `label`, `text`, `comment`: `jb-label`, `jb-text`, `jb-help-text`.
- `link`, `browserLink`, `dropDownLink`: `jb-link`, `jb-dropdown-link`.
- `icon`, `contextHelp`: `jb-icon`, `jb-context-help`.
- `textField`, `passwordField`: `jb-text-field`, `jb-password-field`.
- `textFieldWithBrowseButton`: `jb-text-field` with an action slot, plus host-side browse action wiring.
- `expandableTextField`: `jb-expandable-text-field`.
- `extendableTextField`: `jb-text-field` with prefix/suffix/action slots.
- `intTextField`: `jb-number-field`.
- `spinner`: `jb-spinner`.
- `slider`: `jb-slider`.
- `textArea`: `jb-text-area`.
- `comboBox`: `jb-select` for simple options, `jb-combobox` for searchable input.
- `separator`: `jb-separator`.
- `group`, `collapsibleGroup`: `jb-field-group`, `jb-disclosure`.

The control names can change during implementation, but the public naming should stay close to established web component naming: noun-based tags, kebab-case, and no Kotlin DSL terminology.

## Composition Patterns

Form composition should use web-native containers instead of a TypeScript clone of UI DSL rows and cells.

```html
<jb-field-group label="Build tools">
  <jb-field label="Gradle JVM">
    <jb-select .options=${jvms} value="temurin-21"></jb-select>
    <jb-help-text slot="help">Used for project import and Gradle tasks.</jb-help-text>
  </jb-field>

  <jb-checkbox checked>Use Gradle from wrapper</jb-checkbox>
</jb-field-group>
```

`jb-field` owns common field behavior:

- label placement;
- required marker;
- help text;
- warning and error presentation;
- connection between label, control, help text, and accessibility metadata.

Layout should be expressed with CSS grid, flexbox, container queries where available, and density tokens. The library may provide field and group containers, but it should not expose Swing row/cell layout primitives.

## State, Validation, and Binding

Validation should use browser concepts first.

- Required, pattern, min, max, and step constraints map to native validity where possible.
- Custom validation sets `ElementInternals.setValidity(...)` and a visible error or warning message.
- Warnings are separate from blocking validity because browser validity has no warning level.
- Error and warning UI is part of `jb-field`, not duplicated in every input control.

Binding should remain application-owned.

- Controls expose values and events.
- Applications, framework adapters, or small optional helpers can bind state.
- The base control library should not own an apply/reset/isModified model like `DialogPanel`.

This keeps the controls usable from vanilla TypeScript, Lit, React, Preact, Vue, Svelte, and server-rendered HTML that hydrates later.

## Theming and Styling

The controls package should define a token layer rather than a visual theme hard-coded into components. Component CSS should define layout, interaction states, density, focus geometry, transitions, and token usage. Actual colors should be provided from outside as CSS custom properties.

The important rule is that component styles describe roles, not concrete IDE colors.

```text
:host {
  color: var(--jb-content-foreground);
  background: var(--jb-content-background);
}

[part="control"] {
  border-color: var(--jb-control-border);
  background: var(--jb-control-background);
}

:host([appearance="primary"]) [part="control"] {
  color: var(--jb-button-primary-foreground);
  background: var(--jb-button-primary-background);
}
```

The WebView host should inject the concrete values for the current IDE theme before application code renders.

```html
<style id="jb-webview-theme-tokens">
  :root {
    color-scheme: dark;
    --jb-content-background: #1e1f22;
    --jb-content-foreground: #bbbbbb;
    --jb-control-background: #3c3f41;
    --jb-control-border: #5e6060;
    --jb-focus-ring-color: #466d94;
    --jb-button-primary-background: #365880;
    --jb-button-primary-foreground: #bbbbbb;
  }
</style>
```

This style element should be regenerated when the IDE Look-and-Feel changes and pushed to already open WebViews through the WebView bridge. A page may still keep `data-theme="light|dark"` for coarse CSS branches, but colors should come from tokens, not from duplicated light/dark palettes in every WebView bundle.

V1 must explicitly support the bundled light and dark theme families used by the current Islands UI direction. The token mapping should cover at least Islands Light, Islands Dark, and Islands Darcula, with component screenshots for both light and dark modes. This is part of the base theme contract, not custom-theme support.

The token source should be derived from the already-applied IntelliJ theme, not by parsing `*.theme.json` in JavaScript. The platform side can read normalized `UIDefaults` and named colors after `UITheme.applyTheme(...)` has resolved parent themes, `colors`, `ui`, OS-specific values, and plugin customizers. The WebView layer then maps IDE keys to stable web tokens.

```text
IDE UITheme / UIDefaults
  -> WebViewThemeTokenProvider
  -> CSS custom properties in the WebView document
  -> Lit component CSS consumes var(--jb-*) tokens
```

The stable contract should be semantic `--jb-*` tokens, not raw Swing keys. Exposing every `UIDefaults` key as a CSS variable would leak Swing implementation details and make WebView controls inherit the wrong API surface. Raw key export can exist behind diagnostics if it helps prototyping, but it should not be the public theme contract.

```css
:root {
  --jb-font-family: system-ui;
  --jb-control-height: 24px;
  --jb-control-border-radius: 4px;
  --jb-color-text: CanvasText;
  --jb-color-background: Canvas;
  --jb-color-accent: Highlight;
}
```

The IDE host or WebView runtime can map IntelliJ theme values onto these tokens. Components consume tokens and expose `part` names for controlled customization.

```text
jb-button::part(control) {
  font-weight: 500;
}
```

The token source should be structured enough to generate `tokens.css`, documentation tables, and possibly platform theme adapters later.

## Custom Theme Plugins

Custom theme plugin support is not a v1 priority. The v1 priority is a robust semantic token pipeline for the current IDE theme and bundled themes, including Islands light/dark variants. Custom themes should work reasonably through the same normalized `UIDefaults` mapping when they reuse standard IDE keys, but dedicated custom-theme authoring features should not block the first control library iteration.

Existing custom themes are plugins that register `com.intellij.themeProvider` and point to a `*.theme.json` resource. That already gives a useful baseline for WebView controls: if a custom theme provides standard Swing/UI keys, the WebView runtime can derive the semantic `--jb-*` tokens from the current normalized `UIDefaults`, and the theme works without any WebView-specific files.

That baseline is the lowest-complexity path and should be implemented first.

```text
theme plugin
  -> themeProvider / *.theme.json
  -> UITheme applies colors into UIDefaults
  -> WebView token mapping reads UIDefaults
  -> WebView controls receive CSS variables
```

Some themes may need WebView-specific tuning because browser controls and Swing controls do not have identical state models. For that, allow optional plugin-packaged CSS, but keep it as token CSS rather than arbitrary component skinning.

Recommended extension shape:

```xml
<extensions defaultExtensionNs="com.intellij">
  <themeProvider id="MyTheme" path="/theme/MyTheme.theme.json"/>
  <webViewThemeProvider themeId="MyTheme" css="/theme/webview/MyTheme.css"/>
</extensions>
```

The CSS file should be loaded only for the matching current theme and should be served as a local plugin resource through the WebView asset layer. It should normally contain `:root` token overrides.

```css
:root {
  --jb-button-primary-background: #325a7d;
  --jb-button-primary-foreground: #ffffff;
  --jb-field-error-border: #c75450;
}
```

The loading order should be deterministic:

1. controls package fallback tokens;
2. generated IDE theme token style;
3. optional theme-plugin WebView CSS;
4. application CSS.

This lets custom themes refine platform-generated tokens while still allowing a specific WebView application to scope local overrides inside its own root.

Adding a `webview` section directly to `*.theme.json` is possible later, but it is a larger compatibility and tooling change: the theme parser, DevKit inspections, JSON schema, dynamic plugin loading, and Marketplace guidance would all need to understand the new section. A separate WebView theme extension point is less invasive and can be added without changing existing theme JSON semantics.

Complexity estimate:

- Automatic support through normalized `UIDefaults`: low to medium. The hard part is choosing and testing the semantic token mapping, not plugin loading.
- Optional plugin-packaged WebView CSS: medium. It needs a new extension point, plugin resource serving, dynamic theme/plugin reload handling, and CSS validation rules.
- Full custom theme authoring support in `*.theme.json`: high. It requires schema/parser/tooling updates and a stronger compatibility promise.

The first version should support automatic token extraction for all themes and define the optional CSS extension point as the planned escape hatch.

## Accessibility Requirements

The implementation must follow WAI-ARIA Authoring Practices for composite controls and browser-native semantics for simple controls.

Required behavior includes:

- keyboard support for radio groups, segmented controls, tabs, disclosure, menus, select, and combobox;
- visible focus indication;
- correct accessible name and description calculation;
- `aria-invalid` and error-message linkage for invalid fields;
- no focus traps in popups;
- disabled and readonly behavior matching web platform expectations.

## Showcase

Add a WebView controls showcase under the WebView demo module. It should mirror the Swing showcase as an inventory, but examples should use canonical web markup and TypeScript.

Suggested sections:

- Components;
- Labels and help text;
- Validation;
- Enabled, disabled, readonly, and hidden states;
- Groups and disclosure;
- Tabs and segmented controls;
- Spacing, density, and responsive layout;
- Light and dark theme rendering.

The showcase is an acceptance tool for parity. It is not the primary API design source.

## Testing

Use browser-level tests for component behavior.

- `tsc -b` for typechecking and declaration output.
- Web component tests in real browsers for attributes, properties, events, slots, parts, form association, and validation.
- Playwright tests for keyboard behavior, focus order, accessibility states, and showcase smoke coverage.
- Visual snapshots for light, dark, focused, hovered, pressed, disabled, invalid, warning, selected, and expanded states.
- One consumer fixture that imports the package through `@jetbrains/intellij-webview-controls` and builds to static WebView assets.

## References

- Lit documentation: https://lit.dev/docs/v3/
- Lit browser and tooling requirements: https://lit.dev/docs/tools/requirements/
- MDN Web Components: https://developer.mozilla.org/en-US/docs/Web/API/Web_components
- MDN Custom Elements: https://developer.mozilla.org/en-US/docs/Web/API/Web_components/Using_custom_elements
- MDN ElementInternals: https://developer.mozilla.org/en-US/docs/Web/API/ElementInternals
- MDN CSS shadow parts: https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_shadow_parts
- WAI-ARIA Authoring Practices Guide: https://www.w3.org/WAI/ARIA/apg/
- Design Tokens Format Module: https://www.w3.org/community/reports/design-tokens/CG-FINAL-format-20251028/
- Custom Elements Manifest: https://github.com/webcomponents/custom-elements-manifest
