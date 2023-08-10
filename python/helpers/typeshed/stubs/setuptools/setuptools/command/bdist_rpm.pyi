import distutils.command.bdist_rpm as orig

class bdist_rpm(orig.bdist_rpm):
    def run(self) -> None: ...
