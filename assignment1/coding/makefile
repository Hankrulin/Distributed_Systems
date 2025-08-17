# Makefile for Java RMI Calculator Assignment

# Variables
JAVAC = javac
JAVA = java
JFLAGS = -g
SOURCES = Calculator.java CalculatorImplementation.java CalculatorServer.java CalculatorClient.java
CLASSES = $(SOURCES:.java=.class)

# Default target: compile all Java files
all: check_files $(CLASSES)

# Check if all source files exist
check_files:
	@for src in $(SOURCES); do \
		if [ ! -f "$$src" ]; then \
			echo "Error: Source file $$src not found"; \
			exit 1; \
		fi; \
	done

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
	$(JAVA) -Djava.security.policy=policy.all CalculatorClient & \
	$(JAVA) -Djava.security.policy=policy.all CalculatorClient & \
	$(JAVA) -Djava.security.policy=policy.all CalculatorClient

# Clean up compiled files
clean:
	rm -f *.class

# Phony targets
.PHONY: all check_files registry server client multiclients clean