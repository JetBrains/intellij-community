import { m as root, p as Selection } from "./d3.js";
//#region node_modules/d3-selection/src/select.js
function select_default(selector) {
	return typeof selector === "string" ? new Selection([[document.querySelector(selector)]], [document.documentElement]) : new Selection([[selector]], root);
}
//#endregion
export { select_default as t };
