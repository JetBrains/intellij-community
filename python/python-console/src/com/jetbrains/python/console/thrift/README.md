# Transport for Python Console

## Motivation

The main motivation for the changes originally comes from the new restrictions that *Docker* imposes on its containers' networking. *Docker*
normally allows IP connections from the host machine (where Docker client is running) to the *Docker* containers. However the reversed
communication might not be possible. The previous implementation of *Python Console*, which is based on *Apache XML RPC* library, requires
connections in both directions that caused inability to use *Python Console* in some cases.

## Goal

The goal of the changes is to cover previously unsupported cases for Docker and Docker Compose environments by implementing *unidirectional*
connection from IDE frontend to *Python Console* backend script.

## Requirements

The *Python Console* logic is complicated. It is better to be preserved.

*Python Console* interaction requires sending requests from IDE to *Python Console* backend script and the opposite way.

## Implementation

### Thrift for Communication Protocol

*Thrift* has such a nice abstraction as *service*. *Service* describe request-response client-server interaction model. *Services* as well
as *data structures* they use are described using *\*.thrift* files. *Thrift* allows to generate both server-side and client-side interfaces
and data structures that will be used for the communication.

To meet the *requirements* two *Thrift services* must be present: one for each direction.

### Netty for Transport Layer

*Thrift* transport layer is decoupled from the service model layer. This allows us to use a custom implementation for the transport.

The transport layer should allow us to use *single* TCP connection for two services that operate in different directions. This means that
each side should act both like a client for one *Thrift service*  (sending requests and processing responses) and a server for the other 
*Thrift service* (processing requests and sending responses).

To implement this idea let each message be flagged whether the message is a request or a response.

#### Java Implementation

On Java side the message with direction flag is `com.jetbrains.python.console.thrift.DirectedMessage`. The incoming message is parsed into 
`DirectedMessage` using `com.jetbrains.python.console.thrift.DirectedMessageCodec`. The message content is dispatched then via 
`com.jetbrains.python.console.thrift.DirectedMessageHandler` either to request or response stream and will be handled accordingly by
*server-side Thrift service* or *client-side Thrift service*.

*Netty* handlers are asynchronous so that requests for *server-side service* and responses for *client-side service* are processed 
independently.

#### Python Implementation

Similar approach is used on the Python side but it is achieved using low-level `socket` package interaction.