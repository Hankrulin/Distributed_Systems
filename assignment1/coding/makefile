# Makefile for Java RMI Stack Calculator
# Files: Calculator.java, CalculatorImplementation.java, CalculatorServer.java,
#        CalculatorClient.java, MultiClientTest.java

JAVAC := javac
JAVA  := java

SRC := Calculator.java CalculatorImplementation.java CalculatorServer.java CalculatorClient.java MultiClientTest.java
CLASSES := $(SRC:.java=.class)

# -------------------------------------------------------
# Default: compile all classes
# -------------------------------------------------------
all: $(CLASSES)

%.class: %.java
	$(JAVAC) $<

# -------------------------------------------------------
# Run targets
# -------------------------------------------------------

# Start server (creates RMI registry internally via LocateRegistry.createRegistry(1099))
server: all
	$(JAVA) CalculatorServer

# Run a single client
client: all
	$(JAVA) CalculatorClient

# Run the multi-client test (your MultiClientTest main)
multiclient: all
	$(JAVA) MultiClientTest

# (Optional) Start a standalone rmiregistry if you prefer the classic flow
# Usually NOT needed because the server already starts the registry.
rmiregistry:
	rmiregistry 1099

# -------------------------------------------------------
# Utilities
# -------------------------------------------------------
clean:
	rm -f *.class

re: clean all

# Small self-doc
help:
	@echo "Targets:"
	@echo "  make            - Compile all .java files"
	@echo "  make server     - Run CalculatorServer (starts registry internally)"
	@echo "  make client     - Run CalculatorClient"
	@echo "  make multiclient- Run MultiClientTest"
	@echo "  make rmiregistry- (Optional) start rmiregistry on port 1099"
	@echo "  make clean      - Remove .class files"
	@echo "  make re         - Clean and rebuild"
	@echo ""
	@echo "Typical flow:"
	@echo "  1) make server"
	@echo "  2) (new terminal) make client"
	@echo "  3) (optional)     make multiclient"
