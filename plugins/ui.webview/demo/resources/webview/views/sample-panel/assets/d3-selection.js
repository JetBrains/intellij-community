import { m as root, p as Selection } from "./d3.js";
function select_default(selector) {
	return typeof selector === "string" ? new Selection([[document.querySelector(selector)]], [document.documentElement]) : new Selection([[selector]], root);
}
export { select_default as t };
