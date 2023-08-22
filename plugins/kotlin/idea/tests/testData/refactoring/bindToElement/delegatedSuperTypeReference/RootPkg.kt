// BIND_TO B
interface A

interface B

interface Base : A, B

class Delegation(base: Base) : <caret>A by base