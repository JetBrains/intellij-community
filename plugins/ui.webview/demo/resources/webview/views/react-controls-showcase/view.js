import { n as __toESM } from "./assets/rolldown-runtime.js";
import { i, n as A, r as b, t as i$1 } from "./assets/lit.js";
import { A as Separator, B as MenuItem, C as PopoverDescription$1, D as PopoverPortal, E as PopoverPositioner, F as MenuRadioItem, G as require_jsx_runtime, H as MenuGroup$1, I as MenuRadioGroup$1, L as MenuPositioner, M as MenuTrigger, N as MenuRoot$1, O as PopoverTrigger, P as MenuRadioItemIndicator, R as MenuPortal, S as PopoverClose$1, T as PopoverPopup, U as MenuCheckboxItemIndicator, V as MenuGroupLabel$1, W as MenuCheckboxItem, _ as SelectPortal, a as TooltipTrigger, b as SelectTrigger, c as SelectGroup$1, d as SelectItemText, f as SelectItemIndicator, g as SelectPositioner, h as SelectPopup, i as TooltipPortal, j as MenuViewport, k as PopoverRoot$1, l as SelectScrollUpArrow, m as SelectList, n as TooltipPopup, o as TooltipRoot$1, p as SelectItem, q as require_react, r as TooltipPositioner, s as SelectGroupLabel$1, t as TooltipProvider$1, u as SelectScrollDownArrow, v as SelectIcon, w as PopoverTitle$1, x as SelectRoot$1, y as SelectValue$1, z as MenuPopup } from "./assets/base-ui-react.js";
import { t as require_client } from "./assets/react-dom.js";
(function polyfill() {
	const relList = document.createElement("link").relList;
	if (relList && relList.supports && relList.supports("modulepreload")) return;
	for (const link of document.querySelectorAll("link[rel=\"modulepreload\"]")) processPreload(link);
	new MutationObserver((mutations) => {
		for (const mutation of mutations) {
			if (mutation.type !== "childList") continue;
			for (const node of mutation.addedNodes) if (node.tagName === "LINK" && node.rel === "modulepreload") processPreload(node);
		}
	}).observe(document, {
		childList: true,
		subtree: true
	});
	function getFetchOpts(link) {
		const fetchOpts = {};
		if (link.integrity) fetchOpts.integrity = link.integrity;
		if (link.referrerPolicy) fetchOpts.referrerPolicy = link.referrerPolicy;
		if (link.crossOrigin === "use-credentials") fetchOpts.credentials = "include";
		else if (link.crossOrigin === "anonymous") fetchOpts.credentials = "omit";
		else fetchOpts.credentials = "same-origin";
		return fetchOpts;
	}
	function processPreload(link) {
		if (link.ep) return;
		link.ep = true;
		const fetchOpts = getFetchOpts(link);
		fetch(link.href, fetchOpts);
	}
})();
function boolAttribute(value) {
	return value ? "true" : A;
}
function defineControl(tagName, constructor, registry = customElements) {
	if (!registry.get(tagName)) registry.define(tagName, constructor);
}
var hostStyles = i`
  :host {
    box-sizing: border-box;
    color: var(--jb-text-color);
    font-family: var(--jb-font-family);
    font-size: var(--jb-font-size);
    line-height: var(--jb-line-height);
    -webkit-user-select: none;
    user-select: none;
  }

  :host([hidden]) {
    display: none !important;
  }

  *,
  *::before,
  *::after {
    box-sizing: inherit;
  }

  button,
  input,
  select,
  textarea {
    font: inherit;
  }

  [disabled],
  :host([disabled]) {
    cursor: default;
  }
`;
var buttonStyles = i`
  .button {
    appearance: none;
    align-items: center;
    background: var(--jb-bg-control);
    border: 1px solid var(--jb-border-color);
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    cursor: default;
    display: inline-flex;
    gap: var(--jb-control-gap);
    justify-content: center;
    line-height: var(--jb-line-height);
    min-height: var(--jb-control-height);
    min-width: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    position: relative;
    -webkit-user-select: none;
    user-select: none;
    white-space: nowrap;
  }

  .button:hover:not(:disabled) {
    background: var(--jb-bg-hover);
  }

  .button:active:not(:disabled),
  .button[data-pressed="true"] {
    background: var(--jb-bg-pressed);
  }

  .button:focus-visible {
    box-shadow: var(--jb-focus-ring);
  }

  .button:disabled {
    border-color: var(--jb-border-color-muted);
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .button.primary {
    background: var(--jb-accent-color);
    border-color: var(--jb-accent-color);
    color: var(--jb-text-on-accent);
  }

  .button.primary:hover:not(:disabled) {
    background: var(--jb-accent-hover-color);
    border-color: var(--jb-accent-hover-color);
  }

  .button.danger {
    color: var(--jb-danger-color);
  }

  .button.link {
    background: transparent;
    border-color: transparent;
    color: var(--jb-accent-text-color);
    min-height: var(--jb-control-height-compact);
    min-width: 0;
    padding: 0;
  }

  .button.link:hover:not(:disabled) {
    background: transparent;
    color: var(--jb-accent-hover-color);
    text-decoration: underline;
  }

  .button.toolbar,
  .button.icon {
    background: transparent;
    border-color: transparent;
    height: var(--jb-control-height-compact);
    min-height: var(--jb-control-height-compact);
    min-width: var(--jb-control-height-compact);
    padding: 0 var(--jb-space-xs);
  }

  .button.icon {
    width: var(--jb-control-height-compact);
  }

  .button.selected,
  .button[aria-pressed="true"] {
    background: var(--jb-bg-selected-muted);
    border-color: var(--jb-accent-soft-bg);
    color: var(--jb-text-color);
  }

  .button.small {
    min-height: var(--jb-control-height-compact);
    padding-inline: var(--jb-space-sm);
  }

  .button [part="label"] {
    align-items: center;
    display: inline-flex;
    justify-content: center;
    line-height: var(--jb-line-height);
    min-height: var(--jb-line-height);
  }

  .button .icon-slot.empty {
    display: none;
  }

  .button-icon {
    color: currentColor;
    display: inline-flex;
    flex: 0 0 auto;
    height: 12px;
    line-height: 1;
    position: relative;
    width: 12px;
  }

  .button-icon::before,
  .button-icon::after {
    background: currentColor;
    border-radius: 1px;
    content: "";
    height: 1.5px;
    left: 50%;
    position: absolute;
    top: 50%;
    transform: translate(-50%, -50%);
    width: 8px;
  }

  .button-icon.plus::after {
    transform: translate(-50%, -50%) rotate(90deg);
  }

  .button-icon.minus::after {
    display: none;
  }

  .icon-slot,
  .chevron {
    align-items: center;
    display: inline-flex;
    flex: 0 0 auto;
    height: 12px;
    justify-content: center;
    line-height: 1;
    position: relative;
    width: 12px;
  }

  .chevron {
    color: var(--jb-text-muted);
  }

  .chevron::before {
    border: solid currentColor;
    border-width: 0 1.5px 1.5px 0;
    content: "";
    height: 5px;
    margin-top: -3px;
    transform: rotate(45deg);
    width: 5px;
  }

  .chevron.right::before {
    margin-left: -3px;
    margin-top: 0;
    transform: rotate(-45deg);
  }
`;
var inputStyles = i`
  .field-control,
  .textarea,
  .select {
    appearance: none;
    background: var(--jb-bg-input);
    border: 1px solid var(--jb-border-color);
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    min-height: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    width: 100%;
  }

  .field-control:hover:not(:disabled):not([readonly]),
  .textarea:hover:not(:disabled):not([readonly]),
  .select:hover:not(:disabled) {
    border-color: var(--jb-border-color-strong);
  }

  .field-control:focus,
  .textarea:focus,
  .select:focus {
    border-color: var(--jb-accent-color);
    box-shadow: var(--jb-focus-ring);
    outline: none;
  }

  .field-control:disabled,
  .textarea:disabled,
  .select:disabled {
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .field-control[aria-invalid="true"],
  .textarea[aria-invalid="true"],
  .select[aria-invalid="true"] {
    border-color: var(--jb-danger-color);
  }

  .field-control::placeholder,
  .textarea::placeholder {
    color: var(--jb-text-secondary);
  }

  .textarea {
    line-height: var(--jb-line-height-paragraph);
    min-height: 72px;
    padding-block: var(--jb-space-xs);
    resize: vertical;
  }

  .select-wrap,
  .combo-wrap {
    position: relative;
  }

  .select {
    padding-right: 26px;
    -webkit-user-select: none;
    user-select: none;
  }

  .field-control,
  .textarea {
    -webkit-user-select: text;
    user-select: text;
  }

  .select-wrap::after {
    border: solid currentColor;
    border-width: 0 1.5px 1.5px 0;
    color: var(--jb-text-muted);
    content: "";
    height: 5px;
    pointer-events: none;
    position: absolute;
    right: 9px;
    top: 50%;
    transform: translateY(-65%) rotate(45deg);
    -webkit-user-select: none;
    user-select: none;
    width: 5px;
  }
`;
var popupStyles = i`
  .popup {
    background: var(--jb-bg-panel);
    border: 1px solid var(--jb-border-color-muted);
    border-radius: var(--jb-control-radius);
    box-shadow: var(--jb-popup-shadow);
    display: grid;
    gap: 1px;
    margin-top: var(--jb-space-xs);
    min-width: 160px;
    padding: var(--jb-space-xs);
    position: absolute;
    z-index: 10;
  }

  .menu-root {
    display: inline-block;
    position: relative;
  }

  .menu-item {
    appearance: none;
    background: transparent;
    border: 0;
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    min-height: var(--jb-control-height-compact);
    padding: 0 var(--jb-space-sm);
    text-align: left;
    -webkit-user-select: none;
    user-select: none;
    white-space: nowrap;
  }

  .menu-item:hover:not(:disabled),
  .menu-item:focus-visible {
    background: var(--jb-bg-hover);
    outline: none;
  }

  .menu-item:disabled {
    color: var(--jb-text-disabled);
  }
`;
var choiceStyles = i`
  :host {
    display: inline-flex;
    vertical-align: middle;
  }

  .choice {
    align-items: flex-start;
    color: var(--jb-text-color);
    display: inline-flex;
    gap: var(--jb-control-gap);
    min-height: var(--jb-control-height-compact);
    position: relative;
    -webkit-user-select: none;
    user-select: none;
  }

  .native-check {
    height: 1px;
    left: 8px;
    opacity: 0;
    position: absolute;
    top: 8px;
    width: 1px;
  }

  .mark {
    align-items: center;
    background: var(--jb-bg-input);
    border: 1px solid var(--jb-border-color);
    color: var(--jb-text-on-accent);
    display: inline-flex;
    flex: 0 0 auto;
    height: 16px;
    justify-content: center;
    margin-top: 1px;
    width: 16px;
  }

  .mark::before {
    box-sizing: border-box;
    content: "";
    flex: 0 0 auto;
    opacity: 0;
  }

  .checkbox .mark {
    border-radius: 3px;
  }

  .radio .mark {
    border-radius: 50%;
  }

  .native-check:focus-visible + .mark {
    box-shadow: var(--jb-focus-ring);
  }

  .native-check:checked + .mark,
  .native-check:indeterminate + .mark {
    background: var(--jb-accent-color);
    border-color: var(--jb-accent-color);
  }

  .native-check:disabled + .mark,
  .native-check:disabled ~ .choice-label {
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .checkbox .mark::before {
    border: solid currentColor;
    border-width: 0 2px 2px 0;
    height: 8px;
    margin-top: -1px;
    transform: rotate(45deg);
    width: 4px;
  }

  .checkbox .native-check:checked + .mark::before {
    opacity: 1;
  }

  .checkbox .native-check:indeterminate + .mark::before {
    background: currentColor;
    border: 0;
    height: 2px;
    margin-top: 0;
    opacity: 1;
    transform: none;
    width: 8px;
  }

  .radio .mark::before {
    background: currentColor;
    border-radius: 50%;
    height: 6px;
    width: 6px;
  }

  .radio .native-check:checked + .mark::before {
    opacity: 1;
  }
`;
var JbActionButton = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		expanded: {
			type: Boolean,
			reflect: true
		},
		label: {
			type: String,
			reflect: true
		},
		selected: {
			type: Boolean,
			reflect: true
		}
	};
	static styles = [hostStyles, buttonStyles];
	disabled = false;
	expanded = false;
	label = "";
	selected = false;
	render() {
		return b`
      <button
        part="button"
        class=${[
			"button",
			"icon",
			this.selected ? "selected" : ""
		].filter(Boolean).join(" ")}
        type="button"
        ?disabled=${this.disabled}
        aria-label=${this.label || A}
        aria-expanded=${this.expanded ? "true" : A}
        aria-pressed=${boolAttribute(this.selected)}
      >
        <span part="icon" class="icon-slot"><slot></slot></span>
      </button>
    `;
	}
};
var JbButton = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		hasIcon: { state: true },
		pressed: {
			type: Boolean,
			reflect: true
		},
		selected: {
			type: Boolean,
			reflect: true
		},
		size: {
			type: String,
			reflect: true
		},
		type: {
			type: String,
			reflect: true
		},
		variant: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, buttonStyles];
	disabled = false;
	hasIcon = false;
	pressed = false;
	selected = false;
	size = "default";
	type = "button";
	variant = "default";
	render() {
		const pressed = this.pressed || this.selected;
		return b`
      <button
        part="button"
        class=${this.buttonClass()}
        type=${this.type}
        ?disabled=${this.disabled}
        aria-pressed=${boolAttribute(pressed)}
        data-pressed=${String(this.pressed)}
      >
        <span part="icon" class=${this.hasIcon ? "icon-slot" : "icon-slot empty"}>
          <slot name="icon" @slotchange=${this.updateIconState}></slot>
        </span>
        <span part="label"><slot></slot></span>
      </button>
    `;
	}
	buttonClass() {
		return [
			"button",
			this.variant,
			this.size,
			this.selected ? "selected" : ""
		].filter(Boolean).join(" ");
	}
	updateIconState(event) {
		const slot = event.target;
		const hasAssignedElement = slot.assignedElements({ flatten: true }).length > 0;
		const hasAssignedText = slot.assignedNodes({ flatten: true }).some((node) => Boolean(node.textContent?.trim()));
		this.hasIcon = hasAssignedElement || hasAssignedText;
	}
};
function emitStandardEvent(host, type) {
	host.dispatchEvent(new Event(type, {
		bubbles: true,
		composed: true
	}));
}
function emitValueEvent(host, type, value) {
	host.dispatchEvent(new CustomEvent(type, {
		detail: { value },
		bubbles: true,
		composed: true
	}));
}
var JbCheckbox = class extends i$1 {
	static properties = {
		checked: {
			type: Boolean,
			reflect: true
		},
		disabled: {
			type: Boolean,
			reflect: true
		},
		indeterminate: {
			type: Boolean,
			reflect: true
		},
		name: {
			type: String,
			reflect: true
		},
		readOnly: {
			type: Boolean,
			reflect: true,
			attribute: "readonly"
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, choiceStyles];
	checked = false;
	disabled = false;
	indeterminate = false;
	name = "";
	readOnly = false;
	value = "on";
	updated(changedProperties) {
		if (changedProperties.has("indeterminate")) {
			const input = this.shadowRoot?.querySelector("input");
			if (input) input.indeterminate = this.indeterminate;
		}
	}
	render() {
		return b`
      <label part="label" class="choice checkbox">
        <input
          part="input"
          class="native-check"
          type="checkbox"
          name=${this.name || A}
          value=${this.value}
          .checked=${this.checked}
          .indeterminate=${this.indeterminate}
          ?disabled=${this.disabled}
          aria-readonly=${boolAttribute(this.readOnly)}
          @click=${this.onReadOnlyClick}
          @change=${this.onNativeChange}
        >
        <span part="control" class="mark"></span>
        <span part="text" class="choice-label"><slot></slot></span>
      </label>
    `;
	}
	onReadOnlyClick(event) {
		if (this.readOnly) event.preventDefault();
	}
	onNativeChange(event) {
		event.stopPropagation();
		const input = event.currentTarget;
		if (this.readOnly) {
			input.checked = this.checked;
			input.indeterminate = this.indeterminate;
			return;
		}
		this.checked = input.checked;
		this.indeterminate = input.indeterminate;
		emitStandardEvent(this, "input");
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.checked ? this.value : "");
	}
};
var WEBVIEW_FOCUS_LEAVE_EVENT$1 = "wvi-focus-leave";
var WebViewFocusLeaveController = class {
	onFocusLeave;
	listener = () => this.onFocusLeave();
	constructor(host, onFocusLeave) {
		this.onFocusLeave = onFocusLeave;
		host.addController(this);
	}
	hostConnected() {
		window.addEventListener(WEBVIEW_FOCUS_LEAVE_EVENT$1, this.listener);
	}
	hostDisconnected() {
		window.removeEventListener(WEBVIEW_FOCUS_LEAVE_EVENT$1, this.listener);
	}
};
function normalizeOptions(options) {
	return Array.isArray(options) ? options : [];
}
function optionLabel(options, value, placeholder = "") {
	return options.find((option) => option.value === value)?.label ?? placeholder;
}
var TextInputBase = class extends i$1 {
	static properties = {
		autocomplete: {
			type: String,
			reflect: true
		},
		disabled: {
			type: Boolean,
			reflect: true
		},
		inputMode: {
			type: String,
			reflect: true,
			attribute: "inputmode"
		},
		invalid: {
			type: Boolean,
			reflect: true
		},
		maxLength: {
			type: Number,
			reflect: true,
			attribute: "maxlength"
		},
		name: {
			type: String,
			reflect: true
		},
		placeholder: {
			type: String,
			reflect: true
		},
		readOnly: {
			type: Boolean,
			reflect: true,
			attribute: "readonly"
		},
		required: {
			type: Boolean,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, inputStyles];
	autocomplete = "";
	disabled = false;
	inputMode = "";
	invalid = false;
	maxLength = -1;
	name = "";
	placeholder = "";
	readOnly = false;
	required = false;
	value = "";
	get inputType() {
		return "text";
	}
	render() {
		return b`
      <input
        part="input"
        class="field-control"
        type=${this.inputType}
        name=${this.name || A}
        autocomplete=${this.autocomplete || A}
        inputmode=${this.inputMode || A}
        maxlength=${this.maxLength >= 0 ? this.maxLength : A}
        placeholder=${this.placeholder || A}
        .value=${this.value}
        ?disabled=${this.disabled}
        ?readonly=${this.readOnly}
        ?required=${this.required}
        aria-invalid=${this.invalid ? "true" : "false"}
        @input=${this.onInput}
        @change=${this.onChange}
      >
    `;
	}
	onInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbCombobox = class extends TextInputBase {
	static properties = {
		...TextInputBase.properties,
		items: { attribute: false }
	};
	items = [];
	constructor() {
		super();
		new WebViewFocusLeaveController(this, () => this.renderRoot.querySelector("input")?.blur());
	}
	render() {
		const listId = `${this.localName}-${Math.random().toString(36).slice(2)}`;
		return b`
      <span part="control" class="combo-wrap">
        <input
          part="input"
          class="field-control"
          type="text"
          list=${listId}
          name=${this.name || A}
          placeholder=${this.placeholder || A}
          .value=${this.value}
          ?disabled=${this.disabled}
          ?readonly=${this.readOnly}
          ?required=${this.required}
          aria-invalid=${this.invalid ? "true" : "false"}
          @input=${this.onInput}
          @change=${this.onChange}
        >
        <datalist id=${listId}>${normalizeOptions(this.items).map((option) => b`<option value=${option.value} label=${option.label}></option>`)}</datalist>
      </span>
    `;
	}
};
var JbContextHelp = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		open: {
			type: Boolean,
			reflect: true
		},
		text: {
			type: String,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		buttonStyles,
		popupStyles,
		i`
    .help {
      border-radius: 50%;
      font-weight: var(--jb-font-weight-medium);
      height: 18px;
      min-height: 18px;
      min-width: 18px;
      padding: 0;
      width: 18px;
    }

    .popup {
      line-height: var(--jb-line-height-paragraph);
      max-width: 260px;
      -webkit-user-select: text;
      user-select: text;
      white-space: normal;
    }
  `
	];
	disabled = false;
	open = false;
	text = "";
	render() {
		return b`
      <span part="root" class="menu-root">
        <button part="button" class="button toolbar help" type="button" ?disabled=${this.disabled} aria-label="Context help" aria-expanded=${String(this.open)} @click=${() => this.open = !this.open}>?</button>
        ${this.open ? b`<div part="popup" class="popup"><slot>${this.text}</slot></div>` : A}
      </span>
    `;
	}
};
var JbDisclosure = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		label: {
			type: String,
			reflect: true
		},
		open: {
			type: Boolean,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		buttonStyles,
		i`
    .content {
      margin-top: var(--jb-space-sm);
      padding-left: calc(var(--jb-control-height-compact) + var(--jb-space-xs));
      -webkit-user-select: text;
      user-select: text;
    }
  `
	];
	disabled = false;
	label = "";
	open = false;
	render() {
		return b`
      <button part="summary" class="button link" type="button" ?disabled=${this.disabled} aria-expanded=${String(this.open)} @click=${() => this.open = !this.open}>
        <span part="chevron" class=${["chevron", this.open ? "" : "right"].filter(Boolean).join(" ")} aria-hidden="true"></span>
        <span part="label"><slot name="summary">${this.label}</slot></span>
      </button>
      ${this.open ? b`<div part="content" class="content"><slot></slot></div>` : A}
    `;
	}
};
var JbMenuButton$1 = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		items: { attribute: false },
		label: {
			type: String,
			reflect: true
		},
		open: {
			type: Boolean,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		},
		variant: {
			type: String,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		buttonStyles,
		popupStyles
	];
	disabled = false;
	items = [];
	label = "";
	open = false;
	value = "";
	variant = "default";
	constructor() {
		super();
		new WebViewFocusLeaveController(this, () => {
			this.open = false;
		});
	}
	render() {
		const options = normalizeOptions(this.items);
		return b`
      <span part="root" class="menu-root">
        <button part="button" class=${["button", this.variant].filter(Boolean).join(" ")} type="button" ?disabled=${this.disabled} aria-haspopup="menu" aria-expanded=${String(this.open)} @click=${this.toggleOpen} @keydown=${this.onButtonKeyDown}>
          <span part="label"><slot>${this.label || optionLabel(options, this.value)}</slot></span>
          <span part="chevron" class="chevron" aria-hidden="true"></span>
        </button>
        ${this.open ? b`<div part="menu" class="popup" role="menu">${options.length > 0 ? options.map((option) => this.renderMenuItem(option)) : b`<slot name="menu"></slot>`}</div>` : A}
      </span>
    `;
	}
	renderMenuItem(option) {
		return b`<button part="menu-item" class="menu-item" type="button" role="menuitem" ?disabled=${Boolean(option.disabled)} @click=${() => this.selectOption(option)}>${option.label}</button>`;
	}
	toggleOpen() {
		if (!this.disabled) this.open = !this.open;
	}
	onButtonKeyDown(event) {
		if (event.key === "Escape") this.open = false;
		else if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
			event.preventDefault();
			this.open = true;
		}
	}
	selectOption(option) {
		if (this.disabled || option.disabled) return;
		this.value = option.value;
		this.open = false;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-select", this.value);
	}
};
var JbDropdownLink = class extends JbMenuButton$1 {
	variant = "link";
};
var JbExpandableTextField = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		expanded: {
			type: Boolean,
			reflect: true
		},
		placeholder: {
			type: String,
			reflect: true
		},
		readOnly: {
			type: Boolean,
			reflect: true,
			attribute: "readonly"
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		inputStyles,
		buttonStyles,
		i`
    .expandable {
      display: grid;
      gap: var(--jb-space-xs);
      grid-template-columns: minmax(80px, 1fr) auto;
    }

    .expanded {
      grid-column: 1 / -1;
    }

    .button.expand-button {
      height: var(--jb-control-height);
      min-height: var(--jb-control-height);
      min-width: var(--jb-control-height);
      padding: 0;
      width: var(--jb-control-height);
    }
  `
	];
	disabled = false;
	expanded = false;
	placeholder = "";
	readOnly = false;
	value = "";
	render() {
		return b`
      <span part="root" class="expandable">
        ${this.expanded ? b`
          <textarea part="textarea" class="textarea expanded" placeholder=${this.placeholder || A} .value=${this.value} ?disabled=${this.disabled} ?readonly=${this.readOnly} @input=${this.onTextAreaInput} @change=${this.onTextAreaChange}></textarea>
        ` : b`
          <input part="input" class="field-control" type="text" placeholder=${this.placeholder || A} .value=${this.value} ?disabled=${this.disabled} ?readonly=${this.readOnly} @input=${this.onInput} @change=${this.onChange}>
        `}
        <button
          part="expand-button"
          class="button toolbar expand-button"
          type="button"
          ?disabled=${this.disabled}
          aria-label=${this.expanded ? "Collapse" : "Expand"}
          aria-expanded=${String(this.expanded)}
          @click=${() => this.expanded = !this.expanded}
        >
          <span class=${this.expanded ? "button-icon minus" : "button-icon plus"} aria-hidden="true"></span>
        </button>
      </span>
    `;
	}
	onInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
	onTextAreaInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onTextAreaChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbField = class extends i$1 {
	static properties = {
		error: {
			type: String,
			reflect: true
		},
		help: {
			type: String,
			reflect: true
		},
		label: {
			type: String,
			reflect: true
		},
		required: {
			type: Boolean,
			reflect: true
		},
		warning: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    .field {
      display: grid;
      gap: var(--jb-space-xs);
    }

    .body {
      display: grid;
      gap: var(--jb-space-xs);
      -webkit-user-select: text;
      user-select: text;
    }
  `];
	error = "";
	help = "";
	label = "";
	required = false;
	warning = "";
	render() {
		const helpTone = this.error ? "error" : this.warning ? "warning" : "default";
		return b`
      <div part="field" class="field">
        ${this.label ? b`<jb-label part="label" ?required=${this.required}>${this.label}</jb-label>` : b`<slot name="label"></slot>`}
        <div part="control" class="body"><slot></slot></div>
        ${this.error || this.warning || this.help ? b`<jb-help-text part="help" tone=${helpTone}>${this.error || this.warning || this.help}</jb-help-text>` : b`<slot name="help"></slot>`}
      </div>
    `;
	}
};
var JbFieldGroup = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		label: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    fieldset {
      border: 0;
      margin: 0;
      padding: 0;
    }

    legend {
      color: var(--jb-text-color);
      font-weight: var(--jb-font-weight-medium);
      margin-bottom: var(--jb-space-sm);
      padding: 0;
      -webkit-user-select: none;
      user-select: none;
    }

    .body {
      display: grid;
      gap: var(--jb-space-sm);
      -webkit-user-select: text;
      user-select: text;
    }
  `];
	disabled = false;
	label = "";
	render() {
		return b`<fieldset part="group" ?disabled=${this.disabled}>${this.label ? b`<legend part="label">${this.label}</legend>` : A}<div part="body" class="body"><slot></slot></div></fieldset>`;
	}
};
var JbHelpText = class extends i$1 {
	static properties = { tone: {
		type: String,
		reflect: true
	} };
	static styles = [hostStyles, i`
    .help {
      color: var(--jb-text-muted);
      line-height: var(--jb-line-height-paragraph);
      -webkit-user-select: text;
      user-select: text;
    }

    .error {
      color: var(--jb-danger-color);
    }

    .warning {
      color: var(--jb-warning-color);
    }
  `];
	tone = "default";
	render() {
		return b`<div part="help" class=${["help", this.tone].join(" ")}><slot></slot></div>`;
	}
};
var JbIcon = class extends i$1 {
	static properties = {
		label: {
			type: String,
			reflect: true
		},
		name: {
			type: String,
			reflect: true
		},
		size: {
			type: String,
			reflect: true
		},
		src: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    :host {
      display: inline-flex;
      vertical-align: middle;
    }

    .icon {
      align-items: center;
      color: currentColor;
      display: inline-flex;
      height: 16px;
      justify-content: center;
      line-height: 1;
      -webkit-user-select: none;
      user-select: none;
      width: 16px;
    }

    .icon.large {
      height: 20px;
      width: 20px;
    }

    img {
      display: block;
      height: 100%;
      width: 100%;
    }
  `];
	constructor() {
		super();
		this.label = "";
		this.name = "";
		this.size = "default";
		this.src = "";
	}
	render() {
		return b`<span part="icon" class=${["icon", this.size].join(" ")} role=${this.label ? "img" : A} aria-label=${this.label || A}>${this.renderContent()}</span>`;
	}
	renderContent() {
		if (this.src) return b`<img src=${this.src} alt="" draggable="false">`;
		return b`<slot>${this.name}</slot>`;
	}
};
var JbLabel = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		for: {
			type: String,
			reflect: true
		},
		required: {
			type: Boolean,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    label {
      color: var(--jb-text-color);
      display: inline-block;
      -webkit-user-select: none;
      user-select: none;
    }

    :host([disabled]) label {
      color: var(--jb-text-disabled);
    }

    .required {
      color: var(--jb-danger-color);
      margin-left: 2px;
    }
  `];
	disabled = false;
	for = "";
	required = false;
	render() {
		return b`<label part="label" for=${this.for || A}><slot></slot>${this.required ? b`<span part="required" class="required">*</span>` : A}</label>`;
	}
};
var JbNumberField = class extends TextInputBase {
	static properties = {
		...TextInputBase.properties,
		max: {
			type: String,
			reflect: true
		},
		min: {
			type: String,
			reflect: true
		},
		step: {
			type: String,
			reflect: true
		}
	};
	max = "";
	min = "";
	step = "";
	get inputType() {
		return "number";
	}
	render() {
		return b`
      <input
        part="input"
        class="field-control"
        type="number"
        name=${this.name || A}
        min=${this.min || A}
        max=${this.max || A}
        step=${this.step || A}
        placeholder=${this.placeholder || A}
        .value=${this.value}
        ?disabled=${this.disabled}
        ?readonly=${this.readOnly}
        ?required=${this.required}
        aria-invalid=${this.invalid ? "true" : "false"}
        @input=${this.onInput}
        @change=${this.onChange}
      >
    `;
	}
};
var JbPasswordField = class extends TextInputBase {
	get inputType() {
		return "password";
	}
};
var JbRadio = class extends i$1 {
	static properties = {
		checked: {
			type: Boolean,
			reflect: true
		},
		disabled: {
			type: Boolean,
			reflect: true
		},
		name: {
			type: String,
			reflect: true
		},
		readOnly: {
			type: Boolean,
			reflect: true,
			attribute: "readonly"
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, choiceStyles];
	checked = false;
	disabled = false;
	name = "";
	readOnly = false;
	value = "on";
	render() {
		return b`
      <label part="label" class="choice radio">
        <input
          part="input"
          class="native-check"
          type="radio"
          name=${this.name || A}
          value=${this.value}
          .checked=${this.checked}
          ?disabled=${this.disabled}
          aria-readonly=${boolAttribute(this.readOnly)}
          @click=${this.onReadOnlyClick}
          @change=${this.onNativeChange}
        >
        <span part="control" class="mark"></span>
        <span part="text" class="choice-label"><slot></slot></span>
      </label>
    `;
	}
	onReadOnlyClick(event) {
		if (this.readOnly) event.preventDefault();
	}
	onNativeChange(event) {
		event.stopPropagation();
		const input = event.currentTarget;
		if (this.readOnly) {
			input.checked = this.checked;
			return;
		}
		this.checked = input.checked;
		if (this.checked) {
			emitStandardEvent(this, "input");
			emitStandardEvent(this, "change");
			emitValueEvent(this, "jb-change", this.value);
		}
	}
};
var JbRadioGroup = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		items: { attribute: false },
		label: {
			type: String,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    .group {
      border: 0;
      display: grid;
      gap: var(--jb-space-xs);
      margin: 0;
      padding: 0;
    }

    .legend {
      color: var(--jb-text-muted);
      margin-bottom: var(--jb-space-xs);
      padding: 0;
      -webkit-user-select: none;
      user-select: none;
    }
  `];
	disabled = false;
	items = [];
	label = "";
	value = "";
	render() {
		const options = normalizeOptions(this.items);
		return b`
      <fieldset part="group" class="group" ?disabled=${this.disabled}>
        ${this.label ? b`<legend part="label" class="legend">${this.label}</legend>` : A}
        ${options.length > 0 ? options.map((option) => b`
          <jb-radio
            part="radio"
            value=${option.value}
            ?checked=${this.value === option.value}
            ?disabled=${this.disabled || Boolean(option.disabled)}
            @change=${() => this.selectOption(option)}
          >${option.label}</jb-radio>
        `) : b`<slot></slot>`}
      </fieldset>
    `;
	}
	selectOption(option) {
		if (this.disabled || option.disabled || this.value === option.value) return;
		this.value = option.value;
		emitStandardEvent(this, "input");
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbSegmentedControl = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		items: { attribute: false },
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		buttonStyles,
		i`
    .segments {
      align-items: center;
      background: var(--jb-bg-control);
      border: 1px solid var(--jb-border-color-muted);
      border-radius: var(--jb-control-radius);
      display: inline-flex;
      padding: 1px;
    }

    .segment {
      border-color: transparent;
      border-radius: 3px;
      min-width: 0;
    }
  `
	];
	disabled = false;
	items = [];
	value = "";
	render() {
		return b`
      <div part="group" class="segments" role="radiogroup">
        ${normalizeOptions(this.items).map((option) => b`
          <button part="segment" class=${[
			"button",
			"toolbar",
			"segment",
			this.value === option.value ? "selected" : ""
		].filter(Boolean).join(" ")} type="button" role="radio" aria-checked=${String(this.value === option.value)} ?disabled=${this.disabled || Boolean(option.disabled)} @click=${() => this.selectOption(option)}>${option.label}</button>
        `)}
      </div>
    `;
	}
	selectOption(option) {
		if (this.disabled || option.disabled || this.value === option.value) return;
		this.value = option.value;
		emitStandardEvent(this, "input");
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbSelect$1 = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		invalid: {
			type: Boolean,
			reflect: true
		},
		items: { attribute: false },
		name: {
			type: String,
			reflect: true
		},
		placeholder: {
			type: String,
			reflect: true
		},
		required: {
			type: Boolean,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, inputStyles];
	disabled = false;
	invalid = false;
	items = [];
	name = "";
	placeholder = "";
	required = false;
	value = "";
	constructor() {
		super();
		new WebViewFocusLeaveController(this, () => this.renderRoot.querySelector("select")?.blur());
	}
	render() {
		const options = normalizeOptions(this.items);
		return b`
      <span part="control" class="select-wrap">
        <select
          part="select"
          class="select"
          name=${this.name || A}
          .value=${this.value}
          ?disabled=${this.disabled}
          ?required=${this.required}
          aria-invalid=${this.invalid ? "true" : "false"}
          @input=${this.onSelectInput}
          @change=${this.onSelectChange}
        >
          ${this.placeholder ? b`<option value="" disabled ?selected=${this.value === ""}>${this.placeholder}</option>` : A}
          ${options.map((option) => b`<option value=${option.value} ?disabled=${Boolean(option.disabled)}>${option.label}</option>`)}
        </select>
      </span>
    `;
	}
	onSelectInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onSelectChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbSeparator = class extends i$1 {
	static properties = { orientation: {
		type: String,
		reflect: true
	} };
	static styles = [hostStyles, i`
    :host {
      display: block;
    }

    .separator {
      background: var(--jb-border-color-muted);
      height: 1px;
      width: 100%;
    }

    :host([orientation="vertical"]) {
      display: inline-block;
      height: 100%;
      min-height: var(--jb-control-height-compact);
    }

    :host([orientation="vertical"]) .separator {
      height: 100%;
      width: 1px;
    }
  `];
	orientation = "horizontal";
	render() {
		return b`<div part="separator" class="separator" role="separator" aria-orientation=${this.orientation}></div>`;
	}
};
var JbSlider = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		max: {
			type: Number,
			reflect: true
		},
		min: {
			type: Number,
			reflect: true
		},
		step: {
			type: Number,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    .slider {
      accent-color: var(--jb-accent-color);
      -webkit-user-select: none;
      user-select: none;
      width: 100%;
    }
  `];
	disabled = false;
	max = 100;
	min = 0;
	step = 1;
	value = "0";
	render() {
		return b`<input part="input" class="slider" type="range" min=${this.min} max=${this.max} step=${this.step} .value=${this.value} ?disabled=${this.disabled} @input=${this.onInput} @change=${this.onChange}>`;
	}
	onInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbSpinner = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		max: {
			type: Number,
			reflect: true
		},
		min: {
			type: Number,
			reflect: true
		},
		step: {
			type: Number,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		inputStyles,
		buttonStyles,
		i`
    .spinner {
      display: grid;
      gap: var(--jb-space-xs);
      grid-template-columns: minmax(64px, 1fr) auto auto;
    }

    .button.step-button {
      height: var(--jb-control-height);
      min-height: var(--jb-control-height);
      min-width: var(--jb-control-height);
      padding: 0;
      width: var(--jb-control-height);
    }
  `
	];
	disabled = false;
	max = Number.POSITIVE_INFINITY;
	min = Number.NEGATIVE_INFINITY;
	step = 1;
	value = "0";
	render() {
		return b`
      <span part="control" class="spinner">
        <input part="input" class="field-control" type="number" min=${Number.isFinite(this.min) ? this.min : A} max=${Number.isFinite(this.max) ? this.max : A} step=${this.step} .value=${this.value} ?disabled=${this.disabled} @input=${this.onInput} @change=${this.onChange}>
        <button part="decrement-button" class="button toolbar step-button" type="button" ?disabled=${this.disabled} aria-label="Decrement" @click=${() => this.stepValue(-1)}>
          <span class="button-icon minus" aria-hidden="true"></span>
        </button>
        <button part="increment-button" class="button toolbar step-button" type="button" ?disabled=${this.disabled} aria-label="Increment" @click=${() => this.stepValue(1)}>
          <span class="button-icon plus" aria-hidden="true"></span>
        </button>
      </span>
    `;
	}
	onInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
	stepValue(direction) {
		if (this.disabled) return;
		const current = Number(this.value || 0);
		const next = Math.min(this.max, Math.max(this.min, current + direction * this.step));
		this.value = String(next);
		emitStandardEvent(this, "input");
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbTabs = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		items: { attribute: false },
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [
		hostStyles,
		buttonStyles,
		i`
    .tabs {
      border-bottom: 1px solid var(--jb-border-color-muted);
      display: flex;
      gap: var(--jb-space-xs);
      margin-bottom: var(--jb-space-sm);
    }

    .tab {
      border-bottom-left-radius: 0;
      border-bottom-right-radius: 0;
      margin-bottom: -1px;
    }

    .panel {
      -webkit-user-select: text;
      user-select: text;
    }
  `
	];
	disabled = false;
	items = [];
	value = "";
	render() {
		return b`
      <div part="tablist" class="tabs" role="tablist">
        ${normalizeOptions(this.items).map((option) => b`<button part="tab" class=${[
			"button",
			"toolbar",
			"tab",
			this.value === option.value ? "selected" : ""
		].filter(Boolean).join(" ")} type="button" role="tab" aria-selected=${String(this.value === option.value)} ?disabled=${this.disabled || Boolean(option.disabled)} @click=${() => this.selectOption(option)}>${option.label}</button>`)}
      </div>
      <div part="panel" class="panel"><slot></slot></div>
    `;
	}
	selectOption(option) {
		if (this.disabled || option.disabled || this.value === option.value) return;
		this.value = option.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbText = class extends i$1 {
	static properties = {
		size: {
			type: String,
			reflect: true
		},
		tone: {
			type: String,
			reflect: true
		},
		weight: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, i`
    .text {
      color: var(--jb-text-color);
      margin: 0;
      -webkit-user-select: text;
      user-select: text;
    }

    .small {
      font-size: var(--jb-font-size-small);
    }

    .muted {
      color: var(--jb-text-muted);
    }

    .secondary {
      color: var(--jb-text-secondary);
    }

    .medium {
      font-weight: var(--jb-font-weight-medium);
    }
  `];
	size = "default";
	tone = "default";
	weight = "regular";
	render() {
		return b`<span part="text" class=${[
			"text",
			this.size,
			this.tone,
			this.weight
		].join(" ")}><slot></slot></span>`;
	}
};
var JbTextArea = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
		invalid: {
			type: Boolean,
			reflect: true
		},
		name: {
			type: String,
			reflect: true
		},
		placeholder: {
			type: String,
			reflect: true
		},
		readOnly: {
			type: Boolean,
			reflect: true,
			attribute: "readonly"
		},
		required: {
			type: Boolean,
			reflect: true
		},
		rows: {
			type: Number,
			reflect: true
		},
		value: {
			type: String,
			reflect: true
		}
	};
	static styles = [hostStyles, inputStyles];
	disabled = false;
	invalid = false;
	name = "";
	placeholder = "";
	readOnly = false;
	required = false;
	rows = 4;
	value = "";
	render() {
		return b`
      <textarea
        part="textarea"
        class="textarea"
        name=${this.name || A}
        placeholder=${this.placeholder || A}
        rows=${this.rows}
        .value=${this.value}
        ?disabled=${this.disabled}
        ?readonly=${this.readOnly}
        ?required=${this.required}
        aria-invalid=${this.invalid ? "true" : "false"}
        @input=${this.onInput}
        @change=${this.onChange}
      ></textarea>
    `;
	}
	onInput(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "input");
	}
	onChange(event) {
		event.stopPropagation();
		this.value = event.currentTarget.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
var JbTextField = class extends TextInputBase {};
var controlsTokenStyleId = "jb-webview-controls-tokens";
var controlsTokenStyles = `
:root {
  --jb-font-family: var(--ij-font, "Inter", "Segoe UI", -apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif);
  --jb-font-size: var(--ij-font-size, 13px);
  --jb-font-size-h0: var(--ij-font-size-h0, calc(var(--ij-font-size, 13px) + 12px));
  --jb-font-size-h1: var(--ij-font-size-h1, calc(var(--ij-font-size, 13px) + 9px));
  --jb-font-size-h2: var(--ij-font-size-h2, calc(var(--ij-font-size, 13px) + 5px));
  --jb-font-size-h3: var(--ij-font-size-h3, calc(var(--ij-font-size, 13px) + 3px));
  --jb-font-size-h4: var(--ij-font-size-h4, calc(var(--ij-font-size, 13px) + 1px));
  --jb-font-size-regular: var(--ij-font-size-regular, var(--ij-font-size, 13px));
  --jb-font-size-medium: var(--ij-font-size-medium, calc(var(--ij-font-size, 13px) - 1px));
  --jb-font-size-small: var(--ij-font-size-small, max(calc(var(--ij-font-size, 13px) - 2px), 11px));
  --jb-font-size-mini: var(--ij-font-size-mini, max(calc(var(--ij-font-size, 13px) - 4px), 9px));
  --jb-line-height: var(--ij-line-height-default, 16px);
  --jb-line-height-compact: var(--ij-line-height-compact, calc(var(--ij-line-height-default, 16px) - 2px));
  --jb-line-height-paragraph: var(--ij-line-height-paragraph, calc(var(--ij-line-height-default, 16px) + 2px));
  --jb-line-height-heading: var(--ij-line-height-heading, calc(var(--ij-line-height-default, 16px) + 4px));
  --jb-font-weight-regular: var(--ij-font-weight-regular, 400);
  --jb-font-weight-medium: var(--ij-font-weight-medium, 500);
  --jb-control-height: var(--ij-control-height, max(28px, calc(var(--ij-line-height-default, 16px) + 12px)));
  --jb-control-height-compact: var(--ij-control-height-compact, max(24px, calc(var(--ij-line-height-default, 16px) + 8px)));
  --jb-control-radius: var(--ij-radius-control, 4px);
  --jb-control-padding-x: 8px;
  --jb-control-gap: 6px;
  --jb-space-xs: 4px;
  --jb-space-sm: 8px;
  --jb-space-md: 12px;
  --jb-space-lg: 16px;
  --jb-bg-window: var(--ij-bg-window, #ffffff);
  --jb-bg-panel: var(--ij-bg-panel, #f7f8f9);
  --jb-bg-control: var(--ij-bg-control-raised, #ffffff);
  --jb-bg-input: var(--ij-bg-input, #ffffff);
  --jb-bg-hover: var(--ij-bg-hover, #00000012);
  --jb-bg-pressed: var(--ij-bg-pressed, #00000020);
  --jb-bg-selected: var(--ij-bg-selected, #d0dffe);
  --jb-bg-selected-muted: var(--ij-bg-selected-muted, #e3ebfe);
  --jb-border-color: var(--ij-control-border-raised, #b5b7bd);
  --jb-border-color-muted: var(--ij-border-inline, #e9eaee);
  --jb-border-color-strong: var(--ij-border-strong, #d1d3d9);
  --jb-text-color: var(--ij-text-primary, #000000);
  --jb-text-muted: var(--ij-text-muted, #5f6269);
  --jb-text-secondary: var(--ij-text-secondary, #73767c);
  --jb-text-disabled: var(--ij-text-disabled, #9fa2a8);
  --jb-text-on-accent: var(--ij-text-on-accent, #ffffff);
  --jb-accent-color: var(--ij-accent, #3871e1);
  --jb-accent-hover-color: var(--ij-accent-hover, #2f5eb9);
  --jb-accent-text-color: var(--ij-accent-text, #2f5eb9);
  --jb-accent-soft-bg: var(--ij-accent-soft, #3871e129);
  --jb-danger-color: var(--ij-danger, #c54e58);
  --jb-danger-bg: var(--ij-danger-soft, #fff6f5);
  --jb-danger-border-color: var(--ij-danger-border, #ffc4c5);
  --jb-warning-color: var(--ij-warning, #a56906);
  --jb-warning-bg: var(--ij-warning-soft, #fff6e9);
  --jb-warning-border-color: var(--ij-warning-border, #f4cd9a);
  --jb-focus-ring: var(--ij-focus-ring, 0 0 0 2px rgba(56, 113, 225, 0.32));
  --jb-popup-shadow: var(--ij-popup-shadow, 0 8px 24px #00000026);
}
`;
function ensureControlsTokensInstalled() {
	if (typeof document === "undefined" || document.getElementById("jb-webview-controls-tokens")) return;
	const style = document.createElement("style");
	style.id = controlsTokenStyleId;
	style.textContent = controlsTokenStyles;
	const target = document.head || document.documentElement;
	target.insertBefore(style, target.firstChild);
}
var allControlDefinitions = {
	"jb-action-button": JbActionButton,
	"jb-button": JbButton,
	"jb-checkbox": JbCheckbox,
	"jb-combobox": JbCombobox,
	"jb-context-help": JbContextHelp,
	"jb-disclosure": JbDisclosure,
	"jb-dropdown-link": JbDropdownLink,
	"jb-expandable-text-field": JbExpandableTextField,
	"jb-field": JbField,
	"jb-field-group": JbFieldGroup,
	"jb-help-text": JbHelpText,
	"jb-icon": JbIcon,
	"jb-label": JbLabel,
	"jb-menu-button": JbMenuButton$1,
	"jb-number-field": JbNumberField,
	"jb-password-field": JbPasswordField,
	"jb-radio": JbRadio,
	"jb-radio-group": JbRadioGroup,
	"jb-segmented-control": JbSegmentedControl,
	"jb-select": JbSelect$1,
	"jb-separator": JbSeparator,
	"jb-slider": JbSlider,
	"jb-spinner": JbSpinner,
	"jb-tabs": JbTabs,
	"jb-text": JbText,
	"jb-text-area": JbTextArea,
	"jb-text-field": JbTextField
};
function defineAllControls(registry = customElements) {
	ensureControlsTokensInstalled();
	for (const [tagName, constructor] of Object.entries(allControlDefinitions)) defineControl(tagName, constructor, registry);
}
defineAllControls();
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var import_client = require_client();
var import_jsx_runtime = require_jsx_runtime();
function JbControlChrome({ className, compact, disabled, invalid, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
		...props,
		className: ["jbReactControlChrome", className].filter(Boolean).join(" "),
		"data-compact": compact ? "true" : void 0,
		"data-disabled": disabled ? "true" : void 0,
		"data-invalid": invalid ? "true" : void 0
	});
}
function apiId() {
	return function createApiId(namespace) {
		validateApiNamespace(namespace);
		return { namespace };
	};
}
function validateApiNamespace(namespace) {
	if (typeof namespace !== "string" || namespace.length === 0) throw new Error("WebView API namespace must be a non-empty string");
	if (namespace.startsWith(".") || namespace.endsWith(".") || namespace.startsWith("/") || namespace.endsWith("/")) throw new Error("WebView API namespace must not start or end with '.' or '/': " + namespace);
	if (!/^[A-Za-z0-9_.-]+$/.test(namespace)) throw new Error("WebView API namespace contains unsupported characters: " + namespace);
}
var WEBVIEW_FOCUS_LEAVE_EVENT = "wvi-focus-leave";
function addWebViewFocusLeaveListener(listener) {
	window.addEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, listener);
	return () => window.removeEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, listener);
}
apiId()("webview.focus");
apiId()("webview.focus");
function useWebViewFocusLeave(listener, enabled = true) {
	const listenerRef = (0, import_react.useRef)(listener);
	listenerRef.current = listener;
	(0, import_react.useEffect)(() => {
		if (!enabled) return;
		return addWebViewFocusLeaveListener(() => listenerRef.current());
	}, [enabled]);
}
var reactControlsPortalRootId = "jb-react-controls-portal-root";
function getReactControlsPortalRoot() {
	if (typeof document === "undefined") return null;
	return document.getElementById(reactControlsPortalRootId);
}
function ensureReactControlsPortalRoot() {
	if (typeof document === "undefined") return null;
	const existing = getReactControlsPortalRoot();
	if (existing) return existing;
	const root = document.createElement("div");
	root.id = reactControlsPortalRootId;
	root.className = "jbReactControlsPortalRoot";
	document.body.append(root);
	return root;
}
var MenuGroup = MenuGroup$1;
var MenuGroupLabel = MenuGroupLabel$1;
var MenuRadioGroup = MenuRadioGroup$1;
function JbMenuButton({ children, className, compact, contentClassName, disabled, icon, label, modal = false, onOpenChange, open, triggerAriaLabel }) {
	const [uncontrolledOpen, setUncontrolledOpen] = (0, import_react.useState)(false);
	const isOpen = open ?? uncontrolledOpen;
	useWebViewFocusLeave(() => updateOpen(false), isOpen);
	function updateOpen(nextOpen) {
		setUncontrolledOpen(nextOpen);
		onOpenChange?.(nextOpen);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuRoot$1, {
		disabled,
		modal,
		open: isOpen,
		onOpenChange: updateOpen,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbControlChrome, {
			className,
			compact,
			disabled,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuTrigger, {
				className: "jbReactMenuTrigger",
				"aria-label": triggerAriaLabel,
				disabled,
				children: [
					icon ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "jbReactMenuIcon",
						"aria-hidden": "true",
						children: icon
					}) : null,
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "jbReactMenuTriggerText",
						children: label
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "jbReactMenuIndicator",
						"aria-hidden": "true",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuChevron, {})
					})
				]
			})
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuContent, {
			className: contentClassName,
			children
		})]
	});
}
function JbMenuContent({ className, children, positionerClassName, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuPortal, {
		container: ensureReactControlsPortalRoot(),
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuPositioner, {
			className: ["jbReactMenuPositioner", positionerClassName].filter(Boolean).join(" "),
			sideOffset: 4,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuPopup, {
				...props,
				className: ["jbReactMenuPopup", className].filter(Boolean).join(" "),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuViewport, {
					className: "jbReactMenuViewport",
					children
				})
			})
		})
	});
}
function JbMenuItem({ className, children, shortcut, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuItem, {
		...props,
		className: ["jbReactMenuItem", className].filter(Boolean).join(" "),
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "jbReactMenuItemIndicator",
				"aria-hidden": "true"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "jbReactMenuItemText",
				children
			}),
			shortcut ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuShortcut, { children: shortcut }) : null
		]
	});
}
function JbMenuCheckboxItem({ className, children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuCheckboxItem, {
		...props,
		className: ["jbReactMenuItem", className].filter(Boolean).join(" "),
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "jbReactMenuItemIndicator",
			"aria-hidden": "true",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuCheckboxItemIndicator, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuCheckIcon, {}) })
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "jbReactMenuItemText",
			children
		})]
	});
}
function JbMenuRadioItem({ className, children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuRadioItem, {
		...props,
		className: ["jbReactMenuItem", className].filter(Boolean).join(" "),
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "jbReactMenuItemIndicator",
			"aria-hidden": "true",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuRadioItemIndicator, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuCheckIcon, {}) })
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "jbReactMenuItemText",
			children
		})]
	});
}
function JbMenuSeparator(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Separator, {
		...props,
		className: ["jbReactMenuSeparator", props.className].filter(Boolean).join(" ")
	});
}
function JbMenuShortcut(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
		...props,
		className: ["jbReactMenuShortcut", props.className].filter(Boolean).join(" ")
	});
}
function MenuChevron() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		className: "jbReactMenuChevron",
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M3 4.5L6 7.5L9 4.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
function MenuCheckIcon() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M2.5 6L5 8.5L9.5 3.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
var PopoverTitle = PopoverTitle$1;
var PopoverDescription = PopoverDescription$1;
var PopoverClose = PopoverClose$1;
function JbPopover({ children, className, compact, contentClassName, disabled, modal = false, onOpenChange, open, trigger, triggerAriaLabel }) {
	const [uncontrolledOpen, setUncontrolledOpen] = (0, import_react.useState)(false);
	const isOpen = open ?? uncontrolledOpen;
	useWebViewFocusLeave(() => updateOpen(false), isOpen);
	function updateOpen(nextOpen) {
		setUncontrolledOpen(nextOpen);
		onOpenChange?.(nextOpen);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(PopoverRoot$1, {
		modal,
		open: isOpen,
		onOpenChange: updateOpen,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbControlChrome, {
			className,
			compact,
			disabled,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverTrigger, {
				className: "jbReactPopoverTrigger",
				"aria-label": triggerAriaLabel,
				disabled,
				children: trigger
			})
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbPopoverContent, {
			className: contentClassName,
			children
		})]
	});
}
function JbPopoverContent({ className, children, positionerClassName, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverPortal, {
		container: ensureReactControlsPortalRoot(),
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverPositioner, {
			className: ["jbReactPopoverPositioner", positionerClassName].filter(Boolean).join(" "),
			sideOffset: 4,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverPopup, {
				...props,
				className: ["jbReactPopoverPopup", className].filter(Boolean).join(" "),
				children
			})
		})
	});
}
var SelectGroup = SelectGroup$1;
var SelectGroupLabel = SelectGroupLabel$1;
function JbSelect({ children, className, compact, contentClassName, disabled, icon, invalid, modal = false, onOpenChange, onValueChange, open, options = [], placeholder, triggerAriaLabel, ...props }) {
	const [uncontrolledOpen, setUncontrolledOpen] = (0, import_react.useState)(false);
	const isOpen = open ?? uncontrolledOpen;
	useWebViewFocusLeave(() => updateOpen(false), isOpen);
	function updateOpen(nextOpen) {
		setUncontrolledOpen(nextOpen);
		onOpenChange?.(nextOpen);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectRoot$1, {
		...props,
		disabled,
		modal,
		open: isOpen,
		onOpenChange: updateOpen,
		onValueChange: (value) => onValueChange?.(value),
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbControlChrome, {
			className,
			compact,
			disabled,
			invalid,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectTrigger, {
				className: "jbReactSelectTrigger",
				"aria-label": triggerAriaLabel,
				"aria-invalid": invalid ? "true" : void 0,
				children: [
					icon ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "jbReactSelectIcon",
						"aria-hidden": "true",
						children: icon
					}) : null,
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectValue$1, {
						className: "jbReactSelectValue",
						placeholder
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectIcon, {
						className: "jbReactSelectIndicator",
						"aria-hidden": "true",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectChevron, {})
					})
				]
			})
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectContent, {
			className: contentClassName,
			children: children ?? options.map((option) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
				value: option.value,
				disabled: option.disabled,
				label: option.textValue,
				children: option.label
			}, option.value))
		})]
	});
}
function JbSelectContent({ className, children, positionerClassName, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectPortal, {
		container: ensureReactControlsPortalRoot(),
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectPositioner, {
			className: ["jbReactSelectPositioner", positionerClassName].filter(Boolean).join(" "),
			sideOffset: 4,
			alignItemWithTrigger: false,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectPopup, {
				...props,
				className: ["jbReactSelectPopup", className].filter(Boolean).join(" "),
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectScrollUpArrow, {
						className: "jbReactSelectScrollButton",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectChevron, { direction: "up" })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectList, {
						className: "jbReactSelectList",
						children
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectScrollDownArrow, {
						className: "jbReactSelectScrollButton",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectChevron, {})
					})
				]
			})
		})
	});
}
function JbSelectItem({ className, children, ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectItem, {
		...props,
		className: ["jbReactSelectItem", className].filter(Boolean).join(" "),
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "jbReactSelectItemIndicator",
			"aria-hidden": "true",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectItemIndicator, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectCheckIcon, {}) })
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectItemText, { children })]
	});
}
function JbSelectSeparator(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Separator, {
		...props,
		className: ["jbReactSelectSeparator", props.className].filter(Boolean).join(" ")
	});
}
function SelectChevron({ direction = "down" }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		className: `jbReactSelectChevron jbReactSelectChevron--${direction}`,
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M3 4.5L6 7.5L9 4.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
function SelectCheckIcon() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("svg", {
		width: "12",
		height: "12",
		viewBox: "0 0 12 12",
		"aria-hidden": "true",
		focusable: "false",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("path", {
			d: "M2.5 6L5 8.5L9.5 3.5",
			fill: "none",
			stroke: "currentColor",
			strokeWidth: "1.5",
			strokeLinecap: "round",
			strokeLinejoin: "round"
		})
	});
}
var TooltipProvider = TooltipProvider$1;
function JbTooltip({ children, className, contentClassName, disabled, onOpenChange, open, side = "top", trigger }) {
	const [uncontrolledOpen, setUncontrolledOpen] = (0, import_react.useState)(false);
	const isOpen = open ?? uncontrolledOpen;
	useWebViewFocusLeave(() => updateOpen(false), isOpen);
	function updateOpen(nextOpen) {
		setUncontrolledOpen(nextOpen);
		onOpenChange?.(nextOpen);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TooltipRoot$1, {
		disabled,
		open: isOpen,
		onOpenChange: updateOpen,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TooltipTrigger, {
			className: ["jbReactTooltipTrigger", className].filter(Boolean).join(" "),
			disabled,
			children: trigger
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbTooltipContent, {
			className: contentClassName,
			side,
			children
		})]
	});
}
function JbTooltipContent({ className, children, positionerClassName, side = "top", ...props }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TooltipPortal, {
		container: ensureReactControlsPortalRoot(),
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TooltipPositioner, {
			className: ["jbReactTooltipPositioner", positionerClassName].filter(Boolean).join(" "),
			side,
			sideOffset: 4,
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TooltipPopup, {
				...props,
				className: ["jbReactTooltipPopup", className].filter(Boolean).join(" "),
				children
			})
		})
	});
}
var root = document.getElementById("root");
if (!root) throw new Error("#root missing");
var projectScopes = [
	{
		value: "project",
		label: "Project"
	},
	{
		value: "module",
		label: "Module"
	},
	{
		value: "file",
		label: "Current file"
	}
];
var uiDslItems = [
	"Project",
	"Module",
	"File"
].map(toOption);
var densityItems = [
	"Default",
	"Compact",
	"Toolbar"
].map(toOption);
var statusItems = [
	"Problems",
	"Preview",
	"Events"
].map(toOption);
var buildItems = [
	"Incremental",
	"Full",
	"Rebuild"
].map(toOption);
function useItemsControl(id, items, value) {
	(0, import_react.useEffect)(() => {
		const element = document.getElementById(id);
		if (!element) return;
		element.items = items;
		element.value = value;
	}, [
		id,
		items,
		value
	]);
}
function toOption(value) {
	return {
		value: value.toLowerCase().replace(/\s+/g, "-"),
		label: value
	};
}
function ReactControlsShowcase() {
	const [scope, setScope] = (0, import_react.useState)("project");
	const [runtime, setRuntime] = (0, import_react.useState)("jbr");
	const [inspection, setInspection] = (0, import_react.useState)("syntax");
	const [autoSave, setAutoSave] = (0, import_react.useState)(true);
	const [highlightMode, setHighlightMode] = (0, import_react.useState)("changed");
	const [lastAction, setLastAction] = (0, import_react.useState)("No menu action yet");
	useItemsControl("react-lit-select", uiDslItems, "project");
	useItemsControl("react-lit-combobox", uiDslItems, "module");
	useItemsControl("react-lit-menu-button", buildItems, "incremental");
	useItemsControl("react-lit-dropdown-link", buildItems, "full");
	useItemsControl("react-lit-radio-group", densityItems, "default");
	useItemsControl("react-lit-segmented", densityItems, "compact");
	useItemsControl("react-lit-tabs", statusItems, "problems");
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TooltipProvider, {
		delay: 250,
		closeDelay: 80,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "reactShowcaseShell",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
				className: "reactShowcaseHeader",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", { children: "React controls" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "React consumption parity for the framework-neutral jb-* controls, plus Base UI-backed composite controls." })]
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "reactShowcaseGrid",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel reactShowcaseWidePanel",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
								className: "reactShowcasePanelTitle",
								children: "Basic actions and toolbar controls from jb-*"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseButtonRow",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
										variant: "primary",
										children: "Run"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", { children: "Cancel" }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
										variant: "danger",
										children: "Delete"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
										variant: "link",
										children: "Open Settings"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
										size: "small",
										children: "Small"
									})
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseToolbarRow",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
										label: "Back",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
											className: "reactShowcaseIcon reactShowcaseIconBack",
											"aria-hidden": "true"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
										label: "Refresh",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
											className: "reactShowcaseIcon reactShowcaseIconRefresh",
											"aria-hidden": "true"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
										label: "Pinned",
										selected: true,
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
											className: "reactShowcaseIcon reactShowcaseIconPin",
											"aria-hidden": "true"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-menu-button", {
										id: "react-lit-menu-button",
										label: "Build"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-dropdown-link", {
										id: "react-lit-dropdown-link",
										label: "Profile"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-context-help", { text: "Context help is a framework-neutral jb-* control consumed directly from React." }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
										className: "reactShowcaseIconText",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-icon", {
											label: "Settings",
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
												className: "reactShowcaseIcon reactShowcaseIconSettings",
												"aria-hidden": "true"
											})
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text", { children: "Icon" })]
									})
								]
							})
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel reactShowcaseWidePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Fields, labels, help text, and text inputs from jb-*"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseTwoColumns",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseFormStack",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Name:",
										help: "Text field with label and help text.",
										required: true,
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "WebView demo" })
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Password:",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-password-field", { value: "secret" })
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Arguments:",
										warning: "Use warning state for recoverable configuration issues.",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-area", {
											value: "--stacktrace",
											rows: "3"
										})
									})
								]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseFormStack",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Scope:",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-select", {
											id: "react-lit-select",
											value: "project"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Chooser:",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-combobox", {
											id: "react-lit-combobox",
											value: "module"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Expandable:",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-expandable-text-field", { value: "-Didea.is.internal=true" })
									})
								]
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel reactShowcaseWidePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Choice, numeric, and range controls from jb-*"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseTwoColumns",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseFormStack",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
										checked: true,
										children: "Enable preview"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
										indeterminate: true,
										children: "Partial selection"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
										disabled: true,
										children: "Disabled option"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio", {
										name: "react-standalone-radio",
										value: "one",
										checked: true,
										children: "Standalone radio"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio-group", {
										id: "react-lit-radio-group",
										label: "Density",
										value: "default"
									})
								]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseFormStack",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Port:",
										error: "Invalid values are rendered through shared field chrome.",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-number-field", {
											invalid: true,
											value: "99",
											min: "1024",
											max: "65535"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Workers:",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-spinner", {
											value: "4",
											min: "1",
											max: "16"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field", {
										label: "Memory:",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-slider", {
											value: "64",
											min: "0",
											max: "128"
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-segmented-control", {
										id: "react-lit-segmented",
										value: "compact"
									})
								]
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel reactShowcaseWidePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Structure, text, tabs, and disclosure from jb-*"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseTwoColumns",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-field-group", {
								label: "Build options",
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "reactShowcaseFieldGroupBody",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-label", {
											required: true,
											children: "Target:"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-help-text", { children: "Labels, help text, and regular text are framework-neutral custom elements." }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text", {
											weight: "medium",
											children: "intellij.platform.ui.webview.demo"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text", {
											tone: "muted",
											children: "Muted secondary text"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-help-text", {
											tone: "warning",
											children: "Warning help text"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-help-text", {
											tone: "error",
											children: "Error help text"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-separator", {}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-disclosure", {
											label: "Advanced options",
											open: true,
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
												className: "reactShowcaseDisclosureBody",
												children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
													checked: true,
													children: "Use remote cache"
												}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "--keep-going" })]
											})
										})
									]
								})
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseFormStack",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-tabs", {
									id: "react-lit-tabs",
									value: "problems",
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "reactShowcaseTabBody",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text", {
											weight: "medium",
											children: "Problems"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text", {
											tone: "muted",
											children: "The active tab controls the content area."
										})]
									})
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "reactShowcaseSeparatorBlock",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Before separator" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-separator", {}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "After separator" })
									]
								})]
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Select"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseFormStack",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
								className: "reactShowcaseField",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Scope:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelect, {
									value: scope,
									onValueChange: (value) => setScope(value ?? "project"),
									options: projectScopes,
									triggerAriaLabel: "Scope"
								})]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
								className: "reactShowcaseField",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Runtime:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(JbSelect, {
									value: runtime,
									onValueChange: (value) => setRuntime(value ?? "jbr"),
									triggerAriaLabel: "Runtime",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectGroup, { children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectGroupLabel, {
												className: "reactShowcaseGroupLabel",
												children: "Bundled"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "jbr",
												label: "JetBrains Runtime",
												children: "JetBrains Runtime"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "webview2",
												label: "WebView2",
												children: "WebView2"
											})
										] }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectSeparator, {}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(SelectGroup, { children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SelectGroupLabel, {
												className: "reactShowcaseGroupLabel",
												children: "External"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "system",
												label: "System browser",
												children: "System browser"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "legacy",
												label: "Legacy engine",
												disabled: true,
												children: "Legacy engine"
											})
										] })
									]
								})]
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Menu"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseFormStack",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "reactShowcaseInlineControls",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(JbMenuButton, {
									label: "Actions",
									triggerAriaLabel: "Actions menu",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuItem, {
											shortcut: "Ctrl+R",
											onClick: () => setLastAction("Run inspection"),
											children: "Run inspection"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuItem, {
											shortcut: "Ctrl+Alt+L",
											onClick: () => setLastAction("Reformat selection"),
											children: "Reformat selection"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuItem, {
											disabled: true,
											children: "Attach debugger"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuSeparator, {}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuCheckboxItem, {
											checked: autoSave,
											onCheckedChange: setAutoSave,
											children: "Auto-save results"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuSeparator, {}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuGroup, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuGroupLabel, {
											className: "reactShowcaseGroupLabel",
											children: "Highlight"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(MenuRadioGroup, {
											value: highlightMode,
											onValueChange: setHighlightMode,
											children: [
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuRadioItem, {
													value: "changed",
													children: "Changed files"
												}),
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuRadioItem, {
													value: "all",
													children: "All files"
												}),
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuRadioItem, {
													value: "none",
													children: "None"
												})
											]
										})] })
									]
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(JbMenuButton, {
									label: "Compact",
									compact: true,
									triggerAriaLabel: "Compact menu",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuItem, {
										onClick: () => setLastAction("Compact menu item"),
										children: "Compact action"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbMenuItem, {
										disabled: true,
										children: "Disabled action"
									})]
								})]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
								className: "reactShowcaseStatus",
								children: [
									lastAction,
									"; auto-save ",
									autoSave ? "on" : "off",
									"; highlight ",
									highlightMode
								]
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Popover"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseInlineControls",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(JbPopover, {
								trigger: "Build details",
								triggerAriaLabel: "Build details",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverTitle, { children: "Build details" }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverDescription, { children: "Popover content can hold richer controls while sharing portal and focus-leave behavior." }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "reactShowcasePopoverRows",
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Target" }),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: "@community//plugins/ui.webview/demo:demo" }),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Status" }),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: "Up to date" })
										]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverClose, {
										className: "reactShowcaseTextButton",
										children: "Close"
									})
								]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(JbPopover, {
								trigger: "?",
								compact: true,
								triggerAriaLabel: "Compact popover",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverTitle, { children: "Compact trigger" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PopoverDescription, { children: "The focus ring still belongs to the shared chrome." })]
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "Tooltip"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseInlineControls",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbTooltip, {
									trigger: "?",
									children: "Tooltip popup in the shared portal root."
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbTooltip, {
									trigger: "Top",
									side: "top",
									children: "Tooltip above the trigger."
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbTooltip, {
									trigger: "Right",
									side: "right",
									children: "Tooltip on the right side."
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbTooltip, {
									trigger: "Disabled",
									disabled: true,
									children: "Disabled tooltip does not open."
								})
							]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "reactShowcasePanel",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "reactShowcasePanelTitle",
							children: "States and chrome"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "reactShowcaseFormStack",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
									className: "reactShowcaseField",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Compact:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(JbSelect, {
										value: inspection,
										onValueChange: (value) => setInspection(value ?? "syntax"),
										compact: true,
										triggerAriaLabel: "Inspection",
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "syntax",
												label: "Syntax",
												children: "Syntax"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "semantic",
												label: "Semantic",
												children: "Semantic"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelectItem, {
												value: "whole-project",
												label: "Whole project",
												children: "Whole project"
											})
										]
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
									className: "reactShowcaseField",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Disabled:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelect, {
										value: "locked",
										disabled: true,
										options: [{
											value: "locked",
											label: "Locked by host state"
										}],
										triggerAriaLabel: "Disabled state"
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
									className: "reactShowcaseField",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Invalid:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbSelect, {
										value: "missing",
										invalid: true,
										options: [{
											value: "missing",
											label: "Missing SDK"
										}],
										triggerAriaLabel: "Invalid state"
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
									className: "reactShowcaseField",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Chrome:" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(JbControlChrome, {
										className: "reactShowcaseChromeSample",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "Custom trigger surface" })
									})]
								})
							]
						})]
					})
				]
			})]
		})
	});
}
(0, import_client.createRoot)(root).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ReactControlsShowcase, {}));
