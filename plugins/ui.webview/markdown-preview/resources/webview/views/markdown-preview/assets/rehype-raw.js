import { t as raw } from "./hast-util-raw.js";
//#region node_modules/rehype-raw/lib/index.js
/**
* @typedef {import('hast').Root} Root
* @typedef {import('hast-util-raw').Options} RawOptions
* @typedef {import('vfile').VFile} VFile
*/
/**
* @typedef {Omit<RawOptions, 'file'>} Options
*   Configuration.
*/
/**
* Parse the tree (and raw nodes) again, keeping positional info okay.
*
* @param {Options | null | undefined}  [options]
*   Configuration (optional).
* @returns
*   Transform.
*/
function rehypeRaw(options) {
	/**
	* @param {Root} tree
	*   Tree.
	* @param {VFile} file
	*   File.
	* @returns {Root}
	*   New tree.
	*/
	return function(tree, file) {
		return raw(tree, {
			...options,
			file
		});
	};
}
//#endregion
export { rehypeRaw as t };
