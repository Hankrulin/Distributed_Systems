# Makefile for Java RMI Calculator Assignment

# Variables
JAVAC = javac
JAVA = java
JFLAGS = -g
SOURCES = Calculator.java CalculatorImplementation.java CalculatorServer.java CalculatorClient.java
CLASSES = $(SOURCES:.java=.class)

# Default target: compile all Java files
all: $(CLASSES)

# Rule to compile .java files to .class files
%.class: %.java
	$(JAVAC) $(JFLAGS) $<

# Run the RMI registry
registry:
	rmiregistry &

# Run the server
server: $(CLASSES)
	$(JAVA) -Djava.security.policy=policy.all CalculatorServer &

# Run a single client
client: $(CLASSES)
	$(JAVA) -Djava.security.policy=policy.all CalculatorClient

# Run multiple clients for testing
multiclients: $(CLASSES)
	$(JAVA) -Djava.security.policy=policy.all CalculatorClient & $(JAVA) -Djava.security.policy=policy.all CalculatorClient & $(JAVA) -Djava.security.policy=policy.all CalculatorClient

# Clean up compiled files
clean:
	rm -f *.class

# Phony targets
.PHONY: all registry server client multiclients clean