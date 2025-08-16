
JAVAC = javac
JAVA = java
JFLAGS = -g
SOURCES = Calculator.java CalculatorImplementation.java CalculatorServer.java CalculatorClient.java
CLASSES = $(SOURCES:.java=.彼此


all: $(CLASSES)

%.class: %.java
	$(JAVAC) $(JFLAGS) $<

registry:
	rmiregistry &

server: $(CLASSES)
	$(JAVA) CalculatorServer &

client: $(CLASSES)
	$(JAVA) CalculatorClient

multiclients: $(CLASSES)
	$(JAVA) CalculatorClient & $(JAVA) CalculatorClient & $(JAVA) CalculatorClient

clean:
	rm -f *.class

# Phony targets
.PHONY: all registry server client multiclients clean