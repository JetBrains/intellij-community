class InputState:
    PURE_ASCII: int
    ESC_ASCII: int
    HIGH_BYTE: int

class LanguageFilter:
    CHINESE_SIMPLIFIED: int
    CHINESE_TRADITIONAL: int
    JAPANESE: int
    KOREAN: int
    NON_CJK: int
    ALL: int
    CHINESE: int
    CJK: int

class ProbingState:
    DETECTING: int
    FOUND_IT: int
    NOT_ME: int

class MachineState:
    START: int
    ERROR: int
    ITS_ME: int

class SequenceLikelihood:
    NEGATIVE: int
    UNLIKELY: int
    LIKELY: int
    POSITIVE: int
    @classmethod
    def get_num_categories(cls) -> int: ...

class CharacterCategory:
    UNDEFINED: int
    LINE_BREAK: int
    SYMBOL: int
    DIGIT: int
    CONTROL: int
