# WebView React Base UI Controls Plan

Status: ⬜ **DESIGN ONLY**. No `@jetbrains/intellij-webview-react-controls` package exists yet. The existing `@jetbrains/intellij-webview-controls` Web Component package remains the framework-neutral shared control layer.

## Problem

Complex React WebView screens need desktop-like composite controls: select, combobox, menu, popover, tooltip, dialog, searchable model pickers, and command surfaces. Reimplementing these per view is risky because the hard parts are not visual styling; they are focus management, keyboard navigation, pointer behavior, portal layering, typeahead/search, accessibility roles, and WebView focus-leave interop.

The ACP chat regressions around combo popups, focus transfer, black browser outlines, and selectable control chrome are examples of bugs that should be solved once in a React control layer instead of rediscovered in every feature view.

## Decision

Create an optional React package on top of Base UI primitives, scoped to the **composite/portal controls** that the framework-neutral Web Components cannot do well:

```text
@jetbrains/intellij-webview-react-controls
  Select        (Base UI Select)
  Combobox      (Base UI Combobox / Autocomplete) — the primary motivation
  Menu          (Base UI Menu)
  Popover       (Base UI Popover)
  Tooltip       (Base UI Tooltip)
  Dialog        (Base UI Dialog) — later, gated on Robot coverage
  FocusRing / ControlChrome helpers
```

Simple form controls (button, checkbox, radio, switch, slider, tabs, separator, field, label, help text, text/number/password fields, etc.) already exist as framework-neutral Lit `jb-*` elements in `@jetbrains/intellij-webview-controls` (`webview-src/packages/controls/src/elements/`, 27 elements today). React views should render those custom elements directly rather than reimplement them here. This package adds only the controls where Base UI's focus management, portaling, typeahead, and collision handling earn their keep.

The package is for React WebView applications only. It must not replace `@jetbrains/intellij-webview-controls`, which stays Lit/Web Components and framework-neutral.

Base UI is the first foundation because it is unstyled, accessibility-focused, and provides a native primitive family (Select, Combobox, Autocomplete, Menu, Popover, Tooltip, Dialog) with one engine for both select-like and searchable combo-like controls. The decisive gap versus the current ACP dependency, Radix, is the **searchable combobox/autocomplete**: Radix has no such primitive, Base UI does. Keep Radix only as a fallback if a Base UI spike exposes a WebView-specific blocker; do not mix Base UI and Radix primitives without a concrete, tested gap.

## Goals

- Provide reusable React controls that look and behave like IntelliJ desktop controls inside WebView pages.
- Centralize Base UI integration, portal setup, focus rings, keyboard behavior, pointer behavior, and WebView focus-leave handling.
- Make the correct control structure hard to get wrong: wrapper chrome owns the border and focus ring, inner Base UI trigger/control part owns ARIA and events.
- Preserve copyability of real content while making control chrome non-selectable.
- Keep the public runtime and framework-neutral Web Components unaffected.
- Make the package testable in the existing browser mock preview harness and in IDE Robot tests for WebView focus interop.

## Non-Goals

- Do not migrate existing `jb-*` Web Components to React.
- Do not reimplement simple form controls that already exist as Lit `jb-*` elements; React views consume those directly.
- Do not require React for plugin authors who only need the framework-neutral WebView runtime or Web Components.
- Do not wrap every Base UI primitive immediately.
- Do not introduce a second visual design language, theme, or token system.
- Do not expose raw Base UI class names or data attributes as the stable styling contract.

## Package Shape

```text
webview-src/packages/react-controls/
  package.json
  src/
    index.ts
    select/
    combobox/
    popover/
    menu/
    tooltip/
    dialog/
    chrome/
    styles.css
    test-utils/
```

Initial dependencies:

```json
{
  "peerDependencies": {
    "react": ">=18",
    "react-dom": ">=18"
  },
  "dependencies": {
    "@base-ui/react": "<pinned>"
  }
}
```

Pin Base UI versions in the repository lockfiles. External SDK distribution should follow the same versioning policy as the other WebView TypeScript packages.

Before pinning, confirm the exact Base UI package coordinate and its React requirement: it is published today as `@base-ui/react` (1.x), but shipped pre-1.0 as `@base-ui-components/react`, and a 1.x release may require a newer React than the `>=18` peer range above. Verify against ACP's current React version (18.3.1) before committing the dependency.

## Component Contract

### Control Chrome

Composite controls must render a single outer chrome element that contains icons, labels, value text, indicators, and the inner Base UI trigger/control part.

```tsx
<JbControlChrome icon={<ModelIcon />} invalid={invalid} disabled={disabled}>
  <Select.Trigger className="jbReactSelectTrigger" aria-label={label}>
    <Select.Value />
    <ChevronIcon aria-hidden="true" />
  </Select.Trigger>
</JbControlChrome>
```

The outer chrome owns:

- border;
- border radius;
- background;
- hover state;
- focus ring;
- disabled opacity;
- compact/regular density;
- `user-select: none`.

The inner trigger owns:

- Base UI state attributes;
- accessible name;
- keyboard/pointer event integration;
- `outline: none`;
- transparent layout inside the chrome.

The focus ring must surround the whole combo, including icons, not only the text or inner trigger.

### Focus Ring

Use the IntelliJ token-backed ring, not browser-default black outlines.

```css
.jbReactControlChrome:focus-within {
  border-color: var(--jb-accent-color);
  box-shadow: var(--jb-focus-ring);
}

.jbReactControlChrome :where(button, [role="combobox"]):focus {
  outline: none;
}
```

This keeps keyboard focus visible while preventing the browser from drawing an extra inner outline.

### Selection Behavior

The package should make desktop-like control chrome non-selectable by default:

```css
.jbReactControlChrome,
.jbReactControlChrome * {
  user-select: none;
}
```

Controls that contain editable text must opt the editable element back into selection:

```css
.jbReactTextInput,
.jbReactComboboxInput {
  user-select: text;
}
```

Application content remains the application's responsibility. For example, ACP chat history should stay selectable at the page level; only controls inside it should opt out.

## Swing/UI DSL Inventory Match

Use the Swing/UI DSL parity inventory from [WebView Control Parity Design](WebView-Control-Parity-Design.md#control-inventory) as the baseline, but split it by *who owns the control*. Most light form controls are already covered by the framework-neutral Lit `jb-*` elements; the React package takes only the composite/portal controls.

### React Package Owns (Composite / Portal)

These are the controls the React package should provide, because Base UI supplies the hard interaction primitives (portal, focus, typeahead, collision) that the native element or the current Lit control lacks.

| Swing/UI DSL surface | React control | Base UI foundation | Why it belongs here |
| --- | --- | --- | --- |
| `comboBox` with simple options | `JbSelect` | Base UI Select | Consistent styled popup/focus behavior; validation vehicle for the chrome + focus-ring + focus-leave pattern. Plain native `<select>` (Lit `jb-select`) is already robust, so this is mostly a styling win. |
| searchable model/config chooser | `JbCombobox` / `JbAutocomplete` | Base UI Combobox / Autocomplete | **Primary motivation.** ACP's model picker needs it, native `<datalist>` is too limited, and Radix has no equivalent primitive. |
| `actionsButton`, `dropDownLink` | `JbMenuButton` | Base UI Menu | Portal/focus behavior can be standardized instead of the current inline `position: absolute` popup. |
| `contextHelp`, small info popups | `JbTooltip`, `JbPopover` | Base UI Tooltip / Popover | Centralizes portal root and focus-leave close behavior for richer controls. |
| dialogs (later) | `JbDialog` | Base UI Dialog | Only after WebView focus-trap Robot coverage (see Not First-Wave). |

### Consume The Existing Lit `jb-*` Element Instead

These already exist as framework-neutral Web Components (`webview-src/packages/controls/src/elements/`). React views should render the custom element directly (optionally behind a thin typed wrapper); do not reimplement them over Base UI in the first package.

| Swing/UI DSL surface | Existing element |
| --- | --- |
| `button`, action button | `jb-button`, `jb-action-button` |
| `checkBox`, `threeStateCheckBox` | `jb-checkbox` (indeterminate supported) |
| `radioButton`, `buttonsGroup` | `jb-radio`, `jb-radio-group` |
| boolean config toggle | add a `jb-switch` to the Lit package if needed |
| `textField`, `passwordField`, `textArea` | `jb-text-field`, `jb-password-field`, `jb-text-area` |
| `intTextField`, `spinner` | `jb-number-field`, `jb-spinner` |
| `slider` | `jb-slider` |
| `segmentedButton` | `jb-segmented-control` |
| `tabbedPaneHeader` | `jb-tabs` |
| `separator` | `jb-separator` |
| `label`, `text`, `comment`, help text | `jb-field`, `jb-label`, `jb-help-text`, `jb-text` |

### Not First-Wave

These controls should stay out of the first package unless a product view has a concrete need and tests are ready.

| Swing/UI DSL surface | Reason to defer |
| --- | --- |
| `textFieldWithBrowseButton` | Needs host file chooser/project model wiring; the browse action is not just a frontend primitive. |
| `expandableTextField` | Needs popup/editor semantics and text-selection review; can be built after Popover/TextField are stable. |
| `extendableTextField` | Prefix/suffix slots are easy, but action ordering and keyboard behavior need a shared field anatomy first. |
| `EditorComboBox` / editor-backed combo controls | Tied to editor infrastructure, syntax highlighting, and host-side models. |
| multi-select combo/list renderers | Requires checkbox listbox semantics, selection announcements, and more visual states. |
| tree/table/list controls | Too large for a headless primitive wrapper; needs separate virtualized data-view design. |
| dialogs/wizards | High focus-trap risk at the Swing/WebView boundary; require Robot coverage before adoption. |
| validation framework / dependent visibility DSL | App-level form state problem, not only a control primitive. |

The first real customer is ACP chat, which is already React + `radix-ui`; that is where the searchable combobox and menu popups are needed and where the Base UI focus path gets proven. This package is not a reason to adopt React in a small settings panel that could use the neutral `jb-*` controls instead — see [WebView Frontend Framework Policy](WebView-Frontend-Framework-Policy.md).

## IntelliJ Theme Integration

Base UI is unstyled, so IntelliJ theming is not optional. The WebView runtime **already ships** the token bridge this package needs, so the first stage consumes it rather than building a new one.

### Theme Source

The common runtime injects a semantic `--jb-*` token layer derived from the active IntelliJ Look and Feel / `UIDefaults`, with browser fallbacks, and keeps it reactive to theme changes. See `installIJTheming` / `ensureJBThemeTokensInstalled` in `webview-src/packages/impl/src/theme.ts` and the token definitions in `webview-src/packages/controls/src/tokens.ts`. React controls consume the `--jb-*` layer; they must not import ACP-specific colors, hard-code Darcula/IntelliJ Light values, or consume the raw `--ij-*` source variables directly.

```text
IDE Look and Feel / UIDefaults
  -> raw --ij-* source variables (host-injected)
  -> --jb-* semantic token layer (installIJTheming / ensureJBThemeTokensInstalled, with fallbacks)
  -> @jetbrains/intellij-webview-controls (Lit) and @jetbrains/intellij-webview-react-controls CSS
  -> application CSS overrides
```

### Required Token Groups

These `--jb-*` tokens already exist in `tokens.ts` and are enough to render controls without per-view color hacks. React controls consume these names directly:

```css
--jb-font-family
--jb-font-size
--jb-line-height
--jb-control-height
--jb-control-height-compact
--jb-control-radius
--jb-control-padding-x

--jb-bg-window
--jb-bg-panel
--jb-bg-control
--jb-bg-input
--jb-bg-hover
--jb-bg-pressed
--jb-bg-selected
--jb-bg-selected-muted

--jb-text-color
--jb-text-secondary
--jb-text-muted
--jb-text-disabled
--jb-text-on-accent

--jb-border-color
--jb-border-color-strong
--jb-border-color-muted
--jb-accent-color
--jb-focus-ring
--jb-danger-color
--jb-warning-color

--jb-popup-shadow
```

If a control needs a semantic token that does not exist yet (for example a success color or a non-popup control shadow), add it to the `--jb-*` layer in `tokens.ts` (and the matching bridge in `theme.ts`) as `var(--ij-*, <fallback>)`, so the Lit and React controls share one contract. Do not introduce a parallel React-only `--ij-*` or `--acp-*` name for a color the `--jb-*` layer already expresses.

### Runtime Behavior

- The `--jb-*` layer, its browser fallbacks, and live theme-change updates are already provided by the common runtime (`installIJTheming`); the package relies on that and does not inject its own base tokens.
- Add a browser test fixture with light and dark token sets (the preview harness can drive the `data-theme` attribute the runtime sets).
- Add at least one IDE-side smoke/Robot check that a focused select uses the current IJ focus color and popup colors.
- Keep application-level overrides scoped under the application root, not global `:root`, unless the view intentionally owns the whole page.

### Acceptance Criteria For Theming

- A React control renders correctly in IntelliJ Light, Darcula, and a custom theme that maps standard Swing/UI keys.
- Focus ring, hover, pressed, selected, disabled, invalid, and popup states use the shared `--jb-*` variables.
- No control in the package depends on ACP CSS variables such as `--acp-*`, and none consumes the raw `--ij-*` source variables directly.
- Browser previews can run without IDE by using the runtime's deterministic fallback tokens.

## Portal And WebView Focus Interop

Base UI portals are useful, but the package must own their defaults:

- render popups into a stable WebView-local portal root when one is available;
- keep popup z-indexes reconciled with the existing Lit popups, which render inline at `z-index: 10` (see `menu-button.ts`); pick a portal z-index above that and below the WebView's native overlay strategy;
- close dismissable popups on the internal `wvi-focus-leave` event;
- avoid closing on the initial mouse activation path that transfers focus from Swing to the WebView;
- keep keyboard traversal behavior distinct from pointer activation.

The shared focus-leave primitive already exists: `addWebViewFocusLeaveListener()` in `webview-src/packages/api/src/focus.ts` (and the Lit `WebViewFocusLeaveController`). The React package should expose a thin `useWebViewFocusLeave()` hook over that helper rather than have each feature call `window.addEventListener("wvi-focus-leave", ...)` by hand. On a page that mixes `jb-*` and React controls, ensure popups do not register duplicate focus-leave subscriptions.

## Initial Components

### Select

Wrap Base UI Select.

Required features:

- trigger with optional icon and value text;
- portal content;
- scroll buttons;
- item indicator;
- groups and separators;
- disabled items;
- compact density;
- focus ring on full chrome;
- Playwright coverage for mouse click, keyboard open, typeahead, focus leave, and non-selectable chrome.

### Menu

Wrap Base UI Menu.

Required features:

- action items;
- checkbox/radio items;
- separators;
- icons and shortcuts;
- disabled items;
- submenu support only after a dedicated focus test.

### Popover And Tooltip

Wrap Base UI popover/tooltip for low-level surfaces used by richer controls.

Required features:

- consistent portal root;
- collision settings suitable for embedded WebViews;
- focus-leave close behavior;
- no text selection on trigger chrome;
- selectable content only when explicitly requested.

### Dialog

Wrap Base UI Dialog only after testing WebView focus trapping against Swing focus transfer.

Dialog is higher risk than select/menu because it can trap focus. It should not be part of the first landing commit unless the Robot coverage is ready.

### Combobox And Autocomplete

Wrap Base UI Combobox and/or Autocomplete after a dedicated spike.

- Use Combobox for constrained value selection with typed filtering.
- Use Autocomplete when the input text is the primary value and suggestions assist entry.
- Verify whether Base UI's popup, input, and listbox parts can share the same IntelliJ wrapper chrome and focus-ring contract as Select.

Do not ship a hand-rolled combobox unless the Base UI spike finds a blocker and the replacement has browser tests, WebView Robot tests, and accessibility review.

## Styling And Tokens

The package consumes the shared `--jb-*` token layer described in [IntelliJ Theme Integration](#intellij-theme-integration):

```css
--jb-bg-panel
--jb-bg-input
--jb-bg-hover
--jb-bg-pressed
--jb-border-color
--jb-accent-color
--jb-focus-ring
--jb-text-color
--jb-text-muted
```

If a React control needs a token the `--jb-*` layer does not expose yet, add it there first (see `tokens.ts` / `theme.ts`). Do not hard-code ACP-specific colors or consume raw `--ij-*` source variables in the package.

## Testing Requirements

Browser tests:

- click opens popup and it stays open after two animation frames;
- Enter/Space opens popup from keyboard;
- Escape closes popup and restores focus to the trigger;
- click outside closes popup;
- `wvi-focus-leave` closes popup;
- initial pointer activation from outside the WebView does not open-and-immediately-close;
- focus ring is on wrapper chrome including icon;
- inner trigger outline is `none`;
- control chrome is non-selectable;
- editable inputs stay selectable.

Robot tests:

- external Swing control focused, one Robot click on WebView select opens the popup;
- Swing focus moves to the underlying WebView host panel;
- popup remains open after host/native focus activation;
- click back to Swing closes the popup and returns Swing focus;
- test runs on Windows WebView2 at minimum, with macOS coverage added when a WKWebView host path exists.

Visual tests:

- light/dark;
- focused/unfocused;
- hover/pressed;
- disabled;
- invalid/warning if supported;
- long labels and narrow layouts.

## Rollout Plan

### Stage 0: Spike

- Verify the existing `--jb-*` token bridge (`installIJTheming` / `tokens.ts`) covers the new controls; add any missing semantic tokens to the `--jb-*` layer. The bridge and browser fallbacks already ship, so no new token system is built here.
- Build a private `react-controls` package with `Select`, `Combobox`, `Popover`, and `Menu` only.
- Port one ACP config select to the package behind a narrow local import.
- Port one ACP searchable model selector to the package only if the Base UI Combobox/Autocomplete spike passes WebView focus tests.
- Validate WebView2 mouse activation, focus-leave close, keyboard open/close, and wrapper focus ring.
- Measure generated bundle impact in ACP chat.

### Stage 1: Package Scaffold

- Add `webview-src/packages/react-controls`.
- Add package exports, TypeScript build, and CSS entry.
- Add browser tests under the package or demo harness.
- Add a demo view section using the React controls.

### Stage 2: ACP Adoption

- Replace ACP's local `radix-ui` Select/Popover model-picker popups with package controls where behavior matches. This is a migration of working code, so treat it as regression-risk work and keep the existing behavior as the baseline.
- Keep ACP-specific layout and icons in ACP; only generic chrome/popup behavior moves to the package.
- Preserve existing ACP Playwright tests and add package-level tests for behavior moved out.

### Stage 3: SDK Distribution

- Add the package to frontend SDK distribution plans.
- Version it with the IntelliJ Platform SDK alongside `@jetbrains/intellij-webview` and `@jetbrains/intellij-webview-controls`.
- Document that it is optional and React-only.

## Risks

| Risk | Mitigation |
| --- | --- |
| React package undermines framework-neutral policy | Keep it optional and separate from Web Components. Do not use it in non-React docs as the default path. |
| Base UI portal/focus behavior conflicts with embedded WebView focus transfer | Centralize portal and focus-leave helpers; require Robot tests before adopting in ACP. |
| Different primitive libraries leak into one design system | Start with Base UI only. Keep Radix as a fallback for a specific blocker, not a parallel default. |
| Package becomes ACP-specific | Require a controls showcase and package-level tests independent of ACP. |
| Browser outlines disappear without replacement | Enforce wrapper focus-ring tests. |
| Combobox remains underspecified | Treat Base UI Combobox/Autocomplete as a Stage 0 spike with explicit WebView focus tests. |
| React controls drift from Swing theming | Consume the shared `--jb-*` bridge (already shipped); block package exposure until light/dark/custom-theme smoke tests pass. |

## Acceptance Criteria

- A React WebView can import a `JbSelect` from the package and get desktop-like focus, keyboard, pointer, popup, and non-selectable chrome behavior without local Base UI wiring.
- The same select passes browser tests in Chromium and Edge.
- Windows WebView2 Robot coverage proves one-click open from an external Swing focus owner.
- Focus ring surrounds the full control chrome, including icons.
- Theming comes from the active IntelliJ theme through the shared `--jb-*` WebView tokens.
- The package does not change or replace `@jetbrains/intellij-webview-controls`.
- The package has documented distribution/versioning rules before it is exposed outside the monorepo.

## Related Documents

- [WebView Frontend Framework Policy](WebView-Frontend-Framework-Policy.md)
- [WebView Control Parity Design](WebView-Control-Parity-Design.md)
- [WebView Frontend Testability Without IDE](WebView-Frontend-Testability.md)
- [WebView Frontend SDK Distribution](WebView-Frontend-SDK-Distribution.md)
- [WebView Focus & Tab Interop Plan](../interop/WebView-Focus-Tab-Interop-Plan.md)
- Accessibility traversal across the Swing ↔ WebView boundary is the deferred Focus Interop v2 a11y pass — see the roadmap in [directory.md](../directory.md).

## References

- Base UI quick start: https://base-ui.com/react/overview/quick-start
- Base UI components: https://base-ui.com/react/components
