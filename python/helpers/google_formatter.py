import textwrap

import rest_formatter
from sphinxcontrib.napoleon.docstring import GoogleDocstring


def main(text=None):
    src = rest_formatter.read_safe() if text is None else text
    rest_formatter.main(str(GoogleDocstring(textwrap.dedent(src))))


if __name__ == '__main__':
    main()
