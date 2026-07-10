import { n as visit } from "./hast-util-raw.js";
import { t as BananaSlug } from "./github-slugger.js";
import { t as headingRank } from "./hast-util-heading-rank.js";
import { t as toString } from "./hast-util-to-string.js";
/**
* @typedef {import('hast').Root} Root
*/
/**
* @typedef Options
*   Configuration (optional).
* @property {string} [prefix='']
*   Prefix to add in front of `id`s (default: `''`).
*/
/** @type {Options} */
var emptyOptions = {};
var slugs = new BananaSlug();
/**
* Add `id`s to headings.
*
* @param {Options | null | undefined} [options]
*   Configuration (optional).
* @returns
*   Transform.
*/
function rehypeSlug(options) {
	const prefix = (options || emptyOptions).prefix || "";
	/**
	* @param {Root} tree
	*   Tree.
	* @returns {undefined}
	*   Nothing.
	*/
	return function(tree) {
		slugs.reset();
		visit(tree, "element", function(node) {
			if (headingRank(node) && !node.properties.id) node.properties.id = prefix + slugs.slug(toString(node));
		});
	};
}
export { rehypeSlug as t };
