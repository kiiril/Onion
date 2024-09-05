# Onion

## Description

Onion routing is a technique for enabling anonymous communication over a computer network. In an onion network, messages are encapsulated in multiple layers of encryption, similar to the layers of an onion. These encrypted messages are transmitted through a series of network nodes known as "onion routers." As each message passes through a node, that node removes a single layer of encryption, revealing the next destination in the route. Importantly, each node knows only its immediate predecessor and successor, but cannot access the content of the message, as it remains protected by the remaining layers of encryption. When the message reaches the final node, it is fully decrypted and sent to the server, effectively breaking the link between the source of the request and the destination. This makes it significantly more difficult for eavesdroppers to trace end-to-end communications.

## Testing

1. Clone the repository
2. Run `rebuild.sh` to build the Docker image
3. Set NUM_PEERS_IN_CHAIN in PeerConnectionManager.java (default is 3)
4. Spawn any number of peers in the network using `start.sh`
5. Unzip simple-node-server.zip and run `start.sh` to start the server
6. Copy the url in the console
7. Paste the url to the console of any peer to send a request

In the root directory `output` folder will be created where you can find .html file with the response from the server. Response contains the ip address from which the request was sent.

`logs` folder contains logs of each peer and can be inspected to see the path of the request.