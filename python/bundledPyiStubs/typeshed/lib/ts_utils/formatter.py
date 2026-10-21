import subprocess
import time
from typing_extensions import Self

from ts_utils.utils import TextColor, colored, format_time


class StatusLineFormatter:
    """Context manager for printing a single line status.

    This prints a leading text (for example the name of an item) when the
    context manager is entered, and the colored success status when the
    context manager is exited:

    >>> with StatusLineFormatter("requests") as formatter:
    ...     # ...
    ...     formatter.success("ok")
    requests... ok

    The possible status methods are "success", "warning", and "error". If
    multiple functions are called, only the last one is considered:

    >>> with StatusLineFormatter("requests") as formatter:
    ...     # ...
    ...     formatter.success("ok")
    ...     formatter.error("error")
    requests... error

    If no status method is called before the context manager exists, an error
    is printed and an exception is raised:

    >>> with StatusLineFormatter("requests") as formatter:
    ...     pass
    Traceback (most recent call last):
        ...
    RuntimeError: Progress formatter status not set
    >>> try:
    ...     with StatusLineFormatter("requests") as formatter:
    ...         pass
    ... except RuntimeError:
    ...     pass
    requests... unknown result

    If the context manager is exited with an exception, an error is printed,
    regardless of any status set. The exception is not handled:

    >>> with StatusLineFormatter("requests") as formatter:
    ...     raise ValueError("test error")
    Traceback (most recent call last):
        ...
    ValueError: test error
    >>> try:
    ...     with StatusLineFormatter("requests") as formatter:
    ...         raise ValueError("test error")
    ... except ValueError:
    ...     pass
    requests... error (test error)

    It's possible to attach additional output to the formatter. This is
    printed after the status line, using a dimmed color by default:

    >>> with StatusLineFormatter("requests") as formatter:
    ...     formatter.error("fail")
    ...     formatter.append_output("This is an additional line.")
    requests... fail
    <BLANKLINE>
    This is an additional line.
    <BLANKLINE>
    """

    def __init__(self, initial_text: str, *, timed: bool = False) -> None:
        self.initial_text = initial_text
        self.timed = timed
        self.status_message: str | None = None
        self.status_color: TextColor = "red"
        self.additional_output: list[tuple[str, TextColor]] = []
        self.start_time = 0.0

    def __enter__(self) -> Self:
        print(f"{self.initial_text}... ", end="", flush=True)
        self.start_time = time.time()
        return self

    def __exit__(self, exc_type: object, exc_value: object, traceback: object) -> None:
        t = time.time() - self.start_time

        if exc_value is not None:
            print(colored(f"error ({exc_value})", "red"))
            return

        if self.status_message is None:
            print(colored("unknown result", "red"))
            raise RuntimeError("Progress formatter status not set")

        print(colored(self.status_message, self.status_color), end="")
        if self.timed:
            print(" ", end="")
            print(colored(format_time(t)), end="")
        print()

        if self.additional_output:
            print()
            for text, color in self.additional_output:
                print(colored(text.strip(), color))
            print()

    def success(self, message: str) -> None:
        """Print a successful status."""
        self.status_message = message
        self.status_color = "green"

    def warning(self, message: str) -> None:
        """Print a warning status."""
        self.status_message = message
        self.status_color = "yellow"

    def error(self, message: str) -> None:
        """Print an error status."""
        self.status_message = message
        self.status_color = "red"

    def append_output(self, message: str) -> None:
        """Print an additional message after the status line."""
        self.additional_output.append((message, "dark_grey"))

    def append_warning(self, message: str) -> None:
        """Print an additional warning after the status line."""
        self.additional_output.append((message, "yellow"))

    def append_hint(self, message: str) -> None:
        """Print a hint for the user."""
        self.additional_output.append((message, "magenta"))

    def append_divider(self) -> None:
        """Append a divider to the additional output.

        This can be used to separate sections in the output:

        >>> with StatusLineFormatter("requests") as formatter:
        ...     formatter.success("ok")
        ...     formatter.append_output("Line 1")
        ...     formatter.append_divider()
        ...     formatter.append_output("Line 2")
        requests... ok
        <BLANKLINE>
        Line 1
        <BLANKLINE>
        **********************************************************************
        <BLANKLINE>
        Line 2
        <BLANKLINE>
        """
        self.additional_output.extend([("", "dark_grey"), ("*" * 70, "dark_grey"), ("", "dark_grey")])

    def command_output(self, e: subprocess.CompletedProcess[bytes] | subprocess.CalledProcessError) -> None:
        """Print command output (stdout and stderr) as additional messages.

        >>> with StatusLineFormatter("requests") as formatter:
        ...     e = subprocess.run(["ls", "/dev/null"], capture_output=True)
        ...     formatter.command_output(e)
        ...     formatter.success("ok")
        requests... ok
        <BLANKLINE>
        /dev/null
        <BLANKLINE>
        """
        stdout = e.stdout.decode()
        stderr = e.stderr.decode()
        if stdout:
            self.additional_output.append((stdout, "dark_grey"))
        if stderr:
            self.additional_output.append((stderr, "dark_grey"))

    def command_error(self, message: str, e: subprocess.CalledProcessError) -> None:
        """Print an error status for a failed command.

        The command output (stdout and stderr) will also be printed.

        >>> with StatusLineFormatter("requests") as formatter:
        ...     try:
        ...         subprocess.run(["ls", "/will/not/exist"], check=True, capture_output=True)
        ...     except subprocess.CalledProcessError as e:
        ...         formatter.command_error("fail (could not list directory)", e)
        requests... fail (could not list directory)
        <BLANKLINE>
        ls: cannot access '/will/not/exist': No such file or directory
        <BLANKLINE>
        """
        self.error(message)
        self.command_output(e)
