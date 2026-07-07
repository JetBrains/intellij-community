import { n as __exportAll } from "./rolldown-runtime.js";
import "./devlop.js";
import { t as parse$1 } from "./comma-separated-tokens.js";
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/schema.js
/**
* @import {Schema as SchemaType, Space} from 'property-information'
*/
/** @type {SchemaType} */
var Schema = class {
	/**
	* @param {SchemaType['property']} property
	*   Property.
	* @param {SchemaType['normal']} normal
	*   Normal.
	* @param {Space | undefined} [space]
	*   Space.
	* @returns
	*   Schema.
	*/
	constructor(property, normal, space) {
		this.normal = normal;
		this.property = property;
		if (space) this.space = space;
	}
};
Schema.prototype.normal = {};
Schema.prototype.property = {};
Schema.prototype.space = void 0;
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/merge.js
/**
* @import {Info, Space} from 'property-information'
*/
/**
* @param {ReadonlyArray<Schema>} definitions
*   Definitions.
* @param {Space | undefined} [space]
*   Space.
* @returns {Schema}
*   Schema.
*/
function merge(definitions, space) {
	/** @type {Record<string, Info>} */
	const property = {};
	/** @type {Record<string, string>} */
	const normal = {};
	for (const definition of definitions) {
		Object.assign(property, definition.property);
		Object.assign(normal, definition.normal);
	}
	return new Schema(property, normal, space);
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/normalize.js
/**
* Get the cleaned case insensitive form of an attribute or property.
*
* @param {string} value
*   An attribute-like or property-like name.
* @returns {string}
*   Value that can be used to look up the properly cased property on a
*   `Schema`.
*/
function normalize(value) {
	return value.toLowerCase();
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/info.js
/**
* @import {Info as InfoType} from 'property-information'
*/
/** @type {InfoType} */
var Info = class {
	/**
	* @param {string} property
	*   Property.
	* @param {string} attribute
	*   Attribute.
	* @returns
	*   Info.
	*/
	constructor(property, attribute) {
		this.attribute = attribute;
		this.property = property;
	}
};
Info.prototype.attribute = "";
Info.prototype.booleanish = false;
Info.prototype.boolean = false;
Info.prototype.commaOrSpaceSeparated = false;
Info.prototype.commaSeparated = false;
Info.prototype.defined = false;
Info.prototype.mustUseProperty = false;
Info.prototype.number = false;
Info.prototype.overloadedBoolean = false;
Info.prototype.property = "";
Info.prototype.spaceSeparated = false;
Info.prototype.space = void 0;
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/types.js
var types_exports = /* @__PURE__ */ __exportAll({
	boolean: () => boolean,
	booleanish: () => booleanish,
	commaOrSpaceSeparated: () => commaOrSpaceSeparated,
	commaSeparated: () => commaSeparated,
	number: () => number,
	overloadedBoolean: () => overloadedBoolean,
	spaceSeparated: () => spaceSeparated
});
var powers = 0;
var boolean = increment();
var booleanish = increment();
var overloadedBoolean = increment();
var number = increment();
var spaceSeparated = increment();
var commaSeparated = increment();
var commaOrSpaceSeparated = increment();
function increment() {
	return 2 ** ++powers;
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/defined-info.js
/**
* @import {Space} from 'property-information'
*/
var checks = Object.keys(types_exports);
var DefinedInfo = class extends Info {
	/**
	* @constructor
	* @param {string} property
	*   Property.
	* @param {string} attribute
	*   Attribute.
	* @param {number | null | undefined} [mask]
	*   Mask.
	* @param {Space | undefined} [space]
	*   Space.
	* @returns
	*   Info.
	*/
	constructor(property, attribute, mask, space) {
		let index = -1;
		super(property, attribute);
		mark(this, "space", space);
		if (typeof mask === "number") while (++index < checks.length) {
			const check = checks[index];
			mark(this, checks[index], (mask & types_exports[check]) === types_exports[check]);
		}
	}
};
DefinedInfo.prototype.defined = true;
/**
* @template {keyof DefinedInfo} Key
*   Key type.
* @param {DefinedInfo} values
*   Info.
* @param {Key} key
*   Key.
* @param {DefinedInfo[Key]} value
*   Value.
* @returns {undefined}
*   Nothing.
*/
function mark(values, key, value) {
	if (value) values[key] = value;
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/create.js
/**
* @import {Info, Space} from 'property-information'
*/
/**
* @typedef Definition
*   Definition of a schema.
* @property {Record<string, string> | undefined} [attributes]
*   Normalzed names to special attribute case.
* @property {ReadonlyArray<string> | undefined} [mustUseProperty]
*   Normalized names that must be set as properties.
* @property {Record<string, number | null>} properties
*   Property names to their types.
* @property {Space | undefined} [space]
*   Space.
* @property {Transform} transform
*   Transform a property name.
*/
/**
* @callback Transform
*   Transform.
* @param {Record<string, string>} attributes
*   Attributes.
* @param {string} property
*   Property.
* @returns {string}
*   Attribute.
*/
/**
* @param {Definition} definition
*   Definition.
* @returns {Schema}
*   Schema.
*/
function create(definition) {
	/** @type {Record<string, Info>} */
	const properties = {};
	/** @type {Record<string, string>} */
	const normals = {};
	for (const [property, value] of Object.entries(definition.properties)) {
		const info = new DefinedInfo(property, definition.transform(definition.attributes || {}, property), value, definition.space);
		if (definition.mustUseProperty && definition.mustUseProperty.includes(property)) info.mustUseProperty = true;
		properties[property] = info;
		normals[normalize(property)] = property;
		normals[normalize(info.attribute)] = property;
	}
	return new Schema(properties, normals, definition.space);
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/aria.js
var aria = create({
	properties: {
		ariaActiveDescendant: null,
		ariaAtomic: booleanish,
		ariaAutoComplete: null,
		ariaBusy: booleanish,
		ariaChecked: booleanish,
		ariaColCount: number,
		ariaColIndex: number,
		ariaColSpan: number,
		ariaControls: spaceSeparated,
		ariaCurrent: null,
		ariaDescribedBy: spaceSeparated,
		ariaDetails: null,
		ariaDisabled: booleanish,
		ariaDropEffect: spaceSeparated,
		ariaErrorMessage: null,
		ariaExpanded: booleanish,
		ariaFlowTo: spaceSeparated,
		ariaGrabbed: booleanish,
		ariaHasPopup: null,
		ariaHidden: booleanish,
		ariaInvalid: null,
		ariaKeyShortcuts: null,
		ariaLabel: null,
		ariaLabelledBy: spaceSeparated,
		ariaLevel: number,
		ariaLive: null,
		ariaModal: booleanish,
		ariaMultiLine: booleanish,
		ariaMultiSelectable: booleanish,
		ariaOrientation: null,
		ariaOwns: spaceSeparated,
		ariaPlaceholder: null,
		ariaPosInSet: number,
		ariaPressed: booleanish,
		ariaReadOnly: booleanish,
		ariaRelevant: null,
		ariaRequired: booleanish,
		ariaRoleDescription: spaceSeparated,
		ariaRowCount: number,
		ariaRowIndex: number,
		ariaRowSpan: number,
		ariaSelected: booleanish,
		ariaSetSize: number,
		ariaSort: null,
		ariaValueMax: number,
		ariaValueMin: number,
		ariaValueNow: number,
		ariaValueText: null,
		role: null
	},
	transform(_, property) {
		return property === "role" ? property : "aria-" + property.slice(4).toLowerCase();
	}
});
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/case-sensitive-transform.js
/**
* @param {Record<string, string>} attributes
*   Attributes.
* @param {string} attribute
*   Attribute.
* @returns {string}
*   Transformed attribute.
*/
function caseSensitiveTransform(attributes, attribute) {
	return attribute in attributes ? attributes[attribute] : attribute;
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/util/case-insensitive-transform.js
/**
* @param {Record<string, string>} attributes
*   Attributes.
* @param {string} property
*   Property.
* @returns {string}
*   Transformed property.
*/
function caseInsensitiveTransform(attributes, property) {
	return caseSensitiveTransform(attributes, property.toLowerCase());
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/html.js
var html$1 = create({
	attributes: {
		acceptcharset: "accept-charset",
		classname: "class",
		htmlfor: "for",
		httpequiv: "http-equiv"
	},
	mustUseProperty: [
		"checked",
		"multiple",
		"muted",
		"selected"
	],
	properties: {
		abbr: null,
		accept: commaSeparated,
		acceptCharset: spaceSeparated,
		accessKey: spaceSeparated,
		action: null,
		allow: null,
		allowFullScreen: boolean,
		allowPaymentRequest: boolean,
		allowUserMedia: boolean,
		alt: null,
		as: null,
		async: boolean,
		autoCapitalize: null,
		autoComplete: spaceSeparated,
		autoFocus: boolean,
		autoPlay: boolean,
		blocking: spaceSeparated,
		capture: null,
		charSet: null,
		checked: boolean,
		cite: null,
		className: spaceSeparated,
		cols: number,
		colSpan: null,
		content: null,
		contentEditable: booleanish,
		controls: boolean,
		controlsList: spaceSeparated,
		coords: number | commaSeparated,
		crossOrigin: null,
		data: null,
		dateTime: null,
		decoding: null,
		default: boolean,
		defer: boolean,
		dir: null,
		dirName: null,
		disabled: boolean,
		download: overloadedBoolean,
		draggable: booleanish,
		encType: null,
		enterKeyHint: null,
		fetchPriority: null,
		form: null,
		formAction: null,
		formEncType: null,
		formMethod: null,
		formNoValidate: boolean,
		formTarget: null,
		headers: spaceSeparated,
		height: number,
		hidden: overloadedBoolean,
		high: number,
		href: null,
		hrefLang: null,
		htmlFor: spaceSeparated,
		httpEquiv: spaceSeparated,
		id: null,
		imageSizes: null,
		imageSrcSet: null,
		inert: boolean,
		inputMode: null,
		integrity: null,
		is: null,
		isMap: boolean,
		itemId: null,
		itemProp: spaceSeparated,
		itemRef: spaceSeparated,
		itemScope: boolean,
		itemType: spaceSeparated,
		kind: null,
		label: null,
		lang: null,
		language: null,
		list: null,
		loading: null,
		loop: boolean,
		low: number,
		manifest: null,
		max: null,
		maxLength: number,
		media: null,
		method: null,
		min: null,
		minLength: number,
		multiple: boolean,
		muted: boolean,
		name: null,
		nonce: null,
		noModule: boolean,
		noValidate: boolean,
		onAbort: null,
		onAfterPrint: null,
		onAuxClick: null,
		onBeforeMatch: null,
		onBeforePrint: null,
		onBeforeToggle: null,
		onBeforeUnload: null,
		onBlur: null,
		onCancel: null,
		onCanPlay: null,
		onCanPlayThrough: null,
		onChange: null,
		onClick: null,
		onClose: null,
		onContextLost: null,
		onContextMenu: null,
		onContextRestored: null,
		onCopy: null,
		onCueChange: null,
		onCut: null,
		onDblClick: null,
		onDrag: null,
		onDragEnd: null,
		onDragEnter: null,
		onDragExit: null,
		onDragLeave: null,
		onDragOver: null,
		onDragStart: null,
		onDrop: null,
		onDurationChange: null,
		onEmptied: null,
		onEnded: null,
		onError: null,
		onFocus: null,
		onFormData: null,
		onHashChange: null,
		onInput: null,
		onInvalid: null,
		onKeyDown: null,
		onKeyPress: null,
		onKeyUp: null,
		onLanguageChange: null,
		onLoad: null,
		onLoadedData: null,
		onLoadedMetadata: null,
		onLoadEnd: null,
		onLoadStart: null,
		onMessage: null,
		onMessageError: null,
		onMouseDown: null,
		onMouseEnter: null,
		onMouseLeave: null,
		onMouseMove: null,
		onMouseOut: null,
		onMouseOver: null,
		onMouseUp: null,
		onOffline: null,
		onOnline: null,
		onPageHide: null,
		onPageShow: null,
		onPaste: null,
		onPause: null,
		onPlay: null,
		onPlaying: null,
		onPopState: null,
		onProgress: null,
		onRateChange: null,
		onRejectionHandled: null,
		onReset: null,
		onResize: null,
		onScroll: null,
		onScrollEnd: null,
		onSecurityPolicyViolation: null,
		onSeeked: null,
		onSeeking: null,
		onSelect: null,
		onSlotChange: null,
		onStalled: null,
		onStorage: null,
		onSubmit: null,
		onSuspend: null,
		onTimeUpdate: null,
		onToggle: null,
		onUnhandledRejection: null,
		onUnload: null,
		onVolumeChange: null,
		onWaiting: null,
		onWheel: null,
		open: boolean,
		optimum: number,
		pattern: null,
		ping: spaceSeparated,
		placeholder: null,
		playsInline: boolean,
		popover: null,
		popoverTarget: null,
		popoverTargetAction: null,
		poster: null,
		preload: null,
		readOnly: boolean,
		referrerPolicy: null,
		rel: spaceSeparated,
		required: boolean,
		reversed: boolean,
		rows: number,
		rowSpan: number,
		sandbox: spaceSeparated,
		scope: null,
		scoped: boolean,
		seamless: boolean,
		selected: boolean,
		shadowRootClonable: boolean,
		shadowRootDelegatesFocus: boolean,
		shadowRootMode: null,
		shape: null,
		size: number,
		sizes: null,
		slot: null,
		span: number,
		spellCheck: booleanish,
		src: null,
		srcDoc: null,
		srcLang: null,
		srcSet: null,
		start: number,
		step: null,
		style: null,
		tabIndex: number,
		target: null,
		title: null,
		translate: null,
		type: null,
		typeMustMatch: boolean,
		useMap: null,
		value: booleanish,
		width: number,
		wrap: null,
		writingSuggestions: null,
		align: null,
		aLink: null,
		archive: spaceSeparated,
		axis: null,
		background: null,
		bgColor: null,
		border: number,
		borderColor: null,
		bottomMargin: number,
		cellPadding: null,
		cellSpacing: null,
		char: null,
		charOff: null,
		classId: null,
		clear: null,
		code: null,
		codeBase: null,
		codeType: null,
		color: null,
		compact: boolean,
		declare: boolean,
		event: null,
		face: null,
		frame: null,
		frameBorder: null,
		hSpace: number,
		leftMargin: number,
		link: null,
		longDesc: null,
		lowSrc: null,
		marginHeight: number,
		marginWidth: number,
		noResize: boolean,
		noHref: boolean,
		noShade: boolean,
		noWrap: boolean,
		object: null,
		profile: null,
		prompt: null,
		rev: null,
		rightMargin: number,
		rules: null,
		scheme: null,
		scrolling: booleanish,
		standby: null,
		summary: null,
		text: null,
		topMargin: number,
		valueType: null,
		version: null,
		vAlign: null,
		vLink: null,
		vSpace: number,
		allowTransparency: null,
		autoCorrect: null,
		autoSave: null,
		disablePictureInPicture: boolean,
		disableRemotePlayback: boolean,
		prefix: null,
		property: null,
		results: number,
		security: null,
		unselectable: null
	},
	space: "html",
	transform: caseInsensitiveTransform
});
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/svg.js
var svg$1 = create({
	attributes: {
		accentHeight: "accent-height",
		alignmentBaseline: "alignment-baseline",
		arabicForm: "arabic-form",
		baselineShift: "baseline-shift",
		capHeight: "cap-height",
		className: "class",
		clipPath: "clip-path",
		clipRule: "clip-rule",
		colorInterpolation: "color-interpolation",
		colorInterpolationFilters: "color-interpolation-filters",
		colorProfile: "color-profile",
		colorRendering: "color-rendering",
		crossOrigin: "crossorigin",
		dataType: "datatype",
		dominantBaseline: "dominant-baseline",
		enableBackground: "enable-background",
		fillOpacity: "fill-opacity",
		fillRule: "fill-rule",
		floodColor: "flood-color",
		floodOpacity: "flood-opacity",
		fontFamily: "font-family",
		fontSize: "font-size",
		fontSizeAdjust: "font-size-adjust",
		fontStretch: "font-stretch",
		fontStyle: "font-style",
		fontVariant: "font-variant",
		fontWeight: "font-weight",
		glyphName: "glyph-name",
		glyphOrientationHorizontal: "glyph-orientation-horizontal",
		glyphOrientationVertical: "glyph-orientation-vertical",
		hrefLang: "hreflang",
		horizAdvX: "horiz-adv-x",
		horizOriginX: "horiz-origin-x",
		horizOriginY: "horiz-origin-y",
		imageRendering: "image-rendering",
		letterSpacing: "letter-spacing",
		lightingColor: "lighting-color",
		markerEnd: "marker-end",
		markerMid: "marker-mid",
		markerStart: "marker-start",
		navDown: "nav-down",
		navDownLeft: "nav-down-left",
		navDownRight: "nav-down-right",
		navLeft: "nav-left",
		navNext: "nav-next",
		navPrev: "nav-prev",
		navRight: "nav-right",
		navUp: "nav-up",
		navUpLeft: "nav-up-left",
		navUpRight: "nav-up-right",
		onAbort: "onabort",
		onActivate: "onactivate",
		onAfterPrint: "onafterprint",
		onBeforePrint: "onbeforeprint",
		onBegin: "onbegin",
		onCancel: "oncancel",
		onCanPlay: "oncanplay",
		onCanPlayThrough: "oncanplaythrough",
		onChange: "onchange",
		onClick: "onclick",
		onClose: "onclose",
		onCopy: "oncopy",
		onCueChange: "oncuechange",
		onCut: "oncut",
		onDblClick: "ondblclick",
		onDrag: "ondrag",
		onDragEnd: "ondragend",
		onDragEnter: "ondragenter",
		onDragExit: "ondragexit",
		onDragLeave: "ondragleave",
		onDragOver: "ondragover",
		onDragStart: "ondragstart",
		onDrop: "ondrop",
		onDurationChange: "ondurationchange",
		onEmptied: "onemptied",
		onEnd: "onend",
		onEnded: "onended",
		onError: "onerror",
		onFocus: "onfocus",
		onFocusIn: "onfocusin",
		onFocusOut: "onfocusout",
		onHashChange: "onhashchange",
		onInput: "oninput",
		onInvalid: "oninvalid",
		onKeyDown: "onkeydown",
		onKeyPress: "onkeypress",
		onKeyUp: "onkeyup",
		onLoad: "onload",
		onLoadedData: "onloadeddata",
		onLoadedMetadata: "onloadedmetadata",
		onLoadStart: "onloadstart",
		onMessage: "onmessage",
		onMouseDown: "onmousedown",
		onMouseEnter: "onmouseenter",
		onMouseLeave: "onmouseleave",
		onMouseMove: "onmousemove",
		onMouseOut: "onmouseout",
		onMouseOver: "onmouseover",
		onMouseUp: "onmouseup",
		onMouseWheel: "onmousewheel",
		onOffline: "onoffline",
		onOnline: "ononline",
		onPageHide: "onpagehide",
		onPageShow: "onpageshow",
		onPaste: "onpaste",
		onPause: "onpause",
		onPlay: "onplay",
		onPlaying: "onplaying",
		onPopState: "onpopstate",
		onProgress: "onprogress",
		onRateChange: "onratechange",
		onRepeat: "onrepeat",
		onReset: "onreset",
		onResize: "onresize",
		onScroll: "onscroll",
		onSeeked: "onseeked",
		onSeeking: "onseeking",
		onSelect: "onselect",
		onShow: "onshow",
		onStalled: "onstalled",
		onStorage: "onstorage",
		onSubmit: "onsubmit",
		onSuspend: "onsuspend",
		onTimeUpdate: "ontimeupdate",
		onToggle: "ontoggle",
		onUnload: "onunload",
		onVolumeChange: "onvolumechange",
		onWaiting: "onwaiting",
		onZoom: "onzoom",
		overlinePosition: "overline-position",
		overlineThickness: "overline-thickness",
		paintOrder: "paint-order",
		panose1: "panose-1",
		pointerEvents: "pointer-events",
		referrerPolicy: "referrerpolicy",
		renderingIntent: "rendering-intent",
		shapeRendering: "shape-rendering",
		stopColor: "stop-color",
		stopOpacity: "stop-opacity",
		strikethroughPosition: "strikethrough-position",
		strikethroughThickness: "strikethrough-thickness",
		strokeDashArray: "stroke-dasharray",
		strokeDashOffset: "stroke-dashoffset",
		strokeLineCap: "stroke-linecap",
		strokeLineJoin: "stroke-linejoin",
		strokeMiterLimit: "stroke-miterlimit",
		strokeOpacity: "stroke-opacity",
		strokeWidth: "stroke-width",
		tabIndex: "tabindex",
		textAnchor: "text-anchor",
		textDecoration: "text-decoration",
		textRendering: "text-rendering",
		transformOrigin: "transform-origin",
		typeOf: "typeof",
		underlinePosition: "underline-position",
		underlineThickness: "underline-thickness",
		unicodeBidi: "unicode-bidi",
		unicodeRange: "unicode-range",
		unitsPerEm: "units-per-em",
		vAlphabetic: "v-alphabetic",
		vHanging: "v-hanging",
		vIdeographic: "v-ideographic",
		vMathematical: "v-mathematical",
		vectorEffect: "vector-effect",
		vertAdvY: "vert-adv-y",
		vertOriginX: "vert-origin-x",
		vertOriginY: "vert-origin-y",
		wordSpacing: "word-spacing",
		writingMode: "writing-mode",
		xHeight: "x-height",
		playbackOrder: "playbackorder",
		timelineBegin: "timelinebegin"
	},
	properties: {
		about: commaOrSpaceSeparated,
		accentHeight: number,
		accumulate: null,
		additive: null,
		alignmentBaseline: null,
		alphabetic: number,
		amplitude: number,
		arabicForm: null,
		ascent: number,
		attributeName: null,
		attributeType: null,
		azimuth: number,
		bandwidth: null,
		baselineShift: null,
		baseFrequency: null,
		baseProfile: null,
		bbox: null,
		begin: null,
		bias: number,
		by: null,
		calcMode: null,
		capHeight: number,
		className: spaceSeparated,
		clip: null,
		clipPath: null,
		clipPathUnits: null,
		clipRule: null,
		color: null,
		colorInterpolation: null,
		colorInterpolationFilters: null,
		colorProfile: null,
		colorRendering: null,
		content: null,
		contentScriptType: null,
		contentStyleType: null,
		crossOrigin: null,
		cursor: null,
		cx: null,
		cy: null,
		d: null,
		dataType: null,
		defaultAction: null,
		descent: number,
		diffuseConstant: number,
		direction: null,
		display: null,
		dur: null,
		divisor: number,
		dominantBaseline: null,
		download: boolean,
		dx: null,
		dy: null,
		edgeMode: null,
		editable: null,
		elevation: number,
		enableBackground: null,
		end: null,
		event: null,
		exponent: number,
		externalResourcesRequired: null,
		fill: null,
		fillOpacity: number,
		fillRule: null,
		filter: null,
		filterRes: null,
		filterUnits: null,
		floodColor: null,
		floodOpacity: null,
		focusable: null,
		focusHighlight: null,
		fontFamily: null,
		fontSize: null,
		fontSizeAdjust: null,
		fontStretch: null,
		fontStyle: null,
		fontVariant: null,
		fontWeight: null,
		format: null,
		fr: null,
		from: null,
		fx: null,
		fy: null,
		g1: commaSeparated,
		g2: commaSeparated,
		glyphName: commaSeparated,
		glyphOrientationHorizontal: null,
		glyphOrientationVertical: null,
		glyphRef: null,
		gradientTransform: null,
		gradientUnits: null,
		handler: null,
		hanging: number,
		hatchContentUnits: null,
		hatchUnits: null,
		height: null,
		href: null,
		hrefLang: null,
		horizAdvX: number,
		horizOriginX: number,
		horizOriginY: number,
		id: null,
		ideographic: number,
		imageRendering: null,
		initialVisibility: null,
		in: null,
		in2: null,
		intercept: number,
		k: number,
		k1: number,
		k2: number,
		k3: number,
		k4: number,
		kernelMatrix: commaOrSpaceSeparated,
		kernelUnitLength: null,
		keyPoints: null,
		keySplines: null,
		keyTimes: null,
		kerning: null,
		lang: null,
		lengthAdjust: null,
		letterSpacing: null,
		lightingColor: null,
		limitingConeAngle: number,
		local: null,
		markerEnd: null,
		markerMid: null,
		markerStart: null,
		markerHeight: null,
		markerUnits: null,
		markerWidth: null,
		mask: null,
		maskContentUnits: null,
		maskUnits: null,
		mathematical: null,
		max: null,
		media: null,
		mediaCharacterEncoding: null,
		mediaContentEncodings: null,
		mediaSize: number,
		mediaTime: null,
		method: null,
		min: null,
		mode: null,
		name: null,
		navDown: null,
		navDownLeft: null,
		navDownRight: null,
		navLeft: null,
		navNext: null,
		navPrev: null,
		navRight: null,
		navUp: null,
		navUpLeft: null,
		navUpRight: null,
		numOctaves: null,
		observer: null,
		offset: null,
		onAbort: null,
		onActivate: null,
		onAfterPrint: null,
		onBeforePrint: null,
		onBegin: null,
		onCancel: null,
		onCanPlay: null,
		onCanPlayThrough: null,
		onChange: null,
		onClick: null,
		onClose: null,
		onCopy: null,
		onCueChange: null,
		onCut: null,
		onDblClick: null,
		onDrag: null,
		onDragEnd: null,
		onDragEnter: null,
		onDragExit: null,
		onDragLeave: null,
		onDragOver: null,
		onDragStart: null,
		onDrop: null,
		onDurationChange: null,
		onEmptied: null,
		onEnd: null,
		onEnded: null,
		onError: null,
		onFocus: null,
		onFocusIn: null,
		onFocusOut: null,
		onHashChange: null,
		onInput: null,
		onInvalid: null,
		onKeyDown: null,
		onKeyPress: null,
		onKeyUp: null,
		onLoad: null,
		onLoadedData: null,
		onLoadedMetadata: null,
		onLoadStart: null,
		onMessage: null,
		onMouseDown: null,
		onMouseEnter: null,
		onMouseLeave: null,
		onMouseMove: null,
		onMouseOut: null,
		onMouseOver: null,
		onMouseUp: null,
		onMouseWheel: null,
		onOffline: null,
		onOnline: null,
		onPageHide: null,
		onPageShow: null,
		onPaste: null,
		onPause: null,
		onPlay: null,
		onPlaying: null,
		onPopState: null,
		onProgress: null,
		onRateChange: null,
		onRepeat: null,
		onReset: null,
		onResize: null,
		onScroll: null,
		onSeeked: null,
		onSeeking: null,
		onSelect: null,
		onShow: null,
		onStalled: null,
		onStorage: null,
		onSubmit: null,
		onSuspend: null,
		onTimeUpdate: null,
		onToggle: null,
		onUnload: null,
		onVolumeChange: null,
		onWaiting: null,
		onZoom: null,
		opacity: null,
		operator: null,
		order: null,
		orient: null,
		orientation: null,
		origin: null,
		overflow: null,
		overlay: null,
		overlinePosition: number,
		overlineThickness: number,
		paintOrder: null,
		panose1: null,
		path: null,
		pathLength: number,
		patternContentUnits: null,
		patternTransform: null,
		patternUnits: null,
		phase: null,
		ping: spaceSeparated,
		pitch: null,
		playbackOrder: null,
		pointerEvents: null,
		points: null,
		pointsAtX: number,
		pointsAtY: number,
		pointsAtZ: number,
		preserveAlpha: null,
		preserveAspectRatio: null,
		primitiveUnits: null,
		propagate: null,
		property: commaOrSpaceSeparated,
		r: null,
		radius: null,
		referrerPolicy: null,
		refX: null,
		refY: null,
		rel: commaOrSpaceSeparated,
		rev: commaOrSpaceSeparated,
		renderingIntent: null,
		repeatCount: null,
		repeatDur: null,
		requiredExtensions: commaOrSpaceSeparated,
		requiredFeatures: commaOrSpaceSeparated,
		requiredFonts: commaOrSpaceSeparated,
		requiredFormats: commaOrSpaceSeparated,
		resource: null,
		restart: null,
		result: null,
		rotate: null,
		rx: null,
		ry: null,
		scale: null,
		seed: null,
		shapeRendering: null,
		side: null,
		slope: null,
		snapshotTime: null,
		specularConstant: number,
		specularExponent: number,
		spreadMethod: null,
		spacing: null,
		startOffset: null,
		stdDeviation: null,
		stemh: null,
		stemv: null,
		stitchTiles: null,
		stopColor: null,
		stopOpacity: null,
		strikethroughPosition: number,
		strikethroughThickness: number,
		string: null,
		stroke: null,
		strokeDashArray: commaOrSpaceSeparated,
		strokeDashOffset: null,
		strokeLineCap: null,
		strokeLineJoin: null,
		strokeMiterLimit: number,
		strokeOpacity: number,
		strokeWidth: null,
		style: null,
		surfaceScale: number,
		syncBehavior: null,
		syncBehaviorDefault: null,
		syncMaster: null,
		syncTolerance: null,
		syncToleranceDefault: null,
		systemLanguage: commaOrSpaceSeparated,
		tabIndex: number,
		tableValues: null,
		target: null,
		targetX: number,
		targetY: number,
		textAnchor: null,
		textDecoration: null,
		textRendering: null,
		textLength: null,
		timelineBegin: null,
		title: null,
		transformBehavior: null,
		type: null,
		typeOf: commaOrSpaceSeparated,
		to: null,
		transform: null,
		transformOrigin: null,
		u1: null,
		u2: null,
		underlinePosition: number,
		underlineThickness: number,
		unicode: null,
		unicodeBidi: null,
		unicodeRange: null,
		unitsPerEm: number,
		values: null,
		vAlphabetic: number,
		vMathematical: number,
		vectorEffect: null,
		vHanging: number,
		vIdeographic: number,
		version: null,
		vertAdvY: number,
		vertOriginX: number,
		vertOriginY: number,
		viewBox: null,
		viewTarget: null,
		visibility: null,
		width: null,
		widths: null,
		wordSpacing: null,
		writingMode: null,
		x: null,
		x1: null,
		x2: null,
		xChannelSelector: null,
		xHeight: number,
		y: null,
		y1: null,
		y2: null,
		yChannelSelector: null,
		z: null,
		zoomAndPan: null
	},
	space: "svg",
	transform: caseSensitiveTransform
});
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/xlink.js
var xlink = create({
	properties: {
		xLinkActuate: null,
		xLinkArcRole: null,
		xLinkHref: null,
		xLinkRole: null,
		xLinkShow: null,
		xLinkTitle: null,
		xLinkType: null
	},
	space: "xlink",
	transform(_, property) {
		return "xlink:" + property.slice(5).toLowerCase();
	}
});
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/xmlns.js
var xmlns = create({
	attributes: { xmlnsxlink: "xmlns:xlink" },
	properties: {
		xmlnsXLink: null,
		xmlns: null
	},
	space: "xmlns",
	transform: caseInsensitiveTransform
});
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/xml.js
var xml = create({
	properties: {
		xmlBase: null,
		xmlLang: null,
		xmlSpace: null
	},
	space: "xml",
	transform(_, property) {
		return "xml:" + property.slice(3).toLowerCase();
	}
});
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/hast-to-react.js
/**
* Special cases for React (`Record<string, string>`).
*
* `hast` is close to `React` but differs in a couple of cases.
* To get a React property from a hast property,
* check if it is in `hastToReact`.
* If it is, use the corresponding value;
* otherwise, use the hast property.
*
* @type {Record<string, string>}
*/
var hastToReact = {
	classId: "classID",
	dataType: "datatype",
	itemId: "itemID",
	strokeDashArray: "strokeDasharray",
	strokeDashOffset: "strokeDashoffset",
	strokeLineCap: "strokeLinecap",
	strokeLineJoin: "strokeLinejoin",
	strokeMiterLimit: "strokeMiterlimit",
	typeOf: "typeof",
	xLinkActuate: "xlinkActuate",
	xLinkArcRole: "xlinkArcrole",
	xLinkHref: "xlinkHref",
	xLinkRole: "xlinkRole",
	xLinkShow: "xlinkShow",
	xLinkTitle: "xlinkTitle",
	xLinkType: "xlinkType",
	xmlnsXLink: "xmlnsXlink"
};
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/lib/find.js
/**
* @import {Schema} from 'property-information'
*/
var cap = /[A-Z]/g;
var dash = /-[a-z]/g;
var valid = /^data[-\w.:]+$/i;
/**
* Look up info on a property.
*
* In most cases the given `schema` contains info on the property.
* All standard,
* most legacy,
* and some non-standard properties are supported.
* For these cases,
* the returned `Info` has hints about the value of the property.
*
* `name` can also be a valid data attribute or property,
* in which case an `Info` object with the correctly cased `attribute` and
* `property` is returned.
*
* `name` can be an unknown attribute,
* in which case an `Info` object with `attribute` and `property` set to the
* given name is returned.
* It is not recommended to provide unsupported legacy or recently specced
* properties.
*
*
* @param {Schema} schema
*   Schema;
*   either the `html` or `svg` export.
* @param {string} value
*   An attribute-like or property-like name;
*   it will be passed through `normalize` to hopefully find the correct info.
* @returns {Info}
*   Info.
*/
function find(schema, value) {
	const normal = normalize(value);
	let property = value;
	let Type = Info;
	if (normal in schema.normal) return schema.property[schema.normal[normal]];
	if (normal.length > 4 && normal.slice(0, 4) === "data" && valid.test(value)) {
		if (value.charAt(4) === "-") {
			const rest = value.slice(5).replace(dash, camelcase);
			property = "data" + rest.charAt(0).toUpperCase() + rest.slice(1);
		} else {
			const rest = value.slice(4);
			if (!dash.test(rest)) {
				let dashes = rest.replace(cap, kebab);
				if (dashes.charAt(0) !== "-") dashes = "-" + dashes;
				value = "data" + dashes;
			}
		}
		Type = DefinedInfo;
	}
	return new Type(property, value);
}
/**
* @param {string} $0
*   Value.
* @returns {string}
*   Kebab.
*/
function kebab($0) {
	return "-" + $0.toLowerCase();
}
/**
* @param {string} $0
*   Value.
* @returns {string}
*   Camel.
*/
function camelcase($0) {
	return $0.charAt(1).toUpperCase();
}
//#endregion
//#region node_modules/.bun/property-information@7.1.0/node_modules/property-information/index.js
var html = merge([
	aria,
	html$1,
	xlink,
	xmlns,
	xml
], "html");
var svg = merge([
	aria,
	svg$1,
	xlink,
	xmlns,
	xml
], "svg");
//#endregion
//#region node_modules/.bun/space-separated-tokens@2.0.2/node_modules/space-separated-tokens/index.js
/**
* Parse space-separated tokens to an array of strings.
*
* @param {string} value
*   Space-separated tokens.
* @returns {Array<string>}
*   List of tokens.
*/
function parse(value) {
	const input = String(value || "").trim();
	return input ? input.split(/[ \t\n\r\f]+/g) : [];
}
/**
* Serialize an array of strings as space separated-tokens.
*
* @param {Array<string|number>} values
*   List of tokens.
* @returns {string}
*   Space-separated tokens.
*/
function stringify(values) {
	return values.join(" ").trim();
}
//#endregion
//#region node_modules/.bun/hast-util-parse-selector@4.0.0/node_modules/hast-util-parse-selector/lib/index.js
/**
* @typedef {import('hast').Element} Element
* @typedef {import('hast').Properties} Properties
*/
/**
* @template {string} SimpleSelector
*   Selector type.
* @template {string} DefaultTagName
*   Default tag name.
* @typedef {(
*   SimpleSelector extends ''
*     ? DefaultTagName
*     : SimpleSelector extends `${infer TagName}.${infer Rest}`
*     ? ExtractTagName<TagName, DefaultTagName>
*     : SimpleSelector extends `${infer TagName}#${infer Rest}`
*     ? ExtractTagName<TagName, DefaultTagName>
*     : SimpleSelector extends string
*     ? SimpleSelector
*     : DefaultTagName
* )} ExtractTagName
*   Extract tag name from a simple selector.
*/
var search = /[#.]/g;
/**
* Create a hast element from a simple CSS selector.
*
* @template {string} Selector
*   Type of selector.
* @template {string} [DefaultTagName='div']
*   Type of default tag name (default: `'div'`).
* @param {Selector | null | undefined} [selector]
*   Simple CSS selector (optional).
*
*   Can contain a tag name (`foo`), classes (`.bar`), and an ID (`#baz`).
*   Multiple classes are allowed.
*   Uses the last ID if multiple IDs are found.
* @param {DefaultTagName | null | undefined} [defaultTagName='div']
*   Tag name to use if `selector` does not specify one (default: `'div'`).
* @returns {Element & {tagName: ExtractTagName<Selector, DefaultTagName>}}
*   Built element.
*/
function parseSelector(selector, defaultTagName) {
	const value = selector || "";
	/** @type {Properties} */
	const props = {};
	let start = 0;
	/** @type {string | undefined} */
	let previous;
	/** @type {string | undefined} */
	let tagName;
	while (start < value.length) {
		search.lastIndex = start;
		const match = search.exec(value);
		const subvalue = value.slice(start, match ? match.index : value.length);
		if (subvalue) {
			if (!previous) tagName = subvalue;
			else if (previous === "#") props.id = subvalue;
			else if (Array.isArray(props.className)) props.className.push(subvalue);
			else props.className = [subvalue];
			start += subvalue.length;
		}
		if (match) {
			previous = match[0];
			start++;
		}
	}
	return {
		type: "element",
		tagName: tagName || defaultTagName || "div",
		properties: props,
		children: []
	};
}
//#endregion
//#region node_modules/.bun/hastscript@9.0.1/node_modules/hastscript/lib/create-h.js
/**
* @import {Element, Nodes, RootContent, Root} from 'hast'
* @import {Info, Schema} from 'property-information'
*/
/**
* @typedef {Array<Nodes | PrimitiveChild>} ArrayChildNested
*   List of children (deep).
*/
/**
* @typedef {Array<ArrayChildNested | Nodes | PrimitiveChild>} ArrayChild
*   List of children.
*/
/**
* @typedef {Array<number | string>} ArrayValue
*   List of property values for space- or comma separated values (such as `className`).
*/
/**
* @typedef {ArrayChild | Nodes | PrimitiveChild} Child
*   Acceptable child value.
*/
/**
* @typedef {number | string | null | undefined} PrimitiveChild
*   Primitive children, either ignored (nullish), or turned into text nodes.
*/
/**
* @typedef {boolean | number | string | null | undefined} PrimitiveValue
*   Primitive property value.
*/
/**
* @typedef {Record<string, PropertyValue | Style>} Properties
*   Acceptable value for element properties.
*/
/**
* @typedef {ArrayValue | PrimitiveValue} PropertyValue
*   Primitive value or list value.
*/
/**
* @typedef {Element | Root} Result
*   Result from a `h` (or `s`) call.
*/
/**
* @typedef {number | string} StyleValue
*   Value for a CSS style field.
*/
/**
* @typedef {Record<string, StyleValue>} Style
*   Supported value of a `style` prop.
*/
/**
* @param {Schema} schema
*   Schema to use.
* @param {string} defaultTagName
*   Default tag name.
* @param {ReadonlyArray<string> | undefined} [caseSensitive]
*   Case-sensitive tag names (default: `undefined`).
* @returns
*   `h`.
*/
function createH(schema, defaultTagName, caseSensitive) {
	const adjust = caseSensitive ? createAdjustMap(caseSensitive) : void 0;
	/**
	* Hyperscript compatible DSL for creating virtual hast trees.
	*
	* @overload
	* @param {null | undefined} [selector]
	* @param {...Child} children
	* @returns {Root}
	*
	* @overload
	* @param {string} selector
	* @param {Properties} properties
	* @param {...Child} children
	* @returns {Element}
	*
	* @overload
	* @param {string} selector
	* @param {...Child} children
	* @returns {Element}
	*
	* @param {string | null | undefined} [selector]
	*   Selector.
	* @param {Child | Properties | null | undefined} [properties]
	*   Properties (or first child) (default: `undefined`).
	* @param {...Child} children
	*   Children.
	* @returns {Result}
	*   Result.
	*/
	function h(selector, properties, ...children) {
		/** @type {Result} */
		let node;
		if (selector === null || selector === void 0) {
			node = {
				type: "root",
				children: []
			};
			const child = properties;
			children.unshift(child);
		} else {
			node = parseSelector(selector, defaultTagName);
			const lower = node.tagName.toLowerCase();
			const adjusted = adjust ? adjust.get(lower) : void 0;
			node.tagName = adjusted || lower;
			if (isChild(properties)) children.unshift(properties);
			else for (const [key, value] of Object.entries(properties)) addProperty(schema, node.properties, key, value);
		}
		for (const child of children) addChild(node.children, child);
		if (node.type === "element" && node.tagName === "template") {
			node.content = {
				type: "root",
				children: node.children
			};
			node.children = [];
		}
		return node;
	}
	return h;
}
/**
* Check if something is properties or a child.
*
* @param {Child | Properties} value
*   Value to check.
* @returns {value is Child}
*   Whether `value` is definitely a child.
*/
function isChild(value) {
	if (value === null || typeof value !== "object" || Array.isArray(value)) return true;
	if (typeof value.type !== "string") return false;
	const record = value;
	const keys = Object.keys(value);
	for (const key of keys) {
		const value = record[key];
		if (value && typeof value === "object") {
			if (!Array.isArray(value)) return true;
			const list = value;
			for (const item of list) if (typeof item !== "number" && typeof item !== "string") return true;
		}
	}
	if ("children" in value && Array.isArray(value.children)) return true;
	return false;
}
/**
* @param {Schema} schema
*   Schema.
* @param {Properties} properties
*   Properties object.
* @param {string} key
*   Property name.
* @param {PropertyValue | Style} value
*   Property value.
* @returns {undefined}
*   Nothing.
*/
function addProperty(schema, properties, key, value) {
	const info = find(schema, key);
	/** @type {PropertyValue} */
	let result;
	if (value === null || value === void 0) return;
	if (typeof value === "number") {
		if (Number.isNaN(value)) return;
		result = value;
	} else if (typeof value === "boolean") result = value;
	else if (typeof value === "string") if (info.spaceSeparated) result = parse(value);
	else if (info.commaSeparated) result = parse$1(value);
	else if (info.commaOrSpaceSeparated) result = parse(parse$1(value).join(" "));
	else result = parsePrimitive(info, info.property, value);
	else if (Array.isArray(value)) result = [...value];
	else result = info.property === "style" ? style(value) : String(value);
	if (Array.isArray(result)) {
		/** @type {Array<number | string>} */
		const finalResult = [];
		for (const item of result) finalResult.push(parsePrimitive(info, info.property, item));
		result = finalResult;
	}
	if (info.property === "className" && Array.isArray(properties.className)) result = properties.className.concat(result);
	properties[info.property] = result;
}
/**
* @param {Array<RootContent>} nodes
*   Children.
* @param {Child} value
*   Child.
* @returns {undefined}
*   Nothing.
*/
function addChild(nodes, value) {
	if (value === null || value === void 0) {} else if (typeof value === "number" || typeof value === "string") nodes.push({
		type: "text",
		value: String(value)
	});
	else if (Array.isArray(value)) for (const child of value) addChild(nodes, child);
	else if (typeof value === "object" && "type" in value) if (value.type === "root") addChild(nodes, value.children);
	else nodes.push(value);
	else throw new Error("Expected node, nodes, or string, got `" + value + "`");
}
/**
* Parse a single primitives.
*
* @param {Info} info
*   Property information.
* @param {string} name
*   Property name.
* @param {PrimitiveValue} value
*   Property value.
* @returns {PrimitiveValue}
*   Property value.
*/
function parsePrimitive(info, name, value) {
	if (typeof value === "string") {
		if (info.number && value && !Number.isNaN(Number(value))) return Number(value);
		if ((info.boolean || info.overloadedBoolean) && (value === "" || normalize(value) === normalize(name))) return true;
	}
	return value;
}
/**
* Serialize a `style` object as a string.
*
* @param {Style} styles
*   Style object.
* @returns {string}
*   CSS string.
*/
function style(styles) {
	/** @type {Array<string>} */
	const result = [];
	for (const [key, value] of Object.entries(styles)) result.push([key, value].join(": "));
	return result.join("; ");
}
/**
* Create a map to adjust casing.
*
* @param {ReadonlyArray<string>} values
*   List of properly cased keys.
* @returns {Map<string, string>}
*   Map of lowercase keys to uppercase keys.
*/
function createAdjustMap(values) {
	/** @type {Map<string, string>} */
	const result = /* @__PURE__ */ new Map();
	for (const value of values) result.set(value.toLowerCase(), value);
	return result;
}
//#endregion
//#region node_modules/.bun/hastscript@9.0.1/node_modules/hastscript/lib/svg-case-sensitive-tag-names.js
/**
* List of case-sensitive SVG tag names.
*
* @type {ReadonlyArray<string>}
*/
var svgCaseSensitiveTagNames = [
	"altGlyph",
	"altGlyphDef",
	"altGlyphItem",
	"animateColor",
	"animateMotion",
	"animateTransform",
	"clipPath",
	"feBlend",
	"feColorMatrix",
	"feComponentTransfer",
	"feComposite",
	"feConvolveMatrix",
	"feDiffuseLighting",
	"feDisplacementMap",
	"feDistantLight",
	"feDropShadow",
	"feFlood",
	"feFuncA",
	"feFuncB",
	"feFuncG",
	"feFuncR",
	"feGaussianBlur",
	"feImage",
	"feMerge",
	"feMergeNode",
	"feMorphology",
	"feOffset",
	"fePointLight",
	"feSpecularLighting",
	"feSpotLight",
	"feTile",
	"feTurbulence",
	"foreignObject",
	"glyphRef",
	"linearGradient",
	"radialGradient",
	"solidColor",
	"textArea",
	"textPath"
];
//#endregion
//#region node_modules/.bun/hastscript@9.0.1/node_modules/hastscript/lib/index.js
/**
* @typedef {import('./jsx-classic.js').Element} h.JSX.Element
* @typedef {import('./jsx-classic.js').ElementChildrenAttribute} h.JSX.ElementChildrenAttribute
* @typedef {import('./jsx-classic.js').IntrinsicAttributes} h.JSX.IntrinsicAttributes
* @typedef {import('./jsx-classic.js').IntrinsicElements} h.JSX.IntrinsicElements
*/
/**
* @typedef {import('./jsx-classic.js').Element} s.JSX.Element
* @typedef {import('./jsx-classic.js').ElementChildrenAttribute} s.JSX.ElementChildrenAttribute
* @typedef {import('./jsx-classic.js').IntrinsicAttributes} s.JSX.IntrinsicAttributes
* @typedef {import('./jsx-classic.js').IntrinsicElements} s.JSX.IntrinsicElements
*/
/** @type {ReturnType<createH>} */
var h = createH(html, "div");
/** @type {ReturnType<createH>} */
var s = createH(svg, "g", svgCaseSensitiveTagNames);
//#endregion
//#region node_modules/.bun/vfile-location@5.0.3/node_modules/vfile-location/lib/index.js
/**
* @import {VFile, Value} from 'vfile'
* @import {Location} from 'vfile-location'
*/
/**
* Create an index of the given document to translate between line/column and
* offset based positional info.
*
* Also implemented in Rust in [`wooorm/markdown-rs`][markdown-rs].
*
* [markdown-rs]: https://github.com/wooorm/markdown-rs/blob/main/src/util/location.rs
*
* @param {VFile | Value} file
*   File to index.
* @returns {Location}
*   Accessors for index.
*/
function location(file) {
	const value = String(file);
	/**
	* List, where each index is a line number (0-based), and each value is the
	* byte index *after* where the line ends.
	*
	* @type {Array<number>}
	*/
	const indices = [];
	return {
		toOffset,
		toPoint
	};
	/** @type {Location['toPoint']} */
	function toPoint(offset) {
		if (typeof offset === "number" && offset > -1 && offset <= value.length) {
			let index = 0;
			while (true) {
				let end = indices[index];
				if (end === void 0) {
					const eol = next(value, indices[index - 1]);
					end = eol === -1 ? value.length + 1 : eol + 1;
					indices[index] = end;
				}
				if (end > offset) return {
					line: index + 1,
					column: offset - (index > 0 ? indices[index - 1] : 0) + 1,
					offset
				};
				index++;
			}
		}
	}
	/** @type {Location['toOffset']} */
	function toOffset(point) {
		if (point && typeof point.line === "number" && typeof point.column === "number" && !Number.isNaN(point.line) && !Number.isNaN(point.column)) {
			while (indices.length < point.line) {
				const from = indices[indices.length - 1];
				const eol = next(value, from);
				const end = eol === -1 ? value.length + 1 : eol + 1;
				if (from === end) break;
				indices.push(end);
			}
			const offset = (point.line > 1 ? indices[point.line - 2] : 0) + point.column - 1;
			if (offset < indices[point.line - 1]) return offset;
		}
	}
}
/**
* @param {string} value
* @param {number} from
*/
function next(value, from) {
	const cr = value.indexOf("\r", from);
	const lf = value.indexOf("\n", from);
	if (lf === -1) return cr;
	if (cr === -1 || cr + 1 === lf) return lf;
	return cr < lf ? cr : lf;
}
//#endregion
//#region node_modules/.bun/web-namespaces@2.0.1/node_modules/web-namespaces/index.js
/**
* Map of web namespaces.
*
* @type {Record<string, string>}
*/
var webNamespaces = {
	html: "http://www.w3.org/1999/xhtml",
	mathml: "http://www.w3.org/1998/Math/MathML",
	svg: "http://www.w3.org/2000/svg",
	xlink: "http://www.w3.org/1999/xlink",
	xml: "http://www.w3.org/XML/1998/namespace",
	xmlns: "http://www.w3.org/2000/xmlns/"
};
//#endregion
//#region node_modules/.bun/hast-util-from-parse5@8.0.3/node_modules/hast-util-from-parse5/lib/index.js
/**
* @import {ElementData, Element, Nodes, RootContent, Root} from 'hast'
* @import {DefaultTreeAdapterMap, Token} from 'parse5'
* @import {Schema} from 'property-information'
* @import {Point, Position} from 'unist'
* @import {VFile} from 'vfile'
* @import {Options} from 'hast-util-from-parse5'
*/
/**
* @typedef State
*   Info passed around about the current state.
* @property {VFile | undefined} file
*   Corresponding file.
* @property {boolean} location
*   Whether location info was found.
* @property {Schema} schema
*   Current schema.
* @property {boolean | undefined} verbose
*   Add extra positional info.
*/
var own = {}.hasOwnProperty;
/** @type {unknown} */
var proto = Object.prototype;
/**
* Transform a `parse5` AST to hast.
*
* @param {DefaultTreeAdapterMap['node']} tree
*   `parse5` tree to transform.
* @param {Options | null | undefined} [options]
*   Configuration (optional).
* @returns {Nodes}
*   hast tree.
*/
function fromParse5(tree, options) {
	const settings = options || {};
	return one({
		file: settings.file || void 0,
		location: false,
		schema: settings.space === "svg" ? svg : html,
		verbose: settings.verbose || false
	}, tree);
}
/**
* Transform a node.
*
* @param {State} state
*   Info passed around about the current state.
* @param {DefaultTreeAdapterMap['node']} node
*   p5 node.
* @returns {Nodes}
*   hast node.
*/
function one(state, node) {
	/** @type {Nodes} */
	let result;
	switch (node.nodeName) {
		case "#comment": {
			const reference = node;
			result = {
				type: "comment",
				value: reference.data
			};
			patch(state, reference, result);
			return result;
		}
		case "#document":
		case "#document-fragment": {
			const reference = node;
			const quirksMode = "mode" in reference ? reference.mode === "quirks" || reference.mode === "limited-quirks" : false;
			result = {
				type: "root",
				children: all(state, node.childNodes),
				data: { quirksMode }
			};
			if (state.file && state.location) {
				const document = String(state.file);
				const loc = location(document);
				const start = loc.toPoint(0);
				const end = loc.toPoint(document.length);
				result.position = {
					start,
					end
				};
			}
			return result;
		}
		case "#documentType": {
			const reference = node;
			result = { type: "doctype" };
			patch(state, reference, result);
			return result;
		}
		case "#text": {
			const reference = node;
			result = {
				type: "text",
				value: reference.value
			};
			patch(state, reference, result);
			return result;
		}
		default:
			result = element(state, node);
			return result;
	}
}
/**
* Transform children.
*
* @param {State} state
*   Info passed around about the current state.
* @param {Array<DefaultTreeAdapterMap['node']>} nodes
*   Nodes.
* @returns {Array<RootContent>}
*   hast nodes.
*/
function all(state, nodes) {
	let index = -1;
	/** @type {Array<RootContent>} */
	const results = [];
	while (++index < nodes.length) {
		const result = one(state, nodes[index]);
		results.push(result);
	}
	return results;
}
/**
* Transform an element.
*
* @param {State} state
*   Info passed around about the current state.
* @param {DefaultTreeAdapterMap['element']} node
*   `parse5` node to transform.
* @returns {Element}
*   hast node.
*/
function element(state, node) {
	const schema = state.schema;
	state.schema = node.namespaceURI === webNamespaces.svg ? svg : html;
	let index = -1;
	/** @type {Record<string, string>} */
	const properties = {};
	while (++index < node.attrs.length) {
		const attribute = node.attrs[index];
		const name = (attribute.prefix ? attribute.prefix + ":" : "") + attribute.name;
		if (!own.call(proto, name)) properties[name] = attribute.value;
	}
	const result = (state.schema.space === "svg" ? s : h)(node.tagName, properties, all(state, node.childNodes));
	patch(state, node, result);
	if (result.tagName === "template") {
		const reference = node;
		const pos = reference.sourceCodeLocation;
		const startTag = pos && pos.startTag && position(pos.startTag);
		const endTag = pos && pos.endTag && position(pos.endTag);
		const content = one(state, reference.content);
		if (startTag && endTag && state.file) content.position = {
			start: startTag.end,
			end: endTag.start
		};
		result.content = content;
	}
	state.schema = schema;
	return result;
}
/**
* Patch positional info from `from` onto `to`.
*
* @param {State} state
*   Info passed around about the current state.
* @param {DefaultTreeAdapterMap['node']} from
*   p5 node.
* @param {Nodes} to
*   hast node.
* @returns {undefined}
*   Nothing.
*/
function patch(state, from, to) {
	if ("sourceCodeLocation" in from && from.sourceCodeLocation && state.file) {
		const position = createLocation(state, to, from.sourceCodeLocation);
		if (position) {
			state.location = true;
			to.position = position;
		}
	}
}
/**
* Create clean positional information.
*
* @param {State} state
*   Info passed around about the current state.
* @param {Nodes} node
*   hast node.
* @param {Token.ElementLocation} location
*   p5 location info.
* @returns {Position | undefined}
*   Position, or nothing.
*/
function createLocation(state, node, location) {
	const result = position(location);
	if (node.type === "element") {
		const tail = node.children[node.children.length - 1];
		if (result && !location.endTag && tail && tail.position && tail.position.end) result.end = Object.assign({}, tail.position.end);
		if (state.verbose) {
			/** @type {Record<string, Position | undefined>} */
			const properties = {};
			/** @type {string} */
			let key;
			if (location.attrs) {
				for (key in location.attrs) if (own.call(location.attrs, key)) properties[find(state.schema, key).property] = position(location.attrs[key]);
			}
			location.startTag;
			const opening = position(location.startTag);
			const closing = location.endTag ? position(location.endTag) : void 0;
			/** @type {ElementData['position']} */
			const data = { opening };
			if (closing) data.closing = closing;
			data.properties = properties;
			node.data = { position: data };
		}
	}
	return result;
}
/**
* Turn a p5 location into a position.
*
* @param {Token.Location} loc
*   Location.
* @returns {Position | undefined}
*   Position or nothing.
*/
function position(loc) {
	const start = point({
		line: loc.startLine,
		column: loc.startCol,
		offset: loc.startOffset
	});
	const end = point({
		line: loc.endLine,
		column: loc.endCol,
		offset: loc.endOffset
	});
	return start || end ? {
		start,
		end
	} : void 0;
}
/**
* Filter out invalid points.
*
* @param {Point} point
*   Point with potentially `undefined` values.
* @returns {Point | undefined}
*   Point or nothing.
*/
function point(point) {
	return point.line && point.column ? point : void 0;
}
//#endregion
export { svg as a, html as i, webNamespaces as n, find as o, stringify as r, hastToReact as s, fromParse5 as t };
