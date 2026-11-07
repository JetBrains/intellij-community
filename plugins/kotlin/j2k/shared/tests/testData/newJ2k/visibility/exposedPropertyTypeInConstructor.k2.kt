// ERROR: 'public' property exposes its 'internal' type 'B'.
internal class B

class J private constructor(var b: B?)
