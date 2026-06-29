import { o as __toESM } from "./rolldown-runtime.js";
import { V as require_jsx_runtime, it as require_react } from "./assistant-ui-core.js";
import { b as useComposedRefs, g as Primitive, h as composeEventHandlers } from "./assistant-ui-react.js";
import { h as usePrevious } from "./radix-ui-react-select.js";
import { n as createContextScope } from "./radix-ui-react-collection.js";
import { m as useControllableState, u as useSize } from "./radix-ui-react-popover.js";
//#region node_modules/@radix-ui/react-switch/dist/index.mjs
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var import_jsx_runtime = require_jsx_runtime();
var SWITCH_NAME = "Switch";
var [createSwitchContext, createSwitchScope] = createContextScope(SWITCH_NAME);
var [SwitchProviderImpl, useSwitchContext] = createSwitchContext(SWITCH_NAME);
function SwitchProvider(props) {
	const { __scopeSwitch, checked: checkedProp, children, defaultChecked, disabled, form, name, onCheckedChange, required, value = "on", internal_do_not_use_render } = props;
	const [checked, setChecked] = useControllableState({
		prop: checkedProp,
		defaultProp: defaultChecked ?? false,
		onChange: onCheckedChange,
		caller: SWITCH_NAME
	});
	const [control, setControl] = import_react.useState(null);
	const [bubbleInput, setBubbleInput] = import_react.useState(null);
	const context = {
		checked,
		setChecked,
		disabled,
		control,
		setControl,
		name,
		form,
		value,
		hasConsumerStoppedPropagationRef: import_react.useRef(false),
		required,
		defaultChecked,
		isFormControl: control ? !!form || !!control.closest("form") : true,
		bubbleInput,
		setBubbleInput
	};
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SwitchProviderImpl, {
		scope: __scopeSwitch,
		...context,
		children: isFunction(internal_do_not_use_render) ? internal_do_not_use_render(context) : children
	});
}
var TRIGGER_NAME = "SwitchTrigger";
var SwitchTrigger = import_react.forwardRef(({ __scopeSwitch, onClick, ...switchProps }, forwardedRef) => {
	const { value, disabled, checked, required, setControl, setChecked, hasConsumerStoppedPropagationRef, isFormControl, bubbleInput } = useSwitchContext(TRIGGER_NAME, __scopeSwitch);
	const composedRefs = useComposedRefs(forwardedRef, setControl);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.button, {
		type: "button",
		role: "switch",
		"aria-checked": checked,
		"aria-required": required,
		"data-state": getState(checked),
		"data-disabled": disabled ? "" : void 0,
		disabled,
		value,
		...switchProps,
		ref: composedRefs,
		onClick: composeEventHandlers(onClick, (event) => {
			setChecked((prevChecked) => !prevChecked);
			if (bubbleInput && isFormControl) {
				hasConsumerStoppedPropagationRef.current = event.isPropagationStopped();
				if (!hasConsumerStoppedPropagationRef.current) event.stopPropagation();
			}
		})
	});
});
SwitchTrigger.displayName = TRIGGER_NAME;
var Switch = import_react.forwardRef((props, forwardedRef) => {
	const { __scopeSwitch, name, checked, defaultChecked, required, disabled, value, onCheckedChange, form, ...switchProps } = props;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SwitchProvider, {
		__scopeSwitch,
		checked,
		defaultChecked,
		disabled,
		required,
		onCheckedChange,
		name,
		form,
		value,
		internal_do_not_use_render: ({ isFormControl }) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(SwitchTrigger, {
			...switchProps,
			ref: forwardedRef,
			__scopeSwitch
		}), isFormControl && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SwitchBubbleInput, { __scopeSwitch })] })
	});
});
Switch.displayName = SWITCH_NAME;
var THUMB_NAME = "SwitchThumb";
var SwitchThumb = import_react.forwardRef((props, forwardedRef) => {
	const { __scopeSwitch, ...thumbProps } = props;
	const context = useSwitchContext(THUMB_NAME, __scopeSwitch);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.span, {
		"data-state": getState(context.checked),
		"data-disabled": context.disabled ? "" : void 0,
		...thumbProps,
		ref: forwardedRef
	});
});
SwitchThumb.displayName = THUMB_NAME;
var BUBBLE_INPUT_NAME = "SwitchBubbleInput";
var SwitchBubbleInput = import_react.forwardRef(({ __scopeSwitch, ...props }, forwardedRef) => {
	const { control, hasConsumerStoppedPropagationRef, checked, defaultChecked, required, disabled, name, value, form, bubbleInput, setBubbleInput } = useSwitchContext(BUBBLE_INPUT_NAME, __scopeSwitch);
	const composedRefs = useComposedRefs(forwardedRef, setBubbleInput);
	const prevChecked = usePrevious(checked);
	const controlSize = useSize(control);
	import_react.useEffect(() => {
		const input = bubbleInput;
		if (!input) return;
		const inputProto = window.HTMLInputElement.prototype;
		const setChecked = Object.getOwnPropertyDescriptor(inputProto, "checked").set;
		const bubbles = !hasConsumerStoppedPropagationRef.current;
		if (prevChecked !== checked && setChecked) {
			const event = new Event("click", { bubbles });
			setChecked.call(input, checked);
			input.dispatchEvent(event);
		}
	}, [
		bubbleInput,
		prevChecked,
		checked,
		hasConsumerStoppedPropagationRef
	]);
	const defaultCheckedRef = import_react.useRef(checked);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Primitive.input, {
		type: "checkbox",
		"aria-hidden": true,
		defaultChecked: defaultChecked ?? defaultCheckedRef.current,
		required,
		disabled,
		name,
		value,
		form,
		...props,
		tabIndex: -1,
		ref: composedRefs,
		style: {
			...props.style,
			...controlSize,
			position: "absolute",
			pointerEvents: "none",
			opacity: 0,
			margin: 0,
			transform: "translateX(-100%)"
		}
	});
});
SwitchBubbleInput.displayName = BUBBLE_INPUT_NAME;
function isFunction(value) {
	return typeof value === "function";
}
function getState(checked) {
	return checked ? "checked" : "unchecked";
}
//#endregion
export { SwitchThumb as n, Switch as t };
