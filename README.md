# Onion

A Java-based, Docker-enabled demonstration of onion-routing: anonymous message routing through a chain of peers.

## Overview
Onion routing is a technique for enabling anonymous communication over a computer network. In an onion network, messages are encapsulated in multiple layers of encryption, similar to the layers of an onion. These encrypted messages are transmitted through a series of network nodes known as "onion routers." As each message passes through a node, that node removes a single layer of encryption, revealing the next destination in the route. Importantly, each node knows only its immediate predecessor and successor, but cannot access the content of the message, as it remains protected by the remaining layers of encryption. When the message reaches the final node, it is fully decrypted and sent to the server, effectively breaking the link between the source of the request and the destination. This makes it significantly more difficult for eavesdroppers to trace end-to-end communications.

## Features
- Milti-peer chain setup via Docker containers
- Focus on network anonymisation and routing logic (not production readiness)
- Logs showing the full routing path

## Tech Stack
- Java
- Docker
- Shell scripts for building & running network

## Setup
### 1. Clone the repo
```bash
git clone https://github.com/kiiril/Onion.git
cd Onion
```
### 2. Start the peer network
```bash
./rebuild.sh # build Docker image
# adjust NUM_PEERS_IN_CHAIN in PeerConnectionManager.java if needed (default: 3)
./start.sh # start a peer
# run ./start.sh multiple times in separate terminals to spawn multiple peers
```
### 3. Start the example server
Unzip ```simple-node-server.zip``` and run ```./start.sh``` inside it to start the server.
Copy the printed URL and paste it into the console of any peer to send a request.
- Responses are saved in the ```output/``` folder as .html files (includes the IP address from which the request reached the server)
- Peer-specific logs appear in the ```logs/``` folder and show the request path through the network

## Implementation
Implementation details are available in ```DETAILS.pdf```
