from __future__ import annotations

from typing import Any

from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus.flowables import Image
from reportlab.platypus.paragraph import Paragraph
from reportlab.platypus.tables import Table, TableStyle

data: list[list[Any]]

# Verify all the examples from the docs work

#
# TableStyle User Methods
#
LIST_STYLE = TableStyle(
    [
        ("LINEABOVE", (0, 0), (-1, 0), 2, colors.green),
        ("LINEABOVE", (0, 1), (-1, -1), 0.25, colors.black),
        ("LINEBELOW", (0, -1), (-1, -1), 2, colors.green),
        ("ALIGN", (1, 1), (-1, -1), "RIGHT"),
    ]
)
LIST_STYLE.add("BACKGROUND", (0, 0), (-1, 0), colors.Color(0, 0.7, 0.7))

#
# TableStyle Cell Formatting Commands
#
data = [
    ["00", "01", "02", "03", "04"],
    ["10", "11", "12", "13", "14"],
    ["20", "21", "22", "23", "24"],
    ["30", "31", "32", "33", "34"],
]
t = Table(data)
t.setStyle(TableStyle([("BACKGROUND", (1, 1), (-2, -2), colors.green), ("TEXTCOLOR", (0, 0), (1, -1), colors.red)]))

data = [
    ["00", "01", "02", "03", "04"],
    ["10", "11", "12", "13", "14"],
    ["20", "21", "22", "23", "24"],
    ["30", "31", "32", "33", "34"],
]
t = Table(data, 5 * [0.4 * inch], 4 * [0.4 * inch])
# NOTE: I've modified this example to drop the optional TableStyle
#       wrapper, so we test both variants
t.setStyle(
    [
        ("ALIGN", (1, 1), (-2, -2), "RIGHT"),
        ("TEXTCOLOR", (1, 1), (-2, -2), colors.red),
        ("VALIGN", (0, 0), (0, -1), "TOP"),
        ("TEXTCOLOR", (0, 0), (0, -1), colors.blue),
        ("ALIGN", (0, -1), (-1, -1), "CENTER"),
        ("VALIGN", (0, -1), (-1, -1), "MIDDLE"),
        ("TEXTCOLOR", (0, -1), (-1, -1), colors.green),
        ("INNERGRID", (0, 0), (-1, -1), 0.25, colors.black),
        ("BOX", (0, 0), (-1, -1), 0.25, colors.black),
    ]
)

#
# Table Style Line Commands
#
data = [
    ["00", "01", "02", "03", "04"],
    ["10", "11", "12", "13", "14"],
    ["20", "21", "22", "23", "24"],
    ["30", "31", "32", "33", "34"],
]
Table(
    data,
    style=[
        ("GRID", (1, 1), (-2, -2), 1, colors.green),
        ("BOX", (0, 0), (1, -1), 2, colors.red),
        ("LINEABOVE", (1, 2), (-2, 2), 1, colors.blue),
        ("LINEBEFORE", (2, 1), (2, -2), 1, colors.pink),
    ],
)

data = [
    ["00", "01", "02", "03", "04"],
    ["10", "11", "12", "13", "14"],
    ["20", "21", "22", "23", "24"],
    ["30", "31", "32", "33", "34"],
]
Table(
    data,
    style=[
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("GRID", (1, 1), (-2, -2), 1, colors.green),
        ("BOX", (0, 0), (1, -1), 2, colors.red),
        ("BOX", (0, 0), (-1, -1), 2, colors.black),
        ("LINEABOVE", (1, 2), (-2, 2), 1, colors.blue),
        ("LINEBEFORE", (2, 1), (2, -2), 1, colors.pink),
        ("BACKGROUND", (0, 0), (0, 1), colors.pink),
        ("BACKGROUND", (1, 1), (1, 2), colors.lavender),
        ("BACKGROUND", (2, 2), (2, 3), colors.orange),
    ],
)

#
# Complex Cell Values
#
styleSheet = getSampleStyleSheet()
I = Image("foo.jpg")
I.drawHeight = 1.25 * inch * I.drawHeight / I.drawWidth
I.drawWidth = 1.25 * inch
P0 = Paragraph(
    """<b>A pa<font color=red>r</font>a<i>graph</i></b>
    <super><font color=yellow>1</font></super>""",
    styleSheet["BodyText"],
)
P = Paragraph(
    """<para align=center spaceb=3>The <b>ReportLab Left
    <font color=red>Logo</font></b>
    Image</para>""",
    styleSheet["BodyText"],
)
data = [
    ["A", "B", "C", P0, "D"],
    ["00", "01", "02", [I, P], "04"],
    ["10", "11", "12", [P, I], "14"],
    ["20", "21", "22", "23", "24"],
    ["30", "31", "32", "33", "34"],
]
Table(
    data,
    style=[
        ("GRID", (1, 1), (-2, -2), 1, colors.green),
        ("BOX", (0, 0), (1, -1), 2, colors.red),
        ("LINEABOVE", (1, 2), (-2, 2), 1, colors.blue),
        ("LINEBEFORE", (2, 1), (2, -2), 1, colors.pink),
        ("BACKGROUND", (0, 0), (0, 1), colors.pink),
        ("BACKGROUND", (1, 1), (1, 2), colors.lavender),
        ("BACKGROUND", (2, 2), (2, 3), colors.orange),
        ("BOX", (0, 0), (-1, -1), 2, colors.black),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.black),
        ("VALIGN", (3, 0), (3, 0), "BOTTOM"),
        ("BACKGROUND", (3, 0), (3, 0), colors.limegreen),
        ("BACKGROUND", (3, 1), (3, 1), colors.khaki),
        ("ALIGN", (3, 1), (3, 1), "CENTER"),
        ("BACKGROUND", (3, 2), (3, 2), colors.beige),
        ("ALIGN", (3, 2), (3, 2), "LEFT"),
    ],
)

#
# TableStyle Span Commands
#
data = [
    ["Top\\nLeft", "", "02", "03", "04"],
    ["", "", "12", "13", "14"],
    ["20", "21", "22", "Bottom\\nRight", ""],
    ["30", "31", "32", "", ""],
]
Table(
    data,
    style=[
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("BACKGROUND", (0, 0), (1, 1), colors.palegreen),
        ("SPAN", (0, 0), (1, 1)),
        ("BACKGROUND", (-2, -2), (-1, -1), colors.pink),
        ("SPAN", (-2, -2), (-1, -1)),
    ],
)

#
# TableStyle Miscellaneous Commands
#
# NOTE: This one doesn't provide any actual examples, we just
#       make sure these pass into TableStyle/Table when mixed
#       with other commands
TableStyle([("NOSPLIT", (0, 0), (1, 1))])
LIST_STYLE.add("NOSPLIT", (0, 0), (1, 1))

TableStyle([("ROUNDEDCORNERS", [0, 0, 5, 5])])
TableStyle([("ROUNDEDCORNERS", (0, 0, 5, 5))])
LIST_STYLE.add("ROUNDEDCORNERS", [0, 0, 5, 5])
LIST_STYLE.add("ROUNDEDCORNERS", (0, 0, 5, 5))

Table(
    [["foo"]],
    style=[
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("BACKGROUND", (0, 0), (0, 1), colors.pink),
        ("NOSPLIT", (0, 0), (1, 1)),
        ("ROUNDEDCORNERS", [0, 0, 5, 5]),
    ],
)


# Testing the various possible data layouts
Table([["foo"]])
Table([("foo",)])
Table((["foo"],))
Table((("foo",),))
