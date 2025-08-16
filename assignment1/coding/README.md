# Assignment 1 - Java RMI Calculator

## Overview
This project implements a Java RMI-based calculator server with a shared stack, supporting `pushValue`, `pushOperation` (`min`, `max`, `lcm`, `gcd`), `pop`, `isEmpty`, and `delayPop` operations. It includes a single-client test (`CalculatorClient`) and a multi-client test (`MultiClientTest`).

## Files
- **Calculator.java**: Remote interface defining the calculator methods.
- **CalculatorImplementation.java**: Server-side implementation with a synchronized stack.
- **CalculatorServer.java**: Bootstraps the RMI server.
- **CalculatorClient.java**: Tests all operations with a single client.
- **MultiClientTest.java**: Tests concurrent access with 4 clients.

