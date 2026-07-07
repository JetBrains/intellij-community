//#region node_modules/.bun/hast-util-heading-rank@3.0.0/node_modules/hast-util-heading-rank/lib/index.js
/**
* @typedef {import('hast').Nodes} Nodes
*/
/**
* Get the rank (`1` to `6`) of headings (`h1` to `h6`).
*
* @param {Nodes} node
*   Node to check.
* @returns {number | undefined}
*   Rank of the heading or `undefined` if not a heading.
*/
function headingRank(node) {
	const name = node.type === "element" ? node.tagName.toLowerCase() : "";
	const code = name.length === 2 && name.charCodeAt(0) === 104 ? name.charCodeAt(1) : 0;
	return code > 48 && code < 55 ? code - 48 : void 0;
}
//#endregion
export { headingRank as t };
