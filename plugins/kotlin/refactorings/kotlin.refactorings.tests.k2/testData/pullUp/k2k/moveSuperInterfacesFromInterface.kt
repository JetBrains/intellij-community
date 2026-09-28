// TARGET_CLASS: Base

interface Base

// INFO: {"checked": "true"}
interface X

// INFO: {"checked": "false"}
interface Y

interface <caret>I : Base, X, Y
