import { i, n as A, r as b, t as i$1 } from "./assets/lit.js";
//#region \0vite/modulepreload-polyfill.js
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
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/aria.ts
function boolAttribute(value) {
	return value ? "true" : A;
}
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/define.ts
function defineControl(tagName, constructor, registry = customElements) {
	if (!registry.get(tagName)) registry.define(tagName, constructor);
}
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/styles.ts
var hostStyles = i`
  :host {
    box-sizing: border-box;
    color: var(--jb-text-color);
    font-family: var(--jb-font-family);
    font-size: var(--jb-font-size);
    line-height: var(--jb-line-height);
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
    min-height: var(--jb-control-height);
    min-width: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    position: relative;
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

  .icon-slot,
  .chevron {
    align-items: center;
    display: inline-flex;
    justify-content: center;
    line-height: 1;
  }

  .chevron {
    color: var(--jb-text-muted);
    font-size: var(--jb-font-size-small);
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

  .field-control:focus-visible,
  .textarea:focus-visible,
  .select:focus-visible {
    border-color: var(--jb-accent-color);
    box-shadow: var(--jb-focus-ring);
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
  }

  .select-wrap::after {
    color: var(--jb-text-muted);
    content: "v";
    font-size: var(--jb-font-size-small);
    pointer-events: none;
    position: absolute;
    right: 9px;
    top: 50%;
    transform: translateY(-52%);
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
  .choice {
    align-items: flex-start;
    color: var(--jb-text-color);
    display: inline-flex;
    gap: var(--jb-control-gap);
    min-height: var(--jb-control-height-compact);
    position: relative;
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

  .checkbox .native-check:checked + .mark::before {
    content: "";
    border: solid currentColor;
    border-width: 0 2px 2px 0;
    height: 8px;
    margin-top: -1px;
    transform: rotate(45deg);
    width: 4px;
  }

  .checkbox .native-check:indeterminate + .mark::before {
    background: currentColor;
    content: "";
    height: 2px;
    width: 8px;
  }

  .radio .native-check:checked + .mark::before {
    background: currentColor;
    border-radius: 50%;
    content: "";
    height: 6px;
    width: 6px;
  }
`;
//#endregion
//#region ../../webview-src/packages/controls/src/elements/action-button/action-button.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/button/button.ts
var JbButton = class extends i$1 {
	static properties = {
		disabled: {
			type: Boolean,
			reflect: true
		},
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
        <span part="icon" class="icon-slot"><slot name="icon"></slot></span>
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
};
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/events.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/checkbox/checkbox.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/focus.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/foundation/options.ts
function normalizeOptions(options) {
	return Array.isArray(options) ? options : [];
}
function optionLabel(options, value, placeholder = "") {
	return options.find((option) => option.value === value)?.label ?? placeholder;
}
//#endregion
//#region ../../webview-src/packages/controls/src/elements/text-field/text-input-base.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/combobox/combobox.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/context-help/context-help.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/disclosure/disclosure.ts
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
    }
  `
	];
	disabled = false;
	label = "";
	open = false;
	render() {
		return b`
      <button part="summary" class="button link" type="button" ?disabled=${this.disabled} aria-expanded=${String(this.open)} @click=${() => this.open = !this.open}>
        <span part="chevron" class="chevron">${this.open ? "v" : ">"}</span>
        <span part="label"><slot name="summary">${this.label}</slot></span>
      </button>
      ${this.open ? b`<div part="content" class="content"><slot></slot></div>` : A}
    `;
	}
};
//#endregion
//#region ../../webview-src/packages/controls/src/elements/menu-button/menu-button.ts
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
          <span part="chevron" class="chevron">v</span>
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/dropdown-link/dropdown-link.ts
var JbDropdownLink = class extends JbMenuButton {
	variant = "link";
};
//#endregion
//#region ../../webview-src/packages/controls/src/elements/expandable-text-field/expandable-text-field.ts
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
        <button part="expand-button" class="button toolbar" type="button" ?disabled=${this.disabled} @click=${() => this.expanded = !this.expanded}>${this.expanded ? "-" : "+"}</button>
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/field/field.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/field-group/field-group.ts
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
    }

    .body {
      display: grid;
      gap: var(--jb-space-sm);
    }
  `];
	disabled = false;
	label = "";
	render() {
		return b`<fieldset part="group" ?disabled=${this.disabled}>${this.label ? b`<legend part="label">${this.label}</legend>` : A}<div part="body" class="body"><slot></slot></div></fieldset>`;
	}
};
//#endregion
//#region ../../webview-src/packages/controls/src/elements/help-text/help-text.ts
var JbHelpText = class extends i$1 {
	static properties = { tone: {
		type: String,
		reflect: true
	} };
	static styles = [hostStyles, i`
    .help {
      color: var(--jb-text-muted);
      line-height: var(--jb-line-height-paragraph);
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/icon/icon.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/label/label.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/number-field/number-field.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/password-field/password-field.ts
var JbPasswordField = class extends TextInputBase {
	get inputType() {
		return "password";
	}
};
//#endregion
//#region ../../webview-src/packages/controls/src/elements/radio/radio.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/radio-group/radio-group.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/segmented-control/segmented-control.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/select/select.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/separator/separator.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/slider/slider.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/spinner/spinner.ts
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

    .step-button {
      padding-inline: var(--jb-space-xs);
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
        <button part="decrement-button" class="button toolbar step-button" type="button" ?disabled=${this.disabled} @click=${() => this.stepValue(-1)}>-</button>
        <button part="increment-button" class="button toolbar step-button" type="button" ?disabled=${this.disabled} @click=${() => this.stepValue(1)}>+</button>
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/tabs/tabs.ts
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
      <div part="panel"><slot></slot></div>
    `;
	}
	selectOption(option) {
		if (this.disabled || option.disabled || this.value === option.value) return;
		this.value = option.value;
		emitStandardEvent(this, "change");
		emitValueEvent(this, "jb-change", this.value);
	}
};
//#endregion
//#region ../../webview-src/packages/controls/src/elements/text/text.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/text-area/text-area.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/text-field/text-field.ts
var JbTextField = class extends TextInputBase {};
//#endregion
//#region ../../webview-src/packages/controls/src/tokens.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/elements/index.ts
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
//#endregion
//#region ../../webview-src/packages/controls/src/define/all.ts
defineAllControls();
//#endregion
//#region ../../webview-src/packages/api/src/webViewApi.ts
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
//#endregion
//#region ../../webview-src/packages/api/src/iconSet.ts
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
//#endregion
//#region ../../webview-src/packages/api/src/bridge.ts
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
createLazyWebViewBridge();
//#endregion
//#region views/controls-showcase/src/main.ts
var root = document.getElementById("root");
if (!root) throw new Error("#root missing");
var iconSamples = [
	{
		name: "Run",
		path: "expui/run/run.svg"
	},
	{
		name: "Stop",
		path: "expui/run/stop.svg"
	},
	{
		name: "Pause",
		path: "expui/run/pause.svg"
	},
	{
		name: "Gutter Run",
		path: "expui/gutter/run.svg"
	},
	{
		name: "Refresh",
		path: "expui/actions/forceRefresh.svg"
	},
	{
		name: "Play First",
		path: "expui/actions/playFirst.svg"
	},
	{
		name: "Play Back",
		path: "expui/actions/playBack.svg"
	},
	{
		name: "Play Forward",
		path: "expui/actions/playForward.svg"
	},
	{
		name: "Play Last",
		path: "expui/actions/playLast.svg"
	},
	{
		name: "Add",
		path: "expui/general/add.svg"
	},
	{
		name: "Remove",
		path: "expui/general/remove.svg"
	},
	{
		name: "Delete",
		path: "expui/general/delete.svg"
	},
	{
		name: "Edit",
		path: "expui/general/edit.svg"
	},
	{
		name: "Save",
		path: "expui/general/save.svg"
	},
	{
		name: "Close",
		path: "expui/general/close.svg"
	},
	{
		name: "Search",
		path: "expui/general/search.svg"
	},
	{
		name: "Filter",
		path: "expui/general/filter.svg"
	},
	{
		name: "Settings",
		path: "expui/general/settings.svg"
	},
	{
		name: "Help",
		path: "expui/general/help.svg"
	},
	{
		name: "Export",
		path: "expui/general/export.svg"
	},
	{
		name: "Layout",
		path: "expui/general/layout.svg"
	},
	{
		name: "User",
		path: "expui/general/user.svg"
	},
	{
		name: "Locked",
		path: "expui/general/locked.svg"
	},
	{
		name: "Commit",
		path: "expui/vcs/commit.svg"
	},
	{
		name: "Update",
		path: "expui/vcs/update.svg"
	},
	{
		name: "Diff",
		path: "expui/vcs/diff.svg"
	},
	{
		name: "VCS Remove",
		path: "expui/vcs/remove.svg"
	},
	{
		name: "Breakpoint",
		path: "expui/breakpoints/breakpoint.svg"
	},
	{
		name: "Info",
		path: "expui/status/info.svg"
	},
	{
		name: "Success",
		path: "expui/status/success.svg"
	},
	{
		name: "Warning",
		path: "expui/status/warning.svg"
	},
	{
		name: "Error",
		path: "expui/status/error.svg"
	},
	{
		name: "Folder",
		path: "expui/nodes/folder.svg"
	},
	{
		name: "Package",
		path: "expui/nodes/package.svg"
	},
	{
		name: "Function",
		path: "expui/nodes/function.svg"
	},
	{
		name: "Plugin",
		path: "expui/nodes/plugin.svg"
	},
	{
		name: "Unknown Node",
		path: "expui/nodes/unknown.svg"
	},
	{
		name: "YAML",
		path: "expui/fileTypes/yaml.svg"
	},
	{
		name: "Gradle",
		path: "expui/fileTypes/gradle.svg"
	},
	{
		name: "Docker",
		path: "expui/fileTypes/docker.svg"
	},
	{
		name: "SQL",
		path: "expui/fileTypes/sql.svg"
	},
	{
		name: "Properties",
		path: "expui/fileTypes/properties.svg"
	},
	{
		name: "Run Tool Window",
		path: "expui/toolwindows/run.svg"
	},
	{
		name: "Commit Tool Window",
		path: "expui/toolwindows/commit.svg"
	},
	{
		name: "Profiler Tool Window",
		path: "expui/toolwindows/profiler.svg"
	},
	{
		name: "Structure Tool Window",
		path: "expui/toolwindows/structure.svg"
	},
	{
		name: "Palette Tool Window",
		path: "expui/toolwindows/palette.svg"
	},
	{
		name: "External Link",
		path: "expui/ide/externalLink.svg"
	}
];
var sections = {
	"components": {
		title: "Components",
		note: "Batch 1 primitives and first batch 2 composites rendered with Int UI Kit: Islands mapping.",
		render: renderComponents
	},
	"icons": {
		title: "AllIcons",
		note: "Classpath icon resources rendered through IconSet.define(\"AllIcons\") and the WebView icon asset route.",
		render: renderIcons
	},
	"labels-help": {
		title: "Labels and help text",
		note: "Labeled input anatomy with inline help and context help affordances.",
		render: renderLabelsAndHelp
	},
	"validation": {
		title: "Validation",
		note: "Field states for immediate error, warning, and required-input handling.",
		render: renderValidation
	},
	"states": {
		title: "Enabled, disabled, readonly, hidden",
		note: "Primitive attributes reflected to Web Component hosts and native controls.",
		render: renderStates
	},
	"groups-disclosure": {
		title: "Groups and disclosure",
		note: "Related fields, radio groups, separators, and progressive disclosure surfaces.",
		render: renderGroupsAndDisclosure
	},
	"tabs-segmented": {
		title: "Tabs and segmented controls",
		note: "Selection patterns for compact mode switches and tabbed surfaces.",
		render: renderTabsAndSegmented
	},
	"spacing-density": {
		title: "Spacing, density, responsive layout",
		note: "Default and compact control sizes with responsive wrapping.",
		render: renderSpacingDensity
	},
	"theme-rendering": {
		title: "Theme rendering",
		note: "Controls consume semantic --jb-* tokens injected by the WebView runtime.",
		render: renderThemeRendering
	}
};
var sectionId = normalizeSection(new URLSearchParams(window.location.search).get("section"));
var section = sections[sectionId];
document.body.dataset.section = sectionId;
root.innerHTML = `
  <header class="section-header">
    <h1 class="section-title">${section.title}</h1>
    <p class="section-note">${section.note}</p>
  </header>
  ${section.render()}
`;
hydrateControls(root);
function normalizeSection(value) {
	return value && value in sections ? value : "components";
}
function renderIcons() {
	return `
    <div class="showcase-grid icons-showcase-grid">
      <section class="panel icons-panel">
        <p class="panel-title">AllIcons resource paths</p>
        <div class="icon-grid">
          ${iconSamples.map((icon) => `
    <div class="icon-sample">
      <span class="icon-preview">${renderIconImage(icon.path)}</span>
      <span class="icon-name">${icon.name}</span>
      <code class="icon-path">${icon.path}</code>
    </div>
  `).join("")}
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
  `;
}
function renderIconImage(path) {
	return `<jb-icon src="${AllIcons.src(path)}"></jb-icon>`;
}
function renderComponents() {
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
  `;
}
function renderLabelsAndHelp() {
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
  `;
}
function renderValidation() {
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
  `;
}
function renderStates() {
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
  `;
}
function renderGroupsAndDisclosure() {
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
  `;
}
function renderTabsAndSegmented() {
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
  `;
}
function renderSpacingDensity() {
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
  `;
}
function renderThemeRendering() {
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
  `;
}
function hydrateControls(container) {
	setItems(container, "#toolbar-filter", [
		"All",
		"Enabled",
		"Invalid"
	].map(toOption), "All");
	setItems(container, "#component-type", [
		"field",
		"button",
		"selection",
		"popup"
	].map(toOption), "field");
	setItems(container, "#component-search", [
		"Button",
		"Checkbox",
		"Input Field",
		"Segmented Control"
	].map(toOption), "Button");
	setItems(container, "#density-group", ["default", "compact"].map(toOption), "default");
	setItems(container, "#validation-mode", ["strict", "lenient"].map(toOption), "strict");
	setItems(container, "#build-target", ["intellij.platform.ui.webview", "intellij.platform.ui.webview.demo"].map(toOption), "intellij.platform.ui.webview");
	setItems(container, "#build-kind", ["incremental", "full"].map(toOption), "incremental");
	setItems(container, "#view-mode", [
		"preview",
		"source",
		"diff"
	].map(toOption), "preview");
	setItems(container, "#scope-select", [
		"project",
		"module",
		"file"
	].map(toOption), "project");
	setItems(container, "#result-tabs", [
		"problems",
		"preview",
		"events"
	].map(toOption), "problems");
	setItems(container, "#compact-menu", [
		"Pin",
		"Detach",
		"Close"
	].map(toOption), "Pin");
}
function setItems(container, selector, items, value) {
	const element = container.querySelector(selector);
	if (!element) return;
	element.items = items;
	element.value = value;
}
function toOption(value) {
	return {
		value,
		label: value.charAt(0).toUpperCase() + value.slice(1)
	};
}
//#endregion
