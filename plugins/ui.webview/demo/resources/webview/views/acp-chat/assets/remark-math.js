import { n as mathToMarkdown, t as mathFromMarkdown } from "./mdast-util-math.js";
import { t as math } from "./micromark-extension-math.js";
//#region node_modules/remark-math/lib/index.js
/**
* @typedef {import('mdast').Root} Root
* @typedef {import('mdast-util-math').ToOptions} Options
* @typedef {import('unified').Processor<Root>} Processor
*/
/** @type {Readonly<Options>} */
var emptyOptions = {};
/**
* Add support for math.
*
* @param {Readonly<Options> | null | undefined} [options]
*   Configuration (optional).
* @returns {undefined}
*   Nothing.
*/
function remarkMath(options) {
	const self = this;
	const settings = options || emptyOptions;
	const data = self.data();
	const micromarkExtensions = data.micromarkExtensions || (data.micromarkExtensions = []);
	const fromMarkdownExtensions = data.fromMarkdownExtensions || (data.fromMarkdownExtensions = []);
	const toMarkdownExtensions = data.toMarkdownExtensions || (data.toMarkdownExtensions = []);
	micromarkExtensions.push(math(settings));
	fromMarkdownExtensions.push(mathFromMarkdown());
	toMarkdownExtensions.push(mathToMarkdown(settings));
}
//#endregion
export { remarkMath as t };
