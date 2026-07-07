import { l as position, o as esm_default } from "./hast-util-raw.js";
/**
* @import {Schema} from 'hast-util-sanitize'
*/
var aria = [
	"ariaDescribedBy",
	"ariaLabel",
	"ariaLabelledBy"
];
/**
* Default schema.
*
* Follows GitHub style sanitation.
*
* @type {Schema}
*/
var defaultSchema = {
	ancestors: {
		tbody: ["table"],
		td: ["table"],
		th: ["table"],
		thead: ["table"],
		tfoot: ["table"],
		tr: ["table"]
	},
	attributes: {
		a: [
			...aria,
			"dataFootnoteBackref",
			"dataFootnoteRef",
			["className", "data-footnote-backref"],
			"href"
		],
		blockquote: ["cite"],
		code: [["className", /^language-./]],
		del: ["cite"],
		div: ["itemScope", "itemType"],
		dl: [...aria],
		h2: [["className", "sr-only"]],
		img: [
			...aria,
			"longDesc",
			"src"
		],
		input: [["disabled", true], ["type", "checkbox"]],
		ins: ["cite"],
		li: [["className", "task-list-item"]],
		ol: [...aria, ["className", "contains-task-list"]],
		q: ["cite"],
		section: ["dataFootnotes", ["className", "footnotes"]],
		source: ["srcSet"],
		summary: [...aria],
		table: [...aria],
		ul: [...aria, ["className", "contains-task-list"]],
		"*": [
			"abbr",
			"accept",
			"acceptCharset",
			"accessKey",
			"action",
			"align",
			"alt",
			"axis",
			"border",
			"cellPadding",
			"cellSpacing",
			"char",
			"charOff",
			"charSet",
			"checked",
			"clear",
			"colSpan",
			"color",
			"cols",
			"compact",
			"coords",
			"dateTime",
			"dir",
			"encType",
			"frame",
			"hSpace",
			"headers",
			"height",
			"hrefLang",
			"htmlFor",
			"id",
			"isMap",
			"itemProp",
			"label",
			"lang",
			"maxLength",
			"media",
			"method",
			"multiple",
			"name",
			"noHref",
			"noShade",
			"noWrap",
			"open",
			"prompt",
			"readOnly",
			"rev",
			"rowSpan",
			"rows",
			"rules",
			"scope",
			"selected",
			"shape",
			"size",
			"span",
			"start",
			"summary",
			"tabIndex",
			"title",
			"useMap",
			"vAlign",
			"value",
			"width"
		]
	},
	clobber: [
		"ariaDescribedBy",
		"ariaLabelledBy",
		"id",
		"name"
	],
	clobberPrefix: "user-content-",
	protocols: {
		cite: ["http", "https"],
		href: [
			"http",
			"https",
			"irc",
			"ircs",
			"mailto",
			"xmpp"
		],
		longDesc: ["http", "https"],
		src: ["http", "https"]
	},
	required: { input: {
		disabled: true,
		type: "checkbox"
	} },
	strip: ["script"],
	tagNames: [
		"a",
		"b",
		"blockquote",
		"br",
		"code",
		"dd",
		"del",
		"details",
		"div",
		"dl",
		"dt",
		"em",
		"h1",
		"h2",
		"h3",
		"h4",
		"h5",
		"h6",
		"hr",
		"i",
		"img",
		"input",
		"ins",
		"kbd",
		"li",
		"ol",
		"p",
		"picture",
		"pre",
		"q",
		"rp",
		"rt",
		"ruby",
		"s",
		"samp",
		"section",
		"source",
		"span",
		"strike",
		"strong",
		"sub",
		"summary",
		"sup",
		"table",
		"tbody",
		"td",
		"tfoot",
		"th",
		"thead",
		"tr",
		"tt",
		"ul",
		"var"
	]
};
/**
* @import {
*   Comment,
*   Doctype,
*   ElementContent,
*   Element,
*   Nodes,
*   Properties,
*   RootContent,
*   Root,
*   Text
* } from 'hast'
*/
/**
* @typedef {[string, ...Array<Exclude<Properties[keyof Properties], Array<any>> | RegExp>] | string} PropertyDefinition
*   Definition for a property.
*
* @typedef Schema
*   Schema that defines what nodes and properties are allowed.
*
*   The default schema is `defaultSchema`, which follows how GitHub cleans.
*   If any top-level key is missing in the given schema, the corresponding
*   value of the default schema is used.
*
*   To extend the standard schema with a few changes, clone `defaultSchema`
*   like so:
*
*   ```js
*   import deepmerge from 'deepmerge'
*   import {h} from 'hastscript'
*   import {defaultSchema, sanitize} from 'hast-util-sanitize'
*
*   // This allows `className` on all elements.
*   const schema = deepmerge(defaultSchema, {attributes: {'*': ['className']}})
*
*   const tree = sanitize(h('div', {className: ['foo']}), schema)
*
*   // `tree` still has `className`.
*   console.log(tree)
*   // {
*   //   type: 'element',
*   //   tagName: 'div',
*   //   properties: {className: ['foo']},
*   //   children: []
*   // }
*   ```
* @property {boolean | null | undefined} [allowComments=false]
*   Whether to allow comment nodes (default: `false`).
*
*   For example:
*
*   ```js
*   allowComments: true
*   ```
* @property {boolean | null | undefined} [allowDoctypes=false]
*   Whether to allow doctype nodes (default: `false`).
*
*   For example:
*
*   ```js
*   allowDoctypes: true
*   ```
* @property {Record<string, Array<string>> | null | undefined} [ancestors]
*   Map of tag names to a list of tag names which are required ancestors
*   (default: `defaultSchema.ancestors`).
*
*   Elements with these tag names will be ignored if they occur outside of one
*   of their allowed parents.
*
*   For example:
*
*   ```js
*   ancestors: {
*     tbody: ['table'],
*     // …
*     tr: ['table']
*   }
*   ```
* @property {Record<string, Array<PropertyDefinition>> | null | undefined} [attributes]
*   Map of tag names to allowed property names (default:
*   `defaultSchema.attributes`).
*
*   The special key `'*'` as a tag name defines property names allowed on all
*   elements.
*
*   The special value `'data*'` as a property name can be used to allow all
*   `data` properties.
*
*   For example:
*
*   ```js
*   attributes: {
*     'ariaDescribedBy', 'ariaLabel', 'ariaLabelledBy', …, 'href'
*     // …
*     '*': [
*       'abbr',
*       'accept',
*       'acceptCharset',
*       // …
*       'vAlign',
*       'value',
*       'width'
*     ]
*   }
*   ```
*
*   Instead of a single string in the array, which allows any property value
*   for the field, you can use an array to allow several values.
*   For example, `input: ['type']` allows `type` set to any value on `input`s.
*   But `input: [['type', 'checkbox', 'radio']]` allows `type` when set to
*   `'checkbox'` or `'radio'`.
*
*   You can use regexes, so for example `span: [['className', /^hljs-/]]`
*   allows any class that starts with `hljs-` on `span`s.
*
*   When comma- or space-separated values are used (such as `className`), each
*   value in is checked individually.
*   For example, to allow certain classes on `span`s for syntax highlighting,
*   use `span: [['className', 'number', 'operator', 'token']]`.
*   This will allow `'number'`, `'operator'`, and `'token'` classes, but drop
*   others.
* @property {Array<string> | null | undefined} [clobber]
*   List of property names that clobber (default: `defaultSchema.clobber`).
*
*   For example:
*
*   ```js
*   clobber: ['ariaDescribedBy', 'ariaLabelledBy', 'id', 'name']
*   ```
* @property {string | null | undefined} [clobberPrefix]
*   Prefix to use before clobbering properties (default:
*   `defaultSchema.clobberPrefix`).
*
*   For example:
*
*   ```js
*   clobberPrefix: 'user-content-'
*   ```
* @property {Record<string, Array<string> | null | undefined> | null | undefined} [protocols]
*   Map of *property names* to allowed protocols (default:
*   `defaultSchema.protocols`).
*
*   This defines URLs that are always allowed to have local URLs (relative to
*   the current website, such as `this`, `#this`, `/this`, or `?this`), and
*   only allowed to have remote URLs (such as `https://example.com`) if they
*   use a known protocol.
*
*   For example:
*
*   ```js
*   protocols: {
*     cite: ['http', 'https'],
*     // …
*     src: ['http', 'https']
*   }
*   ```
* @property {Record<string, Record<string, Properties[keyof Properties]>> | null | undefined} [required]
*   Map of tag names to required property names with a default value
*   (default: `defaultSchema.required`).
*
*   This defines properties that must be set.
*   If a field does not exist (after the element was made safe), these will be
*   added with the given value.
*
*   For example:
*
*   ```js
*   required: {
*     input: {disabled: true, type: 'checkbox'}
*   }
*   ```
*
*   > 👉 **Note**: properties are first checked based on `schema.attributes`,
*   > then on `schema.required`.
*   > That means properties could be removed by `attributes` and then added
*   > again with `required`.
* @property {Array<string> | null | undefined} [strip]
*   List of tag names to strip from the tree (default: `defaultSchema.strip`).
*
*   By default, unsafe elements (those not in `schema.tagNames`) are replaced
*   by what they contain.
*   This option can drop their contents.
*
*   For example:
*
*   ```js
*   strip: ['script']
*   ```
* @property {Array<string> | null | undefined} [tagNames]
*   List of allowed tag names (default: `defaultSchema.tagNames`).
*
*   For example:
*
*   ```js
*   tagNames: [
*     'a',
*     'b',
*     // …
*     'ul',
*     'var'
*   ]
*   ```
*
* @typedef State
*   Info passed around.
* @property {Readonly<Schema>} schema
*   Schema.
* @property {Array<string>} stack
*   Tag names of ancestors.
*/
var own = {}.hasOwnProperty;
/**
* Sanitize a tree.
*
* @param {Readonly<Nodes>} node
*   Unsafe tree.
* @param {Readonly<Schema> | null | undefined} [options]
*   Configuration (default: `defaultSchema`).
* @returns {Nodes}
*   New, safe tree.
*/
function sanitize(node, options) {
	/** @type {Nodes} */
	let result = {
		type: "root",
		children: []
	};
	const replace = transform({
		schema: options ? {
			...defaultSchema,
			...options
		} : defaultSchema,
		stack: []
	}, node);
	if (replace) if (Array.isArray(replace)) if (replace.length === 1) result = replace[0];
	else result.children = replace;
	else result = replace;
	return result;
}
/**
* Sanitize `node`.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<unknown>} node
*   Unsafe node.
* @returns {Array<ElementContent> | Nodes | undefined}
*   Safe result.
*/
function transform(state, node) {
	if (node && typeof node === "object") {
		const unsafe = node;
		switch (typeof unsafe.type === "string" ? unsafe.type : "") {
			case "comment": return comment(state, unsafe);
			case "doctype": return doctype(state, unsafe);
			case "element": return element(state, unsafe);
			case "root": return root(state, unsafe);
			case "text": return text(state, unsafe);
			default:
		}
	}
}
/**
* Make a safe comment.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<Record<string, Readonly<unknown>>>} unsafe
*   Unsafe comment-like value.
* @returns {Comment | undefined}
*   Safe comment (if with `allowComments`).
*/
function comment(state, unsafe) {
	if (state.schema.allowComments) {
		const result = typeof unsafe.value === "string" ? unsafe.value : "";
		const index = result.indexOf("-->");
		/** @type {Comment} */
		const node = {
			type: "comment",
			value: index < 0 ? result : result.slice(0, index)
		};
		patch(node, unsafe);
		return node;
	}
}
/**
* Make a safe doctype.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<Record<string, Readonly<unknown>>>} unsafe
*   Unsafe doctype-like value.
* @returns {Doctype | undefined}
*   Safe doctype (if with `allowDoctypes`).
*/
function doctype(state, unsafe) {
	if (state.schema.allowDoctypes) {
		/** @type {Doctype} */
		const node = { type: "doctype" };
		patch(node, unsafe);
		return node;
	}
}
/**
* Make a safe element.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<Record<string, Readonly<unknown>>>} unsafe
*   Unsafe element-like value.
* @returns {Array<ElementContent> | Element | undefined}
*   Safe element.
*/
function element(state, unsafe) {
	const name = typeof unsafe.tagName === "string" ? unsafe.tagName : "";
	state.stack.push(name);
	const content = children(state, unsafe.children);
	const properties_ = properties(state, unsafe.properties);
	state.stack.pop();
	let safeElement = false;
	if (name && name !== "*" && (!state.schema.tagNames || state.schema.tagNames.includes(name))) {
		safeElement = true;
		if (state.schema.ancestors && own.call(state.schema.ancestors, name)) {
			const ancestors = state.schema.ancestors[name];
			let index = -1;
			safeElement = false;
			while (++index < ancestors.length) if (state.stack.includes(ancestors[index])) safeElement = true;
		}
	}
	if (!safeElement) return state.schema.strip && !state.schema.strip.includes(name) ? content : void 0;
	/** @type {Element} */
	const node = {
		type: "element",
		tagName: name,
		properties: properties_,
		children: content
	};
	patch(node, unsafe);
	return node;
}
/**
* Make a safe root.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<Record<string, Readonly<unknown>>>} unsafe
*   Unsafe root-like value.
* @returns {Root}
*   Safe root.
*/
function root(state, unsafe) {
	/** @type {Root} */
	const node = {
		type: "root",
		children: children(state, unsafe.children)
	};
	patch(node, unsafe);
	return node;
}
/**
* Make a safe text.
*
* @param {State} _
*   Info passed around.
* @param {Readonly<Record<string, Readonly<unknown>>>} unsafe
*   Unsafe text-like value.
* @returns {Text}
*   Safe text.
*/
function text(_, unsafe) {
	/** @type {Text} */
	const node = {
		type: "text",
		value: typeof unsafe.value === "string" ? unsafe.value : ""
	};
	patch(node, unsafe);
	return node;
}
/**
* Make children safe.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<unknown>} children
*   Unsafe value.
* @returns {Array<Nodes>}
*   Safe children.
*/
function children(state, children) {
	/** @type {Array<Nodes>} */
	const results = [];
	if (Array.isArray(children)) {
		const childrenUnknown = children;
		let index = -1;
		while (++index < childrenUnknown.length) {
			const value = transform(state, childrenUnknown[index]);
			if (value) if (Array.isArray(value)) results.push(...value);
			else results.push(value);
		}
	}
	return results;
}
/**
* Make element properties safe.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<unknown>} properties
*   Unsafe value.
* @returns {Properties}
*   Safe value.
*/
function properties(state, properties) {
	const tagName = state.stack[state.stack.length - 1];
	const attributes = state.schema.attributes;
	const required = state.schema.required;
	const specific = attributes && own.call(attributes, tagName) ? attributes[tagName] : void 0;
	const defaults = attributes && own.call(attributes, "*") ? attributes["*"] : void 0;
	const properties_ = properties && typeof properties === "object" ? properties : {};
	/** @type {Properties} */
	const result = {};
	/** @type {string} */
	let key;
	for (key in properties_) if (own.call(properties_, key)) {
		const unsafe = properties_[key];
		let safe = propertyValue(state, findDefinition(specific, key), key, unsafe);
		if (safe === null || safe === void 0) safe = propertyValue(state, findDefinition(defaults, key), key, unsafe);
		if (safe !== null && safe !== void 0) result[key] = safe;
	}
	if (required && own.call(required, tagName)) {
		const properties = required[tagName];
		for (key in properties) if (own.call(properties, key) && !own.call(result, key)) result[key] = properties[key];
	}
	return result;
}
/**
* Sanitize a property value.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<PropertyDefinition> | undefined} definition
*   Definition.
* @param {string} key
*   Field name.
* @param {Readonly<unknown>} value
*   Unsafe value (but an array).
* @returns {Array<number | string> | boolean | number | string | undefined}
*   Safe value.
*/
function propertyValue(state, definition, key, value) {
	return definition ? Array.isArray(value) ? propertyValueMany(state, definition, key, value) : propertyValuePrimitive(state, definition, key, value) : void 0;
}
/**
* Sanitize a property value which is a list.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<PropertyDefinition>} definition
*   Definition.
* @param {string} key
*   Field name.
* @param {Readonly<Array<Readonly<unknown>>>} values
*   Unsafe value (but an array).
* @returns {Array<number | string>}
*   Safe value.
*/
function propertyValueMany(state, definition, key, values) {
	let index = -1;
	/** @type {Array<number | string>} */
	const result = [];
	while (++index < values.length) {
		const value = propertyValuePrimitive(state, definition, key, values[index]);
		if (typeof value === "number" || typeof value === "string") result.push(value);
	}
	return result;
}
/**
* Sanitize a property value which is a primitive.
*
* @param {State} state
*   Info passed around.
* @param {Readonly<PropertyDefinition>} definition
*   Definition.
* @param {string} key
*   Field name.
* @param {Readonly<unknown>} value
*   Unsafe value (but not an array).
* @returns {boolean | number | string | undefined}
*   Safe value.
*/
function propertyValuePrimitive(state, definition, key, value) {
	if (typeof value !== "boolean" && typeof value !== "number" && typeof value !== "string") return;
	if (!safeProtocol(state, key, value)) return;
	if (typeof definition === "object" && definition.length > 1) {
		let ok = false;
		let index = 0;
		while (++index < definition.length) {
			const allowed = definition[index];
			if (allowed && typeof allowed === "object" && "flags" in allowed) {
				if (allowed.test(String(value))) {
					ok = true;
					break;
				}
			} else if (allowed === value) {
				ok = true;
				break;
			}
		}
		if (!ok) return;
	}
	return state.schema.clobber && state.schema.clobberPrefix && state.schema.clobber.includes(key) ? state.schema.clobberPrefix + value : value;
}
/**
* Check whether `value` is a safe URL.
*
* @param {State} state
*   Info passed around.
* @param {string} key
*   Field name.
* @param {Readonly<unknown>} value
*   Unsafe value.
* @returns {boolean}
*   Whether it’s a safe value.
*/
function safeProtocol(state, key, value) {
	const protocols = state.schema.protocols && own.call(state.schema.protocols, key) ? state.schema.protocols[key] : void 0;
	if (!protocols || protocols.length === 0) return true;
	const url = String(value);
	const colon = url.indexOf(":");
	const questionMark = url.indexOf("?");
	const numberSign = url.indexOf("#");
	const slash = url.indexOf("/");
	if (colon < 0 || slash > -1 && colon > slash || questionMark > -1 && colon > questionMark || numberSign > -1 && colon > numberSign) return true;
	let index = -1;
	while (++index < protocols.length) {
		const protocol = protocols[index];
		if (colon === protocol.length && url.slice(0, protocol.length) === protocol) return true;
	}
	return false;
}
/**
* Add data and position.
*
* @param {Nodes} node
*   Node to patch safe data and position on.
* @param {Readonly<Record<string, Readonly<unknown>>>} unsafe
*   Unsafe node-like value.
* @returns {undefined}
*   Nothing.
*/
function patch(node, unsafe) {
	const cleanPosition = position(unsafe);
	if (unsafe.data) node.data = esm_default(unsafe.data);
	if (cleanPosition) node.position = cleanPosition;
}
/**
*
* @param {Readonly<Array<PropertyDefinition>> | undefined} definitions
* @param {string} key
* @returns {Readonly<PropertyDefinition> | undefined}
*/
function findDefinition(definitions, key) {
	/** @type {PropertyDefinition | undefined} */
	let dataDefault;
	let index = -1;
	if (definitions) while (++index < definitions.length) {
		const entry = definitions[index];
		const name = typeof entry === "string" ? entry : entry[0];
		if (name === key) return entry;
		if (name === "data*") dataDefault = entry;
	}
	if (key.length > 4 && key.slice(0, 4).toLowerCase() === "data") return dataDefault;
}
export { defaultSchema as n, sanitize as t };
