// TARGET_CLASS: I

// INFO: {"checked": "true"}
interface X

// INFO: {"checked": "false"}
interface Y

interface <caret>B : I, X, Y
