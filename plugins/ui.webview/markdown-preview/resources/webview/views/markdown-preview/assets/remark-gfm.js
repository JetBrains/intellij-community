import { n as gfmToMarkdown, t as gfmFromMarkdown } from "./mdast-util-gfm.js";
import { t as gfm } from "./micromark-extension-gfm.js";
/**
* @import {Root} from 'mdast'
* @import {Options} from 'remark-gfm'
* @import {} from 'remark-parse'
* @import {} from 'remark-stringify'
* @import {Processor} from 'unified'
*/
/** @type {Options} */
var emptyOptions = {};
/**
* Add support GFM (autolink literals, footnotes, strikethrough, tables,
* tasklists).
*
* @param {Options | null | undefined} [options]
*   Configuration (optional).
* @returns {undefined}
*   Nothing.
*/
function remarkGfm(options) {
	const self = this;
	const settings = options || emptyOptions;
	const data = self.data();
	const micromarkExtensions = data.micromarkExtensions || (data.micromarkExtensions = []);
	const fromMarkdownExtensions = data.fromMarkdownExtensions || (data.fromMarkdownExtensions = []);
	const toMarkdownExtensions = data.toMarkdownExtensions || (data.toMarkdownExtensions = []);
	micromarkExtensions.push(gfm(settings));
	fromMarkdownExtensions.push(gfmFromMarkdown());
	toMarkdownExtensions.push(gfmToMarkdown(settings));
}
export { remarkGfm as t };
