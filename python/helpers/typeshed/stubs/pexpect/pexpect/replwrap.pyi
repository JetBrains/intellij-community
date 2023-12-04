from _typeshed import Incomplete

PY3: Incomplete
basestring = str
PEXPECT_PROMPT: str
PEXPECT_CONTINUATION_PROMPT: str

class REPLWrapper:
    child: Incomplete
    prompt: Incomplete
    continuation_prompt: Incomplete
    def __init__(
        self,
        cmd_or_spawn,
        orig_prompt,
        prompt_change,
        new_prompt="[PEXPECT_PROMPT>",
        continuation_prompt="[PEXPECT_PROMPT+",
        extra_init_cmd: Incomplete | None = None,
    ) -> None: ...
    def set_prompt(self, orig_prompt, prompt_change) -> None: ...
    def run_command(self, command, timeout: int = -1, async_: bool = False): ...

def python(command: str = "python"): ...
def bash(command: str = "bash"): ...
