import "./devlop.js";
import { t as fault } from "./fault.js";
import { t as escapeStringRegexp } from "./escape-string-regexp.js";
//#region node_modules/.bun/micromark-extension-frontmatter@2.0.0/node_modules/micromark-extension-frontmatter/lib/to-matters.js
/**
* @typedef {'toml' | 'yaml'} Preset
*   Known name of a frontmatter style.
*
* @typedef Info
*   Sequence.
*
*   Depending on how this structure is used, it reflects a marker or a fence.
* @property {string} close
*   Closing.
* @property {string} open
*   Opening.
*
* @typedef MatterProps
*   Fields describing a kind of matter.
* @property {string} type
*   Node type to tokenize as.
* @property {boolean | null | undefined} [anywhere=false]
*   Whether matter can be found anywhere in the document, normally, only matter
*   at the start of the document is recognized.
*
*   > 👉 **Note**: using this is a terrible idea.
*   > It’s called frontmatter, not matter-in-the-middle or so.
*   > This makes your markdown less portable.
*
* @typedef MarkerProps
*   Marker configuration.
* @property {Info | string} marker
*   Character repeated 3 times, used as complete fences.
*
*   For example the character `'-'` will result in `'---'` being used as the
*   fence
*   Pass `open` and `close` to specify different characters for opening and
*   closing fences.
* @property {never} [fence]
*   If `marker` is set, `fence` must not be set.
*
* @typedef FenceProps
*   Fence configuration.
* @property {Info | string} fence
*   Complete fences.
*
*   This can be used when fences contain different characters or lengths
*   other than 3.
*   Pass `open` and `close` to interface to specify different characters for opening and
*   closing fences.
* @property {never} [marker]
*   If `fence` is set, `marker` must not be set.
*
* @typedef {(MatterProps & FenceProps) | (MatterProps & MarkerProps)} Matter
*   Fields describing a kind of matter.
*
*   > 👉 **Note**: using `anywhere` is a terrible idea.
*   > It’s called frontmatter, not matter-in-the-middle or so.
*   > This makes your markdown less portable.
*
*   > 👉 **Note**: `marker` and `fence` are mutually exclusive.
*   > If `marker` is set, `fence` must not be set, and vice versa.
*
* @typedef {Matter | Preset | Array<Matter | Preset>} Options
*   Configuration.
*/
var own = {}.hasOwnProperty;
var markers = {
	yaml: "-",
	toml: "+"
};
/**
* Simplify options by normalizing them to an array of matters.
*
* @param {Options | null | undefined} [options='yaml']
*   Configuration (default: `'yaml'`).
* @returns {Array<Matter>}
*   List of matters.
*/
function toMatters(options) {
	/** @type {Array<Matter>} */
	const result = [];
	let index = -1;
	/** @type {Array<Matter | Preset>} */
	const presetsOrMatters = Array.isArray(options) ? options : options ? [options] : ["yaml"];
	while (++index < presetsOrMatters.length) result[index] = matter(presetsOrMatters[index]);
	return result;
}
/**
* Simplify an option.
*
* @param {Matter | Preset} option
*   Configuration.
* @returns {Matter}
*   Matter.
*/
function matter(option) {
	let result = option;
	if (typeof result === "string") {
		if (!own.call(markers, result)) throw fault("Missing matter definition for `%s`", result);
		result = {
			type: result,
			marker: markers[result]
		};
	} else if (typeof result !== "object") throw fault("Expected matter to be an object, not `%j`", result);
	if (!own.call(result, "type")) throw fault("Missing `type` in matter `%j`", result);
	if (!own.call(result, "fence") && !own.call(result, "marker")) throw fault("Missing `marker` or `fence` in matter `%j`", result);
	return result;
}
//#endregion
//#region node_modules/.bun/mdast-util-frontmatter@2.0.1/node_modules/mdast-util-frontmatter/lib/index.js
/**
* @typedef {import('mdast').Literal} Literal
*
* @typedef {import('mdast-util-from-markdown').CompileContext} CompileContext
* @typedef {import('mdast-util-from-markdown').Extension} FromMarkdownExtension
* @typedef {import('mdast-util-from-markdown').Handle} FromMarkdownHandle
* @typedef {import('mdast-util-to-markdown').Options} ToMarkdownExtension
*
* @typedef {import('micromark-extension-frontmatter').Info} Info
* @typedef {import('micromark-extension-frontmatter').Matter} Matter
* @typedef {import('micromark-extension-frontmatter').Options} Options
*/
/**
* Create an extension for `mdast-util-from-markdown`.
*
* @param {Options | null | undefined} [options]
*   Configuration (optional).
* @returns {FromMarkdownExtension}
*   Extension for `mdast-util-from-markdown`.
*/
function frontmatterFromMarkdown(options) {
	const matters = toMatters(options);
	/** @type {FromMarkdownExtension['enter']} */
	const enter = {};
	/** @type {FromMarkdownExtension['exit']} */
	const exit = {};
	let index = -1;
	while (++index < matters.length) {
		const matter = matters[index];
		enter[matter.type] = opener(matter);
		exit[matter.type] = close;
		exit[matter.type + "Value"] = value;
	}
	return {
		enter,
		exit
	};
}
/**
* @param {Matter} matter
* @returns {FromMarkdownHandle} enter
*/
function opener(matter) {
	return open;
	/**
	* @this {CompileContext}
	* @type {FromMarkdownHandle}
	*/
	function open(token) {
		this.enter({
			type: matter.type,
			value: ""
		}, token);
		this.buffer();
	}
}
/**
* @this {CompileContext}
* @type {FromMarkdownHandle}
*/
function close(token) {
	const data = this.resume();
	const node = this.stack[this.stack.length - 1];
	"value" in node;
	this.exit(token);
	node.value = data.replace(/^(\r?\n|\r)|(\r?\n|\r)$/g, "");
}
/**
* @this {CompileContext}
* @type {FromMarkdownHandle}
*/
function value(token) {
	this.config.enter.data.call(this, token);
	this.config.exit.data.call(this, token);
}
/**
* Create an extension for `mdast-util-to-markdown`.
*
* @param {Options | null | undefined} [options]
*   Configuration (optional).
* @returns {ToMarkdownExtension}
*   Extension for `mdast-util-to-markdown`.
*/
function frontmatterToMarkdown(options) {
	/** @type {ToMarkdownExtension['unsafe']} */
	const unsafe = [];
	/** @type {ToMarkdownExtension['handlers']} */
	const handlers = {};
	const matters = toMatters(options);
	let index = -1;
	while (++index < matters.length) {
		const matter = matters[index];
		handlers[matter.type] = handler(matter);
		const open = fence(matter, "open");
		unsafe.push({
			atBreak: true,
			character: open.charAt(0),
			after: escapeStringRegexp(open.charAt(1))
		});
	}
	return {
		unsafe,
		handlers
	};
}
/**
* Create a handle that can serialize a frontmatter node as markdown.
*
* @param {Matter} matter
*   Structure.
* @returns {(node: Literal) => string} enter
*   Handler.
*/
function handler(matter) {
	const open = fence(matter, "open");
	const close = fence(matter, "close");
	return handle;
	/**
	* Serialize a frontmatter node as markdown.
	*
	* @param {Literal} node
	*   Node to serialize.
	* @returns {string}
	*   Serialized node.
	*/
	function handle(node) {
		return open + (node.value ? "\n" + node.value : "") + "\n" + close;
	}
}
/**
* Get an `open` or `close` fence.
*
* @param {Matter} matter
*   Structure.
* @param {'close' | 'open'} prop
*   Field to get.
* @returns {string}
*   Fence.
*/
function fence(matter, prop) {
	return matter.marker ? pick(matter.marker, prop).repeat(3) : pick(matter.fence, prop);
}
/**
* Take `open` or `close` fields when schema is an info object, or use the
* given value when it is a string.
*
* @param {Info | string} schema
*   Info object or value.
* @param {'close' | 'open'} prop
*   Field to get.
* @returns {string}
*   Thing to use for the opening or closing.
*/
function pick(schema, prop) {
	return typeof schema === "string" ? schema : schema[prop];
}
//#endregion
export { frontmatterToMarkdown as n, toMatters as r, frontmatterFromMarkdown as t };
