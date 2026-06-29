import { t as fromDom } from "./hast-util-from-dom.js";
//#region node_modules/hast-util-from-html-isomorphic/lib/browser.js
/**
* @typedef {import('hast').Root} Root
*
* @typedef {typeof import('./index.js').fromHtmlIsomorphic} FromHtmlIsomorphic
*/
var parser = new DOMParser();
/** @type {FromHtmlIsomorphic} */
function fromHtmlIsomorphic(value, options) {
	return fromDom(options?.fragment ? parseFragment(value) : parser.parseFromString(value, "text/html"));
}
/**
* Parse as a fragment.
*
* @param {string} value
* @returns {DocumentFragment}
*/
function parseFragment(value) {
	const template = document.createElement("template");
	template.innerHTML = value;
	return template.content;
}
//#endregion
export { fromHtmlIsomorphic as t };
