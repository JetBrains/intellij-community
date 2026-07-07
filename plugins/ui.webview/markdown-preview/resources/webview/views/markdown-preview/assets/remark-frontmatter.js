import { n as frontmatterToMarkdown, t as frontmatterFromMarkdown } from "./mdast-util-frontmatter.js";
import { t as frontmatter } from "./micromark-extension-frontmatter.js";
//#region node_modules/.bun/remark-frontmatter@5.0.0/node_modules/remark-frontmatter/lib/index.js
/**
* @typedef {import('mdast').Root} Root
* @typedef {import('micromark-extension-frontmatter').Options} Options
* @typedef {import('unified').Processor<Root>} Processor
*/
/** @type {Options} */
var emptyOptions = "yaml";
/**
* Add support for frontmatter.
*
* ###### Notes
*
* Doesn’t parse the data inside them: create your own plugin to do that.
*
* @param {Options | null | undefined} [options='yaml']
*   Configuration (default: `'yaml'`).
* @returns {undefined}
*   Nothing.
*/
function remarkFrontmatter(options) {
	const self = this;
	const settings = options || emptyOptions;
	const data = self.data();
	const micromarkExtensions = data.micromarkExtensions || (data.micromarkExtensions = []);
	const fromMarkdownExtensions = data.fromMarkdownExtensions || (data.fromMarkdownExtensions = []);
	const toMarkdownExtensions = data.toMarkdownExtensions || (data.toMarkdownExtensions = []);
	micromarkExtensions.push(frontmatter(settings));
	fromMarkdownExtensions.push(frontmatterFromMarkdown(settings));
	toMarkdownExtensions.push(frontmatterToMarkdown(settings));
}
//#endregion
export { remarkFrontmatter as t };
