from turtle import Turtle, dot

Turtle().dot()
Turtle().dot(10)
Turtle().dot(size=10)
Turtle().dot((0, 0, 0))
Turtle().dot(size=(0, 0, 0))
Turtle().dot("blue")
Turtle().dot("")
Turtle().dot(size="blue")
Turtle().dot(20, "blue")
Turtle().dot(20, "blue")
Turtle().dot(20, (0, 0, 0))
Turtle().dot(20, 0, 0, 0)

Turtle().dot(size=10, color="blue")  # type: ignore
Turtle().dot(10, color="blue")  # type: ignore
Turtle().dot(color="blue")  # type: ignore

dot()
dot(10)
dot(size=10)
dot((0, 0, 0))
dot(size=(0, 0, 0))
dot("blue")
dot("")
dot(size="blue")
dot(20, "blue")
dot(20, "blue")
dot(20, (0, 0, 0))
dot(20, 0, 0, 0)

dot(size=10, color="blue")  # type: ignore
dot(10, color="blue")  # type: ignore
dot(color="blue")  # type: ignore
