# Assignment 1 - Java RMI Calculator

## Overview
This project implements a distributed calculator using Java Remote Method Invocation (RMI). The calculator maintains a shared stack of integers on the server side, allowing clients to perform operations such as pushing values, applying aggregate operations which are min, max, gcd, lcm on the entire stack, popping values, checking if the stack is empty, and performing delayed pops. 
The system is designed to handle concurrent access from multiple clients, with thread-safety ensured through synchronization in the server implementation.
The project demonstrates key concepts in distributed systems, including remote interfaces, server bootstrapping, client-server communication via RMI, and error handling for remote exceptions. It assumes a single shared stack across all clients for simplicity.
Key features:
Shared Stack: A single stack is shared among all clients, making operations atomic and thread-safe.
Operations: Supports pushing integers, applying operations that consume the entire stack and push a single result, popping, checking emptiness, and delayed popping.
Error Handling: Throws RemoteException for network issues or invalid operations (e.g., popping from an empty stack).
Concurrency: Tested with a single client; multi-client testing can be simulated using the provided Makefile.

## Files Description
- Calculator.java: 
Defines the remote methods that clients can call.
Every class that implements this interface can be used as an RMI service.
Key Points:
1.Extends Remote (all RMI interfaces must).
2.Declares methods like pushValue, pushOperation, pop, delayPop, isEmpty.
3.Each method throws RemoteException, since remote calls may fail.

- CalculatorImplementation.java: 
Provides the actual logic for the calculator. This is where the math and stack operations are coded.
extends UnicastRemoteObject: Makes this object accessible remotely.
implements Calculator: Ensures all interface methods are implemented.
Maintains a 'Deque<Integer>' stack where values are stored.
Provide real implementations for operations such as:
    1.'pushValue(int value)' -> push a number into the stack
    2.'pushOperation(String operator)' -> pops all values, performs min/max/gcd/lcm, pushes result.
    3.pop() -> pop the top element from the stack.
    4.delayPop(int ms) -> → waits then pops (simulates delayed remote calls).

- CalculatorServer.java:
Starts the RMI registry, creates a calculator object, and binds it so clients can find and use it.
Key points:
Uses LocateRegistry.createRegistry(1099) to start RMI registry on port 1099.

- CalculatorClient.java: 
Demonstrates how a single client connects to the server and uses the calculator.
Key point:
1.Uses LocateRegistry.getRegistry("localhost") to connect to server.
2.Looks up the remote service with registry.lookup("CalculatorService").
3.Calls methods like pushValue, pushOperation, pop, etc.
4.Shows results on the console.

- MultiClientTest.java:
Demonstrates multiple clients accessing the same server stack simultaneously, to show that the server maintains one shared state.

How They Work Together:
1.Server starts → Creates calculator object and binds it to RMI registry.
2.Client runs → Looks up CalculatorService, calls methods remotely.
3.Multi-client test → Confirms that different clients share the same server state.