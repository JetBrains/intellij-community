import distutils.command.register as orig

class register(orig.register):
    def run(self) -> None: ...
