import { t as sanitize } from "./hast-util-sanitize.js";
//#region node_modules/rehype-sanitize/lib/index.js
/**
* @typedef {import('hast').Root} Root
* @typedef {import('hast-util-sanitize').Schema} Schema
*/
/**
* Sanitize HTML.
*
* @param {Schema | null | undefined} [options]
*   Configuration (optional).
* @returns
*   Transform.
*/
function rehypeSanitize(options) {
	/**
	* @param {Root} tree
	*   Tree.
	* @returns {Root}
	*   New tree.
	*/
	return function(tree) {
		return sanitize(tree, options);
	};
}
//#endregion
export { rehypeSanitize as t };
