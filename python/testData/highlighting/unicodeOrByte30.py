# no need for from __future__ import unicode_literals

z = (
    <info descr="null">"simple"</info>
    <info descr="null">"escaped <info descr="null">\u1234</info> correct"</info>
    <info descr="null">"escaped <error descr="Invalid escape sequence">\u123z</error> incorrect"</info>
    <info descr="null">"escaped <info descr="null">\U12345678</info> correct"</info>
    <info descr="null">"escaped <error descr="Invalid escape sequence">\U1234567 </error> too short"</info>
    <info descr="null">"hex <info descr="null">\x12</info> correct"</info>
    <info descr="null">"hex <error descr="Invalid escape sequence">\x1z</error> incorrect"</info>
    <info descr="null">"named <info descr="null">\N{comma}</info> correct"</info>
    <info descr="null">"named <error descr="Invalid escape sequence">\N</error>{123} incorrect, not a name"</info>
    <info descr="null">"named <error descr="Invalid escape sequence">\N{foo</error>, incorrect"</info>
    <info descr="null">"named incomplete <error descr="Invalid escape sequence">\N{aa</error>"</info>
    #"lone backslash \"
)
z = b"hex <info descr="null">\x12</info> correct"
z = b"hex <info descr="null">\x12</info>3 correct"
z = b"hex <error descr="Invalid escape sequence">\x1z</error> incorrect"
z = b"hex incomplete<error descr="Invalid escape sequence">\x</error>"
z = b"hex incomplete<error descr="Invalid escape sequence">\x1</error>"
z = b"one char <info descr="null">\n</info> correct"
z = b"one char \Q ignored"
z = b"octal <info descr="null">\007</info> correct"
z = b"octal <info descr="null">\27</info> correct"
z = b"octal <info descr="null">\7</info> correct"
z = b"octal <info descr="null">\00</info>8 deceptively correct"
z = b"non-octal \986 ignored"
