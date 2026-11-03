from lib import Client, connect, open_client

client = Client()
client.<warning descr="use send instead">post</warning>("x")
client.send("x")
<warning descr="use connect instead">open_client</warning>()
connect().send("x")
