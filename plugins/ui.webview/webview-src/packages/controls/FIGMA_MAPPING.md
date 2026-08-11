# WebView Controls Figma Mapping

Source Figma file: `Int UI Kit: Islands` (`zKwabe7qCf1c0LFu93997q`, node `6222:74502`). The selected node is the file cover; controls are resolved from the subscribed `Int UI Kit: Islands` design library.

Library key: `lk-b9deb84885ea88e6a5da782bc75f38162be6a7e9af8bc413a6ce9caf9144935b179ee0fb921881aca3a0c28d7736928c62da7756a0a096124ad382edf6b68cf6`.

| Figma component | Component key | Web Component | Notes |
| --- | --- | --- | --- |
| Button | `a6f1c269d5ecc5328156c1158eff97f8a6ad3cdf` | `jb-button` | JButton, including default/primary/danger/link-like variants. |
| Toolbar / Icon Button | `220289cb18de86b463905b2b183b02d030c02fa9` | `jb-action-button` | actionButton and actionsButton. |
| Slim Button | `ffa948afd3fe99d84cbd17647bb11adae5a0de31` | `jb-button size="small"` | ActionToolbar.smallVariant equivalent. |
| Link | `a3b3488b56c429e49791c7585b56e8c9da575f1e` | `jb-button variant="link"`, `jb-dropdown-link` | Covers link, dropDownLink, browserLink styling. |
| Checkbox | `48c12f316aae08b31158a0728b7d338b6b44c935` | `jb-checkbox` | checked, indeterminate, disabled, readonly. |
| Radio Button | `cfe3daf7ed6e6acdca96322645f064eb0ab8ae83` | `jb-radio` | Single radio primitive. |
| Radio Buttons Group | `ea762498dd6bfe9a7cd45188f3a1128caa9e93c2` | `jb-radio-group` | Single selection group with `items` property. |
| Labelled Input | `3d76032b8ecdd7d89f8362701dd94a9542081d4a` | `jb-field`, `jb-label`, `jb-help-text` | Anatomy wrapper for labels, help text, warning, and validation text. |
| Input Field | `481b0a421c9a5bd0cec576fd4bcb987d318cd1cb` | `jb-text-field`, `jb-password-field`, `jb-number-field` | JTextField/JBPasswordField style. |
| Text Area | `bf21b0995ec360e9afe310cf87b98cd4e7e1081d` | `jb-text-area` | JBTextArea style. |
| Dropdown | `b58e8f5b45b2f60f6d7d426978dfba7207b1942a` | `jb-select` | ComboBox/drop-down selection. |
| Combobox | `712603e87b1d207f1115e247f7d015d91c32e4a7` | `jb-combobox` | Editable ComboBox. |
| Toolbar / Dropdown | `c09c7c60d2396b92f9d4a3a36578c10ae4f96e09` | `jb-menu-button` | Toolbar filter/dropdown. |
| Popup / Popup with Checkmarks | `74ddf652d98e44b9bc81b2c6e7950ae4ccf75655` | `jb-menu-button` menu surface | Context-menu-like popup surface. |
| Segmented Control | `c470d2d281c9168ea7a301df1993dfeb96e2c982` | `jb-segmented-control` | segmentedButton style. |
| Toggle | `b2dadab21a925420cdaa5d05a37fc70528c1c057` | future `jb-toggle` | Tracked in inventory; not exposed in batch API yet. |

The MCP session could not import component sets because the Figma file was exposed in read-only mode, so per-variant inspection is captured through library component metadata plus the IntelliJ UI Guidelines state model: focus, hover, pressed, selected, disabled, readonly, validation error/warning, expanded, and light/dark theme rendering.
