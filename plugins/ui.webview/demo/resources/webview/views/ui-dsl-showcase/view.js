import { n as __toESM } from "./assets/rolldown-runtime.js";
import { i, n as A, r as b, t as i$1 } from "./assets/lit.js";
import { n as require_react, t as require_jsx_runtime } from "./assets/react.js";
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
var WEBVIEW_FOCUS_LEAVE_EVENT = "wvi-focus-leave";
var WebViewFocusLeaveController = class {
	onFocusLeave;
	listener = () => this.onFocusLeave();
	constructor(host, onFocusLeave) {
		this.onFocusLeave = onFocusLeave;
		host.addController(this);
	}
	hostConnected() {
		window.addEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, this.listener);
	}
	hostDisconnected() {
		window.removeEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, this.listener);
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
var JbMenuButton = class extends i$1 {
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
var JbDropdownLink = class extends JbMenuButton {
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
var JbSelect = class extends i$1 {
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
	"jb-menu-button": JbMenuButton,
	"jb-number-field": JbNumberField,
	"jb-password-field": JbPasswordField,
	"jb-radio": JbRadio,
	"jb-radio-group": JbRadioGroup,
	"jb-segmented-control": JbSegmentedControl,
	"jb-select": JbSelect,
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
function defineWebViewNotification(method) {
	return { method };
}
apiId()("webview.theme");
apiId()("webview.theme");
function getWebViewTheme() {
	return window.__WVI_THEME__;
}
function requireWebViewTheme() {
	const theme = getWebViewTheme();
	if (!theme) throw new Error("WebView theme is not installed. Load /__webview/wvi-platform-features.js after /__webview/wvi-bridge.js before theme-aware application code.");
	return theme;
}
function createLazyWebViewTheme() {
	return new Proxy({}, {
		get(_target, property, receiver) {
			return Reflect.get(requireWebViewTheme(), property, receiver);
		},
		set(_target, property, value, receiver) {
			return Reflect.set(requireWebViewTheme(), property, value, receiver);
		},
		has(_target, property) {
			return property in requireWebViewTheme();
		}
	});
}
var webViewTheme = createLazyWebViewTheme();
var IconSet = /* @__PURE__ */ Object.freeze({ define(id) {
	validateIconSetId(id);
	return new DefinedIconSet(id);
} });
var DefinedIconSet = class {
	id;
	constructor(id) {
		this.id = id;
	}
	src(resourcePath) {
		validateIconResourcePath(resourcePath);
		return `./__ij-icons/${this.id}/${webViewTheme.current}/${encodeIconResourcePath(resourcePath)}`;
	}
};
var AllIcons = /* @__PURE__ */ IconSet.define("AllIcons");
function validateIconSetId(id) {
	if (!/^[A-Za-z][A-Za-z0-9._-]*$/.test(id)) throw new Error(`Invalid WebView icon set id: ${id}`);
}
function validateIconResourcePath(resourcePath) {
	if (resourcePath.length === 0 || resourcePath.startsWith("/") || resourcePath.includes("\\")) throw new Error(`Invalid WebView icon resource path: ${resourcePath}`);
	if (/^[A-Za-z][A-Za-z0-9+.-]*:/.test(resourcePath)) throw new Error(`Invalid WebView icon resource path: ${resourcePath}`);
	if (resourcePath.split("/").some((segment) => segment.length === 0 || segment === "." || segment === "..")) throw new Error(`Invalid WebView icon resource path: ${resourcePath}`);
	if (!resourcePath.endsWith(".svg") && !resourcePath.endsWith(".png")) throw new Error(`Unsupported WebView icon resource extension: ${resourcePath}`);
}
function encodeIconResourcePath(resourcePath) {
	return resourcePath.split("/").map((segment) => encodeURIComponent(segment)).join("/");
}
apiId()("webview.focus");
apiId()("webview.focus");
function getWebViewBridge() {
	return window.__WVI__;
}
function requireWebViewBridge() {
	const bridge = getWebViewBridge();
	if (!bridge) throw new Error("WebView bridge is not installed. Load /__webview/wvi-bridge.js before application code.");
	return bridge;
}
function createLazyWebViewBridge() {
	return new Proxy({}, {
		get(_target, property, receiver) {
			return Reflect.get(requireWebViewBridge(), property, receiver);
		},
		set(_target, property, value, receiver) {
			return Reflect.set(requireWebViewBridge(), property, value, receiver);
		},
		has(_target, property) {
			return property in requireWebViewBridge();
		}
	});
}
var webView = createLazyWebViewBridge();
var import_jsx_runtime = require_jsx_runtime();
var root = document.getElementById("root");
if (!root) throw new Error("#root missing");
var openSourceNotification = defineWebViewNotification("demo/uiDslShowcase/openSource");
var icons = {
	add: AllIcons.src("expui/general/add.svg"),
	bulb: AllIcons.src("expui/codeInsight/quickfixOffBulb.svg"),
	expand: AllIcons.src("expui/inline/expand.svg"),
	externalLink: AllIcons.src("expui/ide/externalLink.svg"),
	folder: AllIcons.src("expui/nodes/folder.svg"),
	gear: AllIcons.src("expui/general/moreVertical.svg"),
	info: AllIcons.src("expui/status/info.svg")
};
var item1to3 = [
	"Item 1",
	"Item 2",
	"Item 3"
].map(toOption);
var singleLineRadioItems = [
	"Option 1",
	"Option 2",
	"Option 3"
].map(toOption);
var bindValueItems = [{
	value: "value-1",
	label: "Value = 1"
}, {
	value: "value-2",
	label: "Value = 2"
}];
var componentsRadioItems = [
	{
		value: "value-1",
		label: "Value 1"
	},
	{
		value: "value-2",
		label: "Value 2"
	},
	{
		value: "value-3",
		label: "Value 3"
	}
];
var actionsItems = [{
	value: "action-1",
	label: "Action 1"
}, {
	value: "action-2",
	label: "Action 2"
}];
var tabItems = [
	{
		value: "tab-1",
		label: "Tab 1"
	},
	{
		value: "tab-2",
		label: "Tab 2"
	},
	{
		value: "last-tab",
		label: "Last Tab"
	}
];
var comboItems = [{
	value: "item-1",
	label: "Item 1"
}, {
	value: "item-2",
	label: "Item 2"
}];
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
function toOption(label) {
	return {
		label,
		value: label.toLowerCase().replace(/\s+/g, "-")
	};
}
function boolAttr(value) {
	return value ? "" : void 0;
}
function eventValue(event) {
	return event.detail?.value ?? "";
}
function clamp(value, min, max) {
	return Math.min(max, Math.max(min, value));
}
function formatDecimal(value) {
	return value.toFixed(2);
}
function notifyOpenSource() {
	try {
		webView.notification(openSourceNotification).send({});
	} catch (error) {
		console.warn("UI DSL showcase source navigation is unavailable", error);
	}
}
function Icon(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-icon", {
		className: props.className ?? "dslSmallIcon",
		src: props.src,
		label: props.label ?? ""
	});
}
function DslSection(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
		className: "dslSection",
		"aria-labelledby": `${props.title.toLowerCase()}-section-title`,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
			className: "dslSectionTitle",
			id: `${props.title.toLowerCase()}-section-title`,
			children: props.title
		}), props.children]
	});
}
function DslGroup(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
		className: "dslGroup",
		"aria-label": props.title,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "dslGroupTitle",
			children: props.title
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "dslRows",
			children: props.children
		})]
	});
}
function DslLabel(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: props.empty ? "dslLabel dslLabelEmpty" : "dslLabel",
		children: props.children
	});
}
function DslCell(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: [
			"dslCell",
			"webviewDemoNoWrapControls",
			props.column ? "dslCellColumn" : "",
			props.top ? "dslCellTop" : "",
			props.className ?? ""
		].filter(Boolean).join(" "),
		children: props.children
	});
}
function DslRightComment(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "dslRightComment webview-selectable-text",
		children: props.children
	});
}
function DslRowComment(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "dslRowComment webview-selectable-text",
		children: props.children
	});
}
function DslInlineHelp(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
		className: "dslInlineHelp",
		children: [props.children, /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-context-help", { text: props.text })]
	});
}
function DslRow(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: [
			"dslRow",
			props.noLabel ? "dslRowNoLabel" : "",
			props.independent ? "dslRowIndependent" : "",
			props.fullWidth ? "dslRowFullWidth" : "",
			props.top ? "dslRowTop" : ""
		].filter(Boolean).join(" "),
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslLabel, {
				empty: props.noLabel,
				children: props.label
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslCell, {
				top: props.top,
				className: props.cellClassName,
				children: props.children
			}),
			props.rightComment ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRightComment, { children: props.rightComment }) : null,
			props.rowComment ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRowComment, { children: props.rowComment }) : null
		]
	});
}
function BrowserLink(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("a", {
		className: "dslBrowserLink",
		href: props.href,
		target: "_blank",
		rel: "noreferrer",
		children: [props.children, /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
			className: "dslExternalIcon",
			src: icons.externalLink,
			label: "External link"
		})]
	});
}
function SegmentedButton(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "dslSegmented",
		role: "radiogroup",
		"aria-label": "Segmented button",
		children: [
			{
				value: "button-1",
				label: "Button 1"
			},
			{
				value: "button-2",
				label: "Button 2"
			},
			{
				value: "button-last",
				label: "Button Last",
				info: true
			}
		].map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
			"aria-checked": props.value === item.value,
			className: props.value === item.value ? "dslSegment isSelected" : "dslSegment",
			onClick: () => props.onChange(item.value),
			role: "radio",
			type: "button",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: item.label }), item.info ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
				src: icons.info,
				label: "Info"
			}) : null]
		}, item.value))
	});
}
function TabbedPaneHeader(props) {
	const activeLabel = tabItems.find((item) => item.value === props.value)?.label ?? "Tab 1";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "dslTabs",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
			className: "dslTabList",
			role: "tablist",
			"aria-label": "Tabbed pane header",
			children: tabItems.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				"aria-selected": props.value === item.value,
				className: props.value === item.value ? "dslTab isSelected" : "dslTab",
				onClick: () => props.onChange(item.value),
				role: "tab",
				type: "button",
				children: item.label
			}, item.value))
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "dslTabPanel",
			role: "tabpanel",
			children: ["Selected: ", activeLabel]
		})]
	});
}
function TextFieldWithBrowseButton() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "dslCompositeField",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
			value: "C:/workspace/project",
			"aria-label": "Path"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
			className: "dslBrowseButton",
			label: "Browse",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
				src: icons.folder,
				label: "Browse"
			})
		})]
	});
}
function ExpandableTextField() {
	const [expanded, setExpanded] = (0, import_react.useState)(false);
	const [value, setValue] = (0, import_react.useState)("one line expandable text");
	(0, import_react.useEffect)(() => {
		const element = document.getElementById("components-expandable-field");
		if (!element) return;
		const listener = (event) => setValue(eventValue(event));
		element.addEventListener("jb-change", listener);
		return () => element.removeEventListener("jb-change", listener);
	}, []);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: expanded ? "dslCompositeFieldExpanded" : "dslCompositeField",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
				id: "components-expandable-field",
				value,
				"aria-label": "Expandable text"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
				className: "dslBrowseButton",
				expanded: boolAttr(expanded),
				label: expanded ? "Collapse" : "Expand",
				onClick: () => setExpanded(!expanded),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
					src: icons.expand,
					label: expanded ? "Collapse" : "Expand"
				})
			}),
			expanded ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-area", {
				className: "dslExpandedTextArea",
				rows: "4",
				value
			}) : null
		]
	});
}
function ExtendableTextField() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "dslCompositeField dslExtendableField",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
			value: "Text with extension",
			"aria-label": "Extendable text"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
			className: "dslExtendButton",
			label: "Add",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
				src: icons.add,
				label: "Add"
			})
		})]
	});
}
function DslSpinner(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "dslSpinner",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-number-field", {
			id: props.id,
			value: props.value,
			min: props.min,
			max: props.max,
			step: props.step
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
			className: "dslSpinnerButtons",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				className: "dslSpinnerButton",
				type: "button",
				"aria-label": "Increment",
				onClick: () => props.onStep(1),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "dslArrowUp",
					"aria-hidden": "true"
				})
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				className: "dslSpinnerButton",
				type: "button",
				"aria-label": "Decrement",
				onClick: () => props.onStep(-1),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "dslArrowDown",
					"aria-hidden": "true"
				})
			})]
		})]
	});
}
function UiDslShowcase() {
	const pageRef = (0, import_react.useRef)(null);
	const [optionEnabled, setOptionEnabled] = (0, import_react.useState)(false);
	const [singleLineRadio, setSingleLineRadio] = (0, import_react.useState)("option-1");
	const [bindValueRadio, setBindValueRadio] = (0, import_react.useState)("value-outside-model");
	const [componentsRadio, setComponentsRadio] = (0, import_react.useState)("value-2");
	const [segmentedValue, setSegmentedValue] = (0, import_react.useState)("button-1");
	const [tabValue, setTabValue] = (0, import_react.useState)("tab-1");
	const [dropdownValue, setDropdownValue] = (0, import_react.useState)("item-1");
	const [comboValue, setComboValue] = (0, import_react.useState)("item-1");
	const [spinnerInt, setSpinnerInt] = (0, import_react.useState)("42");
	const [spinnerDouble, setSpinnerDouble] = (0, import_react.useState)("42.00");
	const [sliderValue, setSliderValue] = (0, import_react.useState)("5");
	const [commentClickStatus, setCommentClickStatus] = (0, import_react.useState)("No comment link clicked");
	const [intTextValue, setIntTextValue] = (0, import_react.useState)("42");
	const [actionsStatus, setActionsStatus] = (0, import_react.useState)("No action selected");
	useItemsControl("examples-single-line-radio", singleLineRadioItems, singleLineRadio);
	useItemsControl("examples-bind-value-radio", bindValueItems, bindValueRadio);
	useItemsControl("components-radio-group", componentsRadioItems, componentsRadio);
	useItemsControl("components-actions-button", actionsItems, "");
	useItemsControl("components-dropdown-link", item1to3, dropdownValue);
	useItemsControl("components-combo-box", comboItems, comboValue);
	const intTextInvalid = (0, import_react.useMemo)(() => {
		if (intTextValue.trim() === "") return true;
		const numeric = Number(intTextValue);
		return Number.isNaN(numeric) || numeric < 0 || numeric > 100;
	}, [intTextValue]);
	(0, import_react.useEffect)(() => {
		const element = pageRef.current;
		if (!element) return;
		const onJbChange = (event) => {
			const target = event.target;
			if (!target) return;
			const value = eventValue(event);
			switch (target.id) {
				case "examples-single-line-radio":
					setSingleLineRadio(value);
					break;
				case "examples-bind-value-radio":
					setBindValueRadio(value);
					break;
				case "examples-option-checkbox":
					setOptionEnabled(value !== "");
					break;
				case "components-radio-group":
					setComponentsRadio(value);
					break;
				case "components-dropdown-link":
					setDropdownValue(value);
					break;
				case "components-combo-box":
					setComboValue(value);
					break;
				case "components-int-field":
					setIntTextValue(normalizeIntegerText(value));
					break;
				case "components-spinner-int":
					setSpinnerInt(normalizeIntegerText(value));
					break;
				case "components-spinner-double":
					setSpinnerDouble(normalizeDecimalText(value));
					break;
				case "components-slider":
					setSliderValue(value);
					break;
			}
		};
		const onJbSelect = (event) => {
			const target = event.target;
			if (!target) return;
			const value = eventValue(event);
			if (target.id === "components-actions-button") setActionsStatus(value === "action-2" ? "Action 2 selected" : "Action 1 selected");
			else if (target.id === "components-dropdown-link") setDropdownValue(value);
		};
		element.addEventListener("jb-change", onJbChange);
		element.addEventListener("jb-select", onJbSelect);
		return () => {
			element.removeEventListener("jb-change", onJbChange);
			element.removeEventListener("jb-select", onJbSelect);
		};
	}, []);
	function stepIntSpinner(direction) {
		setSpinnerInt((current) => String(clamp((Number(current) || 0) + direction, 0, 100)));
	}
	function stepDoubleSpinner(direction) {
		setSpinnerDouble((current) => formatDecimal(clamp((Number(current) || 0) + direction * .01, 0, 100)));
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "uiDslShowcasePage webviewDemoFixedCanvas",
		ref: pageRef,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
			className: "uiDslHeader",
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "uiDslHeaderTitle",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", { children: "UI DSL Showcase" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "WebView implementation of UI DSL Examples, Components, and Comments" })]
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				className: "uiDslSourceLink",
				type: "button",
				onClick: notifyOpenSource,
				children: "View source"
			})]
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "uiDslSections",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ExamplesSection, {
					bindValueRadio,
					optionEnabled
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ComponentsSection, {
					actionsStatus,
					comboValue,
					componentsRadio,
					dropdownValue,
					intTextInvalid,
					intTextValue,
					segmentedValue,
					setSegmentedValue,
					setTabValue,
					sliderValue,
					spinnerDouble,
					spinnerInt,
					stepDoubleSpinner,
					stepIntSpinner,
					tabValue
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(CommentsSection, {
					commentClickStatus,
					setCommentClickStatus
				})
			]
		})]
	});
}
function normalizeIntegerText(value) {
	if (value.trim() === "") return value;
	const numeric = Number(value);
	if (Number.isNaN(numeric)) return value;
	return String(clamp(Math.trunc(numeric), 0, 100));
}
function normalizeDecimalText(value) {
	if (value.trim() === "") return value;
	const numeric = Number(value);
	if (Number.isNaN(numeric)) return value;
	return formatDecimal(clamp(numeric, 0, 100));
}
function ExamplesSection(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslSection, {
		title: "Examples",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslGroup, {
				title: "Initializing components with extension functions",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
						noLabel: true,
						rightComment: "'bold()' works for any component",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "dslTextStrong",
							children: "Bold text"
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
						noLabel: true,
						rightComment: "'selected(true)'",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
							checked: "",
							children: "Selected CheckBox"
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						noLabel: true,
						rightComment: "'selected(true)'",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio", {
							name: "examples-initial-radio",
							value: "radio",
							children: "RadioButton"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio", {
							name: "examples-initial-radio",
							value: "selected",
							checked: "",
							children: "Selected RadioButton"
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
						noLabel: true,
						rightComment: "'text(\"Initial text\")'",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
							className: "dslFieldMedium",
							value: "Initial text"
						})
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslGroup, {
				title: "CheckBox/RadioButton examples",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						label: "CheckBox/RadioButton Group:",
						cellClassName: "dslCellColumn",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "dslControlLine dslIndented webviewDemoNoWrapControls",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
								checked: "",
								children: "CheckBox 1"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Context help popup bound to CheckBox 1." })]
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "dslControlLine dslIndented webviewDemoNoWrapControls",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", { children: "CheckBox 2" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(BrowserLink, {
								href: "https://www.jetbrains.com/help/idea/settings.html",
								children: "How it works"
							})]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
						label: "",
						noLabel: true,
						rightComment: "External links must be marked with the external link icon",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { "aria-hidden": "true" })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						label: "Single line:",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio-group", {
							id: "examples-single-line-radio",
							value: "option-1"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Radio buttons in one row are mutually exclusive." })]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
						label: "Bind value:",
						rightComment: "'buttonsGroup.bind'",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio-group", {
							id: "examples-bind-value-radio",
							value: props.bindValueRadio
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						label: "Option:",
						rightComment: "'enabledIf'",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
								id: "examples-option-checkbox",
								checked: boolAttr(props.optionEnabled),
								children: "Option"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
								className: "dslFieldMedium",
								disabled: boolAttr(!props.optionEnabled),
								value: "Enabled by option"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "The text field follows the checkbox state live." })
						]
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslGroup, {
				title: "TextField",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						label: "Default field:",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
							className: "dslFieldMedium",
							value: "Text"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Default text field with context help." })]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						noLabel: true,
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
							className: "dslFieldFillHost",
							value: "Text field fills the remaining row width"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Empty label row keeps shared alignment." })]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
						label: "Don't align very long labels with short ones:",
						independent: true,
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
								className: "dslFieldMedium",
								value: "15"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "seconds" }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Independent rows can opt out of the shared label column." })
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "dslParentGrid",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
							label: "Row 1:",
							rightComment: "RowLayout.PARENT_GRID",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "Parent grid field" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
									className: "dslEqualButton dslTextButton",
									children: "Test"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Rows share the same inner column sizing." })
							]
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
							label: "Row 2:",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "Second parent grid field" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "dslReservedButtonSlot",
								"aria-hidden": "true"
							})]
						})]
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslGroup, {
				title: "Comments",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
					noLabel: true,
					rightComment: "Requires restart",
					rowComment: "It's important to connect comments to the related cells: they are displayed in the correct location with appropriate styling and used by the accessibility framework",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", { children: "A very complex option" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Context help belongs to the option cell." })]
				})
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslGroup, {
				title: "Buttons",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslRow, {
					noLabel: true,
					rightComment: "'widthGroup'",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
							className: "dslEqualButton dslTextButton",
							variant: "primary",
							children: "Default Button"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", {
							className: "dslEqualButton dslTextButton",
							children: "Button"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslInlineHelp, { text: "Width group keeps related buttons equal." })
					]
				})
			})
		]
	});
}
function ComponentsSection(props) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslSection, {
		title: "Components",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslGroup, {
			title: "Basic components",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "checkBox:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
						checked: "",
						children: "checkBox"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "threeStateCheckBox:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-checkbox", {
						indeterminate: "",
						children: "threeStateCheckBox"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "radioButton:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-radio-group", {
						id: "components-radio-group",
						value: props.componentsRadio
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "button:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-button", { children: "button" })
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "actionButton:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-action-button", {
						className: "dslIconButton",
						label: "Quick fix off bulb",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
							src: icons.bulb,
							label: "Quick fix off bulb"
						})
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "actionsButton:",
					rowComment: props.actionsStatus,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-menu-button", {
						id: "components-actions-button",
						label: "Actions",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
							src: icons.gear,
							label: "Actions"
						})
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "segmentedButton:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SegmentedButton, {
						value: props.segmentedValue,
						onChange: props.setSegmentedValue
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "tabbedPaneHeader:",
					top: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TabbedPaneHeader, {
						value: props.tabValue,
						onChange: props.setTabValue
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "label:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-label", { children: "label" })
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "text:",
					top: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
						className: "dslCommonInfo",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
							className: "dslCommonComment",
							children: [
								"Regular text with ",
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
									className: "dslTextLink",
									type: "button",
									children: "link"
								}),
								", line break,",
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("br", {}),
								"and ",
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
									src: icons.info,
									label: "Info"
								}),
								" bundled info icon."
							]
						})
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "link:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						className: "dslTextLink",
						type: "button",
						children: "Focusable link"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "browserLink:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(BrowserLink, {
						href: "https://www.jetbrains.com/help/idea/",
						children: "Browser link"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "dropDownLink:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-dropdown-link", {
						id: "components-dropdown-link",
						label: "Item 1",
						value: props.dropdownValue
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "icon:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
						className: "dslInlineIconText",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
							src: icons.info,
							label: "Info"
						}), " icon"]
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "contextHelp:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-context-help", { text: "Context help related to the component, displayed in a popup" })
				})
			]
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslGroup, {
			title: "Input components",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "textField:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
						className: "dslFieldMedium",
						value: "text"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "passwordField:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-password-field", {
						className: "dslFieldMedium",
						value: "password"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "textFieldWithBrowseButton:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextFieldWithBrowseButton, {})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "expandableTextField:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ExpandableTextField, {})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "extendableTextField:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ExtendableTextField, {})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "intTextField(0..100):",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-number-field", {
						className: "dslFieldShort",
						id: "components-int-field",
						invalid: boolAttr(props.intTextInvalid),
						max: "100",
						min: "0",
						value: props.intTextValue
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "spinner(0..100):",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslSpinner, {
						id: "components-spinner-int",
						value: props.spinnerInt,
						min: 0,
						max: 100,
						step: 1,
						onStep: props.stepIntSpinner
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "spinner(0.0..100.0, 0.01):",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslSpinner, {
						id: "components-spinner-double",
						value: props.spinnerDouble,
						min: 0,
						max: 100,
						step: .01,
						onStep: props.stepDoubleSpinner
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "slider(0, 10, 1, 5):",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "dslSliderBlock",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-slider", {
							id: "components-slider",
							min: "0",
							max: "10",
							step: "1",
							value: props.sliderValue
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "dslSliderLabels",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "0" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "5" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "10" })
							]
						})]
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "textArea:",
					top: true,
					fullWidth: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-area", {
						className: "dslTextAreaWide",
						rows: "5",
						value: "Text area\\nwith several lines\\nand top-aligned label"
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "comboBox:",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-select", {
						className: "dslSelectMedium",
						id: "components-combo-box",
						value: props.comboValue
					})
				})
			]
		})]
	});
}
function CommentsSection(props) {
	const longString = "A very long string ".repeat(16).trim();
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslSection, {
		title: "Comments",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DslGroup, {
				title: "Cell Comment",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					fullWidth: true,
					noLabel: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "dslArbitraryComment webview-selectable-text",
						children: "Comments related to a cell must be assigned directly to that cell. This ensures proper layout placement and improves support for the accessibility framework"
					})
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "Cells:",
					top: true,
					fullWidth: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "dslThreeFields",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "dslCellInlineComment",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "textField1" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "dslRightComment webview-selectable-text",
									children: "Right comment to textField1"
								})]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "dslCellBlock",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "textField2" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "dslBottomComment webview-selectable-text",
									children: "Bottom comment to textField2"
								})]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "dslControlLine webviewDemoNoWrapControls",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", { value: "textField3" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-context-help", { text: "Context help related to the component, displayed in a popup" })]
							})
						]
					})
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslGroup, {
				title: "Row Comment",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					label: "Text field:",
					rowComment: "A row comment is placed below the row",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("jb-text-field", {
						className: "dslFieldMedium",
						value: "textField"
					})
				})
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslGroup, {
				title: "Arbitrary Comment",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					fullWidth: true,
					noLabel: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "dslArbitraryComment webview-selectable-text",
						children: "Arbitrary comments can be placed anywhere. They are not related to any cell or row"
					})
				})
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslGroup, {
				title: "Common Info",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DslRow, {
					top: true,
					fullWidth: true,
					noLabel: true,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "dslCommonInfo webview-selectable-text",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
								className: "dslCommonComment",
								children: [
									"Comments can be an html text with some clickable\xA0",
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
										className: "dslTextLink",
										type: "button",
										onClick: () => props.setCommentClickStatus("First comment link clicked"),
										children: "link"
									}),
									"\xA0and even several\xA0",
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
										className: "dslTextLink",
										type: "button",
										onClick: () => props.setCommentClickStatus("Second comment link clicked"),
										children: "links"
									}),
									"."
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
								className: "dslStatusText",
								"aria-live": "polite",
								children: props.commentClickStatus
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
								className: "dslCommonComment dslInfoLine",
								children: [
									"It's possible to use line breaks and bundled icons",
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("br", {}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Icon, {
										src: icons.info,
										label: "Info"
									}),
									" bundled info icon"
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
								className: "dslCommonComment dslWrappedComment",
								children: longString
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
								className: "dslCommonComment dslNoWrapComment",
								children: longString
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
								className: "dslCommonComment dslMaxLineComment",
								children: longString
							})
						]
					})
				})
			})
		]
	});
}
(0, import_client.createRoot)(root).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(UiDslShowcase, {}));
