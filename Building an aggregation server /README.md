# Weather Aggregation Server

## Overview
This project implements a **distributed weather aggregation system** in Java.  
The system accepts weather data from multiple content servers through **HTTP PUT** requests and allows clients to retrieve aggregated weather data through **HTTP GET** requests.

The server is designed with a strong focus on:

- **concurrency control**
- **Lamport logical clocks**
- **data expiration**
- **failure recovery with Write-Ahead Logging (WAL)**
- **JSON validation and aggregation**

Every response includes an `X-Lamport-TS` header so that event ordering can be tracked consistently across the distributed system. The server also removes expired data from inactive sources and recovers safely after crashes using WAL and snapshot persistence. :contentReference[oaicite:1]{index=1}

---

## Key Features

### 1. HTTP-based Aggregation Server
The server listens for HTTP requests and supports:

- `PUT /weather.json`
- `GET /weather.json`
- `GET /weather.json?sid=...`

It validates incoming requests, routes them to the correct handler, and ensures all responses include the Lamport timestamp header. :contentReference[oaicite:2]{index=2}

### 2. Lamport Clock Support
A thread-safe Lamport clock is used to preserve event ordering across requests.

- `onReceive(ts)`: updates the local clock when receiving a request
- `onSend()`: increments the clock before sending a response

This ensures that the response timestamp is always greater than the request timestamp. :contentReference[oaicite:3]{index=3}

### 3. Concurrency Control
The system handles concurrent reads and writes safely.

- PUT requests are processed through a **single-threaded priority queue**
- ordering follows **Lamport timestamps**
- read/write consistency is protected using `withRead(...)` and `withWrite(...)`
- sweep tasks use the same write sequence to avoid race conditions with PUT requests :contentReference[oaicite:4]{index=4}

### 4. 30-Second Expiration
To keep the aggregated data current, the server removes weather data from inactive content servers.

**Rule:**  
If a source identified by `X-Sender-ID` has not sent a PUT request in **30 seconds**, all station IDs previously reported by that source are removed from the aggregate.

A background sweeper checks inactivity every **5 seconds** and enqueues removal tasks through the same write path used by PUT operations. :contentReference[oaicite:5]{index=5}

### 5. Failure Recovery with WAL
The server uses **Write-Ahead Logging (WAL)** to ensure crash recovery.

Recovery flow:
1. On every PUT, append the payload to `aggregate.wal` and `fsync`
2. Under the write lock, read the snapshot, merge the new content, atomically replace `aggregate.json`, and truncate the WAL on success
3. On startup, replay any WAL entries to rebuild a consistent snapshot and then clear the WAL :contentReference[oaicite:6]{index=6}

### 6. JSON Validation and Aggregation
The system includes lightweight JSON parsing and validation.

- PUT payload must be either:
  - a JSON object, or
  - an array of JSON objects
- every object must contain a string `"id"`
- weather data is merged into the aggregate
- results can also be filtered by station ID using query parameters :contentReference[oaicite:7]{index=7}

---

## Project Structure

### `AggregationServer.java`
Main server entry point.

Responsibilities:
- listens on TCP/HTTP
- parses requests
- dispatches to handlers
- adds `X-Lamport-TS` to every response
- runs a background sweeper every 5 seconds to remove expired data :contentReference[oaicite:8]{index=8}

### `ConcurrencyControl.java`
Handles ordering and consistency.

Responsibilities:
- single-threaded PUT worker with Lamport-based priority ordering
- read/write locking via `withRead(...)` and `withWrite(...)`
- serializes sweep tasks with writes to avoid race conditions :contentReference[oaicite:9]{index=9}

### `Persistence.java`
Handles storage and crash recovery.

Responsibilities:
- append payloads to WAL
- fsync for durability
- atomically replace snapshot
- replay WAL during recovery
- read committed data, optionally by station ID :contentReference[oaicite:10]{index=10}

### `JsonCodec.java`
Handles JSON validation, parsing, filtering, and merging.

Responsibilities:
- validate PUT payload format
- ensure every object contains string `"id"`
- merge new weather data into aggregate
- filter by station ID
- extract or remove expired IDs :contentReference[oaicite:11]{index=11}

### `LamportClock.java`
Implements the logical clock.

Responsibilities:
- update clock on receive
- increment clock on send
- provide thread-safe timestamp handling :contentReference[oaicite:12]{index=12}

### `RequestRouter.java`
Routes and validates requests.

Responsibilities:
- only accept:
  - `GET /weather.json`
  - `GET /weather.json?sid=...`
  - `PUT /weather.json`
- read headers such as:
  - `X-Lamport-TS`
  - `X-Sender-ID`
- return appropriate status decisions such as:
  - `NO_CONTENT`
  - `BAD_REQUEST`
  - `SERVER_ERROR` :contentReference[oaicite:13]{index=13}

### `ContentServer.java`
Example PUT client.

Responsibilities:
- sends weather data to the aggregation server
- includes `Content-Type: application/json`
- includes Lamport timestamp
- optionally includes `X-Sender-ID` :contentReference[oaicite:14]{index=14}

### `GETClient.java`
Example GET client.

Responsibilities:
- requests weather data from the aggregation server
- can request all data or a specific station by `sid` :contentReference[oaicite:15]{index=15}

### `HttpUtil.java`
Helper utilities for client/server HTTP communication. :contentReference[oaicite:16]{index=16}

---

## System Workflow

1. A **Content Server** sends weather data to the Aggregation Server using an HTTP PUT request.
2. The **Request Router** validates the request path, method, and headers.
3. The **Lamport Clock** updates the logical timestamp on request receive.
4. The request is passed through **Concurrency Control** to preserve ordering and consistency.
5. The data is validated and merged using **JsonCodec**.
6. The updated aggregate is written through **Persistence**, which uses WAL and snapshots.
7. A **GET Client** can request the latest aggregated weather data.
8. A background sweeper removes stale data if a source has been inactive for more than 30 seconds. :contentReference[oaicite:17]{index=17}

---

## Data Expiration Policy

The system applies a TTL-style expiration rule:

- each content source is tracked using `X-Sender-ID`
- after a successful PUT:
  - the server records the source’s `lastSeen`
  - extracts all station IDs owned by that source
- every 5 seconds, the sweeper checks for expired sources
- if a source has not updated for 30 seconds, all of its station IDs are removed from the aggregate :contentReference[oaicite:18]{index=18}

This keeps the aggregated weather data fresh and prevents stale entries from remaining in the system.

---

## Failure Recovery

The project uses **Write-Ahead Logging** to ensure durability and recovery.

### WAL Process
- append each successful PUT payload to `aggregate.wal`
- flush the WAL to disk using `fsync`
- merge the new content into the snapshot
- atomically replace `aggregate.json`
- truncate the WAL after a successful commit :contentReference[oaicite:19]{index=19}

### Recovery on Restart
When the server restarts:
- if WAL entries exist, they are replayed
- the snapshot is rebuilt
- the WAL is truncated afterward :contentReference[oaicite:20]{index=20}

This design improves reliability and protects against partial writes or crashes.
