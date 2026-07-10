/**
* @import {Nodes, Parents} from 'hast'
*/
/**
* Get the plain-text value of a hast node.
*
* @param {Nodes} node
*   Node to serialize.
* @returns {string}
*   Serialized node.
*/
function toString(node) {
	if ("children" in node) return all(node);
	return "value" in node ? node.value : "";
}
/**
* @param {Nodes} node
*   Node.
* @returns {string}
*   Serialized node.
*/
function one(node) {
	if (node.type === "text") return node.value;
	return "children" in node ? all(node) : "";
}
/**
* @param {Parents} node
*   Node.
* @returns {string}
*   Serialized node.
*/
function all(node) {
	let index = -1;
	/** @type {Array<string>} */
	const result = [];
	while (++index < node.children.length) result[index] = one(node.children[index]);
	return result.join("");
}
export { toString as t };
