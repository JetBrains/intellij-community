function clamp(value, [min, max]) {
	return Math.min(max, Math.max(min, value));
}
export { clamp as t };
