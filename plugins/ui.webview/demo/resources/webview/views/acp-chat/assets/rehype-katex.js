import { i as visitParents, r as SKIP } from "./hast-util-raw.js";
import { t as toText } from "./hast-util-to-text.js";
import { t as fromHtmlIsomorphic } from "./hast-util-from-html-isomorphic.js";
import { t as katex } from "./katex.js";
//#region node_modules/rehype-katex/lib/index.js
/**
* @import {ElementContent, Root} from 'hast'
* @import {KatexOptions} from 'katex'
* @import {VFile} from 'vfile'
*/
/**
* @typedef {Omit<KatexOptions, 'displayMode' | 'throwOnError'>} Options
*/
/** @type {Readonly<Options>} */
var emptyOptions = {};
/** @type {ReadonlyArray<unknown>} */
var emptyClasses = [];
/**
* Render elements with a `language-math` (or `math-display`, `math-inline`)
* class with KaTeX.
*
* @param {Readonly<Options> | null | undefined} [options]
*   Configuration (optional).
* @returns
*   Transform.
*/
function rehypeKatex(options) {
	const settings = options || emptyOptions;
	/**
	* Transform.
	*
	* @param {Root} tree
	*   Tree.
	* @param {VFile} file
	*   File.
	* @returns {undefined}
	*   Nothing.
	*/
	return function(tree, file) {
		visitParents(tree, "element", function(element, parents) {
			const classes = Array.isArray(element.properties.className) ? element.properties.className : emptyClasses;
			const languageMath = classes.includes("language-math");
			const mathDisplay = classes.includes("math-display");
			const mathInline = classes.includes("math-inline");
			let displayMode = mathDisplay;
			if (!languageMath && !mathDisplay && !mathInline) return;
			let parent = parents[parents.length - 1];
			let scope = element;
			if (element.tagName === "code" && languageMath && parent && parent.type === "element" && parent.tagName === "pre") {
				scope = parent;
				parent = parents[parents.length - 2];
				displayMode = true;
			}
			/* c8 ignore next -- verbose to test. */
			if (!parent) return;
			const value = toText(scope, { whitespace: "pre" });
			/** @type {Array<ElementContent> | string | undefined} */
			let result;
			try {
				result = katex.renderToString(value, {
					...settings,
					displayMode,
					throwOnError: true
				});
			} catch (error) {
				const cause = error;
				const ruleId = cause.name.toLowerCase();
				file.message("Could not render math with KaTeX", {
					ancestors: [...parents, element],
					cause,
					place: element.position,
					ruleId,
					source: "rehype-katex"
				});
				try {
					result = katex.renderToString(value, {
						...settings,
						displayMode,
						strict: "ignore",
						throwOnError: false
					});
				} catch {
					result = [{
						type: "element",
						tagName: "span",
						properties: {
							className: ["katex-error"],
							style: "color:" + (settings.errorColor || "#cc0000"),
							title: String(error)
						},
						children: [{
							type: "text",
							value
						}]
					}];
				}
			}
			if (typeof result === "string") result = fromHtmlIsomorphic(result, { fragment: true }).children;
			const index = parent.children.indexOf(scope);
			parent.children.splice(index, 1, ...result);
			return SKIP;
		});
	};
}
//#endregion
export { rehypeKatex as t };
