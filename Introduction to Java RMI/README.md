# Java RMI Calculator

## Overview
This project implements a distributed calculator using Java Remote Method Invocation (RMI). The calculator maintains a shared stack of integers on the server side, allowing clients to remotely perform operations such as pushing values, applying aggregate operations, popping values, checking whether the stack is empty, and performing delayed pops.

The project demonstrates core distributed systems concepts, including remote interfaces, server bootstrapping, RMI-based client-server communication, shared remote state, concurrency control, and remote exception handling. For simplicity, the system uses a single shared stack for all connected clients.

---

## Features
- **Shared Stack:** A single integer stack is maintained on the server and shared by all clients.
- **Supported Operations:** Clients can push integers, apply aggregate operations (`min`, `max`, `gcd`, `lcm`), pop values, check whether the stack is empty, and perform delayed pops.
- **Thread Safety:** Server-side operations are synchronized to ensure safe concurrent access.
- **Remote Communication:** Clients interact with the calculator through Java RMI.
- **Error Handling:** Remote exceptions are used to handle invalid operations and communication failures.

---

## Supported Operations
- `pushValue(int value)`  
  Pushes an integer onto the shared stack.

- `pushOperation(String operator)`  
  Consumes all values in the stack, applies the specified operation (`min`, `max`, `gcd`, or `lcm`), and pushes the result back onto the stack.

- `pop()`  
  Removes and returns the top value from the stack.

- `delayPop(int ms)`  
  Waits for the specified number of milliseconds before popping the top value.

- `isEmpty()`  
  Checks whether the shared stack is empty.

---

## File Description

### `Calculator.java`
Defines the remote interface for the calculator service.

**Key points:**
- Extends `Remote`, as required for all RMI interfaces.
- Declares the methods that clients can call remotely.
- Each method throws `RemoteException` because remote calls may fail.

---

### `CalculatorImplementation.java`
Provides the server-side implementation of the calculator logic.

**Key points:**
- Extends `UnicastRemoteObject` to make the object remotely accessible.
- Implements the `Calculator` interface.
- Maintains a `Deque<Integer>` as the shared stack.
- Implements all calculator operations, including stack manipulation and aggregate functions.
- Uses synchronization to ensure thread-safe access.

---

### `CalculatorServer.java`
Starts the RMI registry, creates the calculator object, and binds it to the registry.

**Key points:**
- Uses `LocateRegistry.createRegistry(1099)` to start the RMI registry on port `1099`.
- Creates an instance of `CalculatorImplementation`.
- Binds the remote object using a service name such as `CalculatorService`.

---

### `CalculatorClient.java`
Demonstrates how a client connects to the RMI server and invokes remote calculator methods.

**Key points:**
- Uses `LocateRegistry.getRegistry("localhost")` to connect to the local RMI registry.
- Looks up the remote service with `registry.lookup("CalculatorService")`.
- Invokes methods such as `pushValue`, `pushOperation`, `pop`, and `isEmpty`.
- Displays results in the console.

---

### `MultiClientTest.java`
Demonstrates multiple clients accessing the same shared calculator stack.

**Key points:**
- Shows that all clients operate on the same server-side stack.
- Helps verify shared-state behaviour and concurrency handling.
- Can be used to test thread safety under concurrent access.

---

## How It Works
1. The server starts and creates the shared calculator object.
2. The calculator object is bound to the RMI registry.
3. A client connects to the registry and looks up the calculator service.
4. The client invokes calculator methods remotely.
5. All clients interact with the same shared stack on the server.

---

## Concurrency Model
This project assumes a single shared stack across all clients. Since multiple clients may call remote methods at the same time, synchronization is used in the server implementation to ensure that stack operations remain atomic and thread-safe.

---

## Error Handling
The system uses `RemoteException` to handle:
- network or communication failures
- invalid remote operations
- server-side execution issues

Additional checks should also prevent invalid operations such as popping from an empty stack.

---

## How to Run

### 1. Compile the files
```bash
javac *.java
