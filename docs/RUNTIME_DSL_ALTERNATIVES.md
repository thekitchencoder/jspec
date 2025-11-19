# Runtime DSL Alternatives for JSpec

## Executive Summary

This proposal evaluates runtime-configurable DSL alternatives that allow specification changes without application rebuilds. These options complement the existing YAML format and proposed Kotlin DSL by providing hot-reloadable, externally-defined specifications.

---

## 1. Options Overview

| Category | Options | Key Characteristic |
|----------|---------|-------------------|
| **Parser Generators** | ANTLR, JavaCC | Full custom syntax control |
| **Expression Languages** | MVEL, SpEL, JEXL | Lightweight, Java-like syntax |
| **Embedded Scripting** | Groovy, GraalJS | Full programming language power |

---

## 2. Parser Generators

### 2.1 ANTLR 4

**Overview**: ANother Tool for Language Recognition - the most popular parser generator for Java.

**How It Works**:
1. Define grammar in `.g4` file
2. ANTLR generates lexer/parser Java classes
3. Use Visitor or Listener pattern to interpret parse tree
4. Load and parse DSL files at runtime

**Example Grammar** (`JSpec.g4`):

```antlr
grammar JSpec;

specification
    : 'specification' ID '{' criterion* '}'
    ;

criterion
    : 'criterion' ID '{' condition+ '}'
    | 'composite' ID '{' junction '}'
    ;

condition
    : STRING operator value
    ;

operator
    : 'eq' | 'ne' | 'gt' | 'gte' | 'lt' | 'lte'
    | 'in' | 'notIn' | 'all' | 'size'
    | 'exists' | 'type' | 'regex'
    ;

junction
    : ('and' | 'or') '{' reference+ '}'
    ;

reference
    : '+' ID
    | 'ref' '(' ID ')'
    ;

value
    : NUMBER | STRING | BOOLEAN | array
    ;

array
    : '[' value (',' value)* ']'
    ;

ID      : [a-zA-Z_][a-zA-Z0-9_-]* ;
STRING  : '"' (~["\r\n])* '"' ;
NUMBER  : '-'? [0-9]+ ('.' [0-9]+)? ;
BOOLEAN : 'true' | 'false' ;
WS      : [ \t\r\n]+ -> skip ;
```

**Example DSL Syntax**:

```
specification loan-eligibility {

    criterion age-minimum {
        "applicant.age" gte 18
    }

    criterion credit-good {
        "financial.credit_score" gt 650
    }

    criterion approved-states {
        "applicant.address.state" in ["CA", "NY", "TX", "FL"]
    }

    composite basic-eligibility {
        and {
            +age-minimum
            +credit-good
            +approved-states
        }
    }
}
```

**Java Integration**:

```java
public class JSpecParser {

    public Specification parse(String dslContent) {
        CharStream input = CharStreams.fromString(dslContent);
        JSpecLexer lexer = new JSpecLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JSpecParser parser = new JSpecParser(tokens);

        ParseTree tree = parser.specification();
        JSpecVisitor visitor = new JSpecVisitorImpl();
        return visitor.visit(tree);
    }

    // Runtime reloading
    public Specification loadFromFile(Path path) throws IOException {
        String content = Files.readString(path);
        return parse(content);
    }
}
```

**Pros**:
- Complete syntax control - design exactly what you want
- Excellent error messages with line/column info
- IDE plugins available (ANTLR4 plugin for IntelliJ)
- Generates parsers in multiple languages
- Mature ecosystem with extensive documentation
- AST/parse tree manipulation

**Cons**:
- Steep learning curve for grammar design
- Significant upfront development effort
- Must handle all edge cases in grammar
- Requires maintaining grammar as features evolve
- Generated code adds to codebase size

**Best For**: Projects needing a completely custom, domain-specific syntax with full control over language design.

---

### 2.2 JavaCC

**Overview**: Java Compiler Compiler - simpler alternative to ANTLR for straightforward grammars.

**How It Works**:
1. Define grammar in `.jj` file (embeds Java directly)
2. JavaCC generates recursive descent parser
3. Actions written inline in grammar file
4. Parse DSL files at runtime

**Example Grammar** (`JSpec.jj`):

```javacc
PARSER_BEGIN(JSpecParser)
package uk.codery.jspec.parser;

import uk.codery.jspec.model.*;
import java.util.*;

public class JSpecParser {
    public static void main(String[] args) throws ParseException {
        JSpecParser parser = new JSpecParser(System.in);
        Specification spec = parser.specification();
        System.out.println("Parsed: " + spec.id());
    }
}
PARSER_END(JSpecParser)

SKIP : { " " | "\t" | "\n" | "\r" }

TOKEN : {
    <SPECIFICATION: "specification">
  | <CRITERION: "criterion">
  | <COMPOSITE: "composite">
  | <AND: "and">
  | <OR: "or">
  | <GTE: "gte">
  | <GT: "gt">
  | <EQ: "eq">
  | <ID: ["a"-"z","A"-"Z","_"](["a"-"z","A"-"Z","0"-"9","_","-"])*>
  | <NUMBER: (["-"])?(["0"-"9"])+ | (["-"])?(["0"-"9"])+"."(["0"-"9"])+>
  | <STRING: "\"" (~["\""])* "\"">
}

Specification specification() :
{
    String id;
    List<Criterion> criteria = new ArrayList<>();
    Criterion c;
}
{
    <SPECIFICATION> id=identifier() "{"
        ( c=criterion() { criteria.add(c); } )*
    "}"
    { return new Specification(id, criteria); }
}

QueryCriterion criterion() :
{
    String id;
    Map<String, Object> query = new HashMap<>();
}
{
    <CRITERION> id=identifier() "{"
        condition(query)
    "}"
    { return new QueryCriterion(id, query); }
}

void condition(Map<String, Object> query) :
{
    String field;
    String op;
    Object value;
}
{
    field=stringLiteral() op=operator() value=value()
    {
        Map<String, Object> opMap = new HashMap<>();
        opMap.put("$" + op, value);
        query.put(field, opMap);
    }
}
```

**Pros**:
- Simpler than ANTLR for basic grammars
- Java code embedded directly in grammar
- Single file for grammar + actions
- Good for straightforward recursive descent parsing
- Well-documented with many examples

**Cons**:
- Less powerful than ANTLR (no left recursion)
- Actions mixed with grammar reduces readability
- Limited error recovery options
- Smaller community than ANTLR
- Generated code harder to debug

**Best For**: Simple, straightforward grammars where ANTLR feels like overkill.

---

## 3. Expression Languages

### 3.1 MVEL (MVFLEX Expression Language)

**Overview**: Java-like expression language designed for high performance, used by Drools rule engine.

**Maven Dependency**:

```xml
<dependency>
    <groupId>org.mvel</groupId>
    <artifactId>mvel2</artifactId>
    <version>2.5.2.Final</version>
</dependency>
```

**Example JSpec DSL with MVEL**:

```java
// DSL file content (external file)
String criterionExpression = """
    applicant.age >= 18 &&
    financial.creditScore > 650 &&
    ['CA', 'NY', 'TX'].contains(applicant.address.state)
    """;

// Parse and compile for reuse
Serializable compiled = MVEL.compileExpression(criterionExpression);

// Evaluate at runtime
Map<String, Object> document = loadDocument("applicant.json");
Boolean result = (Boolean) MVEL.executeExpression(compiled, document);
```

**Custom Syntax Wrapper**:

```java
public class MvelCriterionEvaluator {
    private final Map<String, Serializable> compiledCriteria = new ConcurrentHashMap<>();

    public void loadCriteria(Path configFile) throws IOException {
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        props.forEach((id, expression) -> {
            Serializable compiled = MVEL.compileExpression((String) expression);
            compiledCriteria.put((String) id, compiled);
        });
    }

    public EvaluationState evaluate(String criterionId, Map<String, Object> document) {
        Serializable compiled = compiledCriteria.get(criterionId);
        if (compiled == null) {
            return EvaluationState.UNDETERMINED;
        }

        try {
            Boolean result = (Boolean) MVEL.executeExpression(compiled, document);
            return result ? EvaluationState.MATCHED : EvaluationState.NOT_MATCHED;
        } catch (Exception e) {
            return EvaluationState.UNDETERMINED;
        }
    }

    // Hot reload support
    public void reload(Path configFile) throws IOException {
        compiledCriteria.clear();
        loadCriteria(configFile);
    }
}
```

**Example Configuration File** (`criteria.properties`):

```properties
age-minimum = applicant.age >= 18
credit-good = financial.creditScore > 650
approved-states = ['CA', 'NY', 'TX', 'FL'].contains(applicant.address.state)
income-range = financial.annualIncome >= 50000 && financial.annualIncome <= 500000
has-email = applicant.contact.email != null && applicant.contact.email != ''
email-valid = applicant.contact.email ~= '^[\\w.]+@[\\w.]+\\.[a-z]+$'
```

**MVEL Features for JSpec**:

| JSpec Operator | MVEL Syntax |
|----------------|-------------|
| `$eq` | `field == value` |
| `$ne` | `field != value` |
| `$gt` | `field > value` |
| `$gte` | `field >= value` |
| `$lt` | `field < value` |
| `$lte` | `field <= value` |
| `$in` | `['a','b'].contains(field)` |
| `$nin` | `!['a','b'].contains(field)` |
| `$exists` | `field != null` |
| `$regex` | `field ~= 'pattern'` |

**Pros**:
- Very fast execution (bytecode compilation)
- Java-like syntax - familiar to developers
- Pre-compilation for repeated execution
- Good null handling
- Built-in regex support with `~=` operator
- Used in production by Drools

**Cons**:
- Less active development than other options
- Limited documentation
- No built-in sandboxing
- Syntax not as clean as custom DSL
- Type errors at runtime only

**Best For**: High-performance rule evaluation where Java-like syntax is acceptable.

---

### 3.2 SpEL (Spring Expression Language)

**Overview**: Powerful expression language from Spring Framework, usable standalone.

**Maven Dependency**:

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-expression</artifactId>
    <version>6.1.12</version>
</dependency>
```

**Example JSpec DSL with SpEL**:

```java
public class SpelCriterionEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> compiledCriteria = new ConcurrentHashMap<>();

    public void loadCriteria(Path configFile) throws IOException {
        // Load YAML/JSON config
        Map<String, String> config = loadConfig(configFile);

        config.forEach((id, expression) -> {
            Expression compiled = parser.parseExpression(expression);
            compiledCriteria.put(id, compiled);
        });
    }

    public EvaluationState evaluate(String criterionId, Map<String, Object> document) {
        Expression expr = compiledCriteria.get(criterionId);
        if (expr == null) {
            return EvaluationState.UNDETERMINED;
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setRootObject(document);

        // Register custom functions
        registerCustomFunctions(context);

        try {
            Boolean result = expr.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result)
                ? EvaluationState.MATCHED
                : EvaluationState.NOT_MATCHED;
        } catch (Exception e) {
            return EvaluationState.UNDETERMINED;
        }
    }

    private void registerCustomFunctions(StandardEvaluationContext context) {
        try {
            // Register #all() function for $all operator
            context.registerFunction("all",
                SpelFunctions.class.getDeclaredMethod("all", List.class, List.class));

            // Register #size() function
            context.registerFunction("sizeIs",
                SpelFunctions.class.getDeclaredMethod("sizeIs", List.class, int.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}

// Custom functions for JSpec operators
public class SpelFunctions {
    public static boolean all(List<?> actual, List<?> required) {
        return actual != null && actual.containsAll(required);
    }

    public static boolean sizeIs(List<?> list, int size) {
        return list != null && list.size() == size;
    }
}
```

**Example Configuration File** (`criteria.yaml`):

```yaml
criteria:
  age-minimum: "[applicant][age] >= 18"
  credit-good: "[financial][creditScore] > 650"
  approved-states: "{'CA', 'NY', 'TX', 'FL'}.contains([applicant][address][state])"
  income-diverse: "#all([financial][incomeSources], {'salary', 'investments'})"
  references-count: "#sizeIs([applicant][references], 3)"
  email-valid: "[applicant][contact][email] matches '^[\\w.]+@[\\w.]+\\.[a-z]+$'"

composites:
  basic-eligibility:
    junction: AND
    refs: [age-minimum, credit-good, approved-states]
```

**SpEL Features for JSpec**:

| JSpec Operator | SpEL Syntax |
|----------------|-------------|
| `$eq` | `[field] == value` |
| `$ne` | `[field] != value` |
| `$gt` | `[field] > value` |
| `$in` | `{'a','b'}.contains([field])` |
| `$all` | `#all([field], {'a','b'})` |
| `$size` | `#sizeIs([field], 3)` |
| `$regex` | `[field] matches 'pattern'` |
| `$exists` | `[field] != null` |

**Pros**:
- Well-documented (Spring documentation)
- Active development and support
- Extensible with custom functions
- Safe navigation operator (`?.`)
- Collection projection and selection
- Type conversion built-in
- Can be compiled for performance

**Cons**:
- Brings Spring dependency (even if minimal)
- Map access syntax `[key]` less intuitive than dot notation
- Some JSpec operators need custom functions
- Larger dependency footprint

**Best For**: Projects already using Spring or needing well-documented, actively maintained expression language.

---

### 3.3 JEXL (Java Expression Language)

**Overview**: Apache Commons expression language with scripting support.

**Maven Dependency**:

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-jexl3</artifactId>
    <version>3.3</version>
</dependency>
```

**Example JSpec DSL with JEXL**:

```java
public class JexlCriterionEvaluator {
    private final JexlEngine jexl;
    private final Map<String, JexlExpression> compiledCriteria = new ConcurrentHashMap<>();

    public JexlCriterionEvaluator() {
        // Configure JEXL engine
        this.jexl = new JexlBuilder()
            .cache(512)           // Expression cache size
            .strict(true)         // Fail on unknown variables
            .silent(false)        // Log errors
            .create();
    }

    public void loadCriteria(Path configFile) throws IOException {
        Map<String, String> config = loadConfig(configFile);

        config.forEach((id, expression) -> {
            JexlExpression compiled = jexl.createExpression(expression);
            compiledCriteria.put(id, compiled);
        });
    }

    public EvaluationState evaluate(String criterionId, Map<String, Object> document) {
        JexlExpression expr = compiledCriteria.get(criterionId);
        if (expr == null) {
            return EvaluationState.UNDETERMINED;
        }

        JexlContext context = new MapContext(document);

        try {
            Object result = expr.evaluate(context);
            if (result instanceof Boolean) {
                return (Boolean) result
                    ? EvaluationState.MATCHED
                    : EvaluationState.NOT_MATCHED;
            }
            return EvaluationState.UNDETERMINED;
        } catch (Exception e) {
            return EvaluationState.UNDETERMINED;
        }
    }
}
```

**Example Configuration File** (`criteria.jexl`):

```properties
# Simple comparisons
age-minimum = applicant.age >= 18
credit-good = financial.creditScore > 650

# Collection operations
approved-states = applicant.address.state =~ ['CA', 'NY', 'TX', 'FL']
excluded-industries = !(employment.industry =~ ['gambling', 'cannabis'])
income-diverse = financial.incomeSources.containsAll(['salary', 'investments'])
references-count = size(applicant.references) == 3

# Existence and null checks
has-email = applicant.contact.email != null

# Regex matching
email-valid = applicant.contact.email =~ /^[\w.]+@[\w.]+\.[a-z]+$/

# Complex conditions
income-range = financial.annualIncome >= 50000 && financial.annualIncome <= 500000
```

**JEXL Features for JSpec**:

| JSpec Operator | JEXL Syntax |
|----------------|-------------|
| `$eq` | `field == value` |
| `$ne` | `field != value` |
| `$gt` | `field > value` |
| `$in` | `field =~ [...]` |
| `$nin` | `!(field =~ [...])` |
| `$all` | `field.containsAll([...])` |
| `$size` | `size(field) == n` |
| `$exists` | `field != null` |
| `$regex` | `field =~ /pattern/` |

**Pros**:
- Clean syntax with dot notation
- Built-in `=~` for regex and collection membership
- Apache license, well-maintained
- Script support for complex logic
- Configurable strictness levels
- Sandboxing support
- No Spring dependency

**Cons**:
- Less performant than MVEL for heavy workloads
- Smaller community than SpEL
- Documentation could be better
- Some advanced features less intuitive

**Best For**: Clean syntax without Spring dependency, good balance of power and simplicity.

---

## 4. Embedded Scripting Languages

### 4.1 Groovy

**Overview**: Dynamic JVM language with excellent DSL support and Java interoperability.

**Maven Dependency**:

```xml
<dependency>
    <groupId>org.apache.groovy</groupId>
    <artifactId>groovy</artifactId>
    <version>4.0.22</version>
</dependency>
```

**Example JSpec DSL with Groovy**:

```groovy
// criteria.groovy - External DSL file
specification 'loan-eligibility', {

    criterion 'age-minimum', {
        field 'applicant.age'
        gte 18
    }

    criterion 'credit-good', {
        field 'financial.creditScore'
        gt 650
    }

    criterion 'approved-states', {
        field 'applicant.address.state'
        inList 'CA', 'NY', 'TX', 'FL'
    }

    criterion 'income-range', {
        field 'financial.annualIncome'
        gte 50000
        lte 500000
    }

    composite 'basic-eligibility', {
        and {
            ref 'age-minimum'
            ref 'credit-good'
            ref 'approved-states'
        }
    }
}
```

**Java Integration**:

```java
public class GroovySpecificationLoader {
    private final GroovyShell shell;
    private final Binding binding;

    public GroovySpecificationLoader() {
        this.binding = new Binding();

        // Configure compiler for security
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(
            new SecureASTCustomizer()  // Restrict dangerous operations
        );

        this.shell = new GroovyShell(binding, config);
    }

    public Specification load(Path dslFile) throws IOException {
        // Set up DSL context
        SpecificationBuilder builder = new SpecificationBuilder();
        binding.setVariable("specification",
            (BiConsumer<String, Closure<?>>) builder::specification);
        binding.setVariable("criterion",
            (BiConsumer<String, Closure<?>>) builder::criterion);
        binding.setVariable("composite",
            (BiConsumer<String, Closure<?>>) builder::composite);

        // Execute DSL script
        String script = Files.readString(dslFile);
        shell.evaluate(script);

        return builder.build();
    }

    // Hot reload with file watching
    public void watchAndReload(Path dslFile, Consumer<Specification> onReload) {
        // Use WatchService to detect changes
        // Reload and notify on change
    }
}

// DSL builder that receives Groovy closures
public class SpecificationBuilder {
    private String id;
    private List<Criterion> criteria = new ArrayList<>();

    public void specification(String id, Closure<?> closure) {
        this.id = id;
        closure.setDelegate(this);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
    }

    public void criterion(String id, Closure<?> closure) {
        CriterionContext ctx = new CriterionContext(id);
        closure.setDelegate(ctx);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        criteria.add(ctx.build());
    }

    public Specification build() {
        return new Specification(id, criteria);
    }
}
```

**Alternative: Direct Expression Evaluation**:

```java
public class GroovyCriterionEvaluator {
    private final GroovyShell shell;
    private final Map<String, Script> compiledScripts = new ConcurrentHashMap<>();

    public void loadCriteria(Path configFile) throws IOException {
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        props.forEach((id, expression) -> {
            Script script = shell.parse((String) expression);
            compiledScripts.put((String) id, script);
        });
    }

    public EvaluationState evaluate(String criterionId, Map<String, Object> document) {
        Script script = compiledScripts.get(criterionId);
        if (script == null) {
            return EvaluationState.UNDETERMINED;
        }

        Binding binding = new Binding(document);
        script.setBinding(binding);

        try {
            Object result = script.run();
            return Boolean.TRUE.equals(result)
                ? EvaluationState.MATCHED
                : EvaluationState.NOT_MATCHED;
        } catch (Exception e) {
            return EvaluationState.UNDETERMINED;
        }
    }
}
```

**Pros**:
- Full programming language power
- Excellent DSL support with closures
- Native Java interoperability
- Clean, readable syntax
- Active community and development
- Hot reloading with GroovyScriptEngine
- Compile to bytecode for performance

**Cons**:
- Larger runtime footprint
- Security concerns (full language access)
- Compilation overhead
- More complex to sandbox properly
- Can be "too powerful" for simple configs

**Best For**: Complex business rules requiring full programming logic, when DSL aesthetics matter.

---

### 4.2 GraalJS (JavaScript on GraalVM)

**Overview**: ECMAScript 2024 compliant JavaScript engine for Java embedding.

**Maven Dependencies**:

```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.1.0</version>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>24.1.0</version>
    <type>pom</type>
</dependency>
```

**Example JSpec DSL with JavaScript**:

```javascript
// criteria.js - External JavaScript file

const specification = {
    id: 'loan-eligibility',

    criteria: {
        'age-minimum': (doc) => doc.applicant.age >= 18,

        'credit-good': (doc) => doc.financial.creditScore > 650,

        'approved-states': (doc) =>
            ['CA', 'NY', 'TX', 'FL'].includes(doc.applicant.address.state),

        'income-range': (doc) =>
            doc.financial.annualIncome >= 50000 &&
            doc.financial.annualIncome <= 500000,

        'email-valid': (doc) =>
            /^[\w.]+@[\w.]+\.[a-z]+$/.test(doc.applicant.contact.email),

        'income-diverse': (doc) =>
            ['salary', 'investments'].every(src =>
                doc.financial.incomeSources.includes(src))
    },

    composites: {
        'basic-eligibility': {
            junction: 'AND',
            refs: ['age-minimum', 'credit-good', 'approved-states']
        }
    }
};

// Export for Java
specification;
```

**Java Integration**:

```java
public class GraalJSCriterionEvaluator {
    private final Context context;
    private Value specification;

    public GraalJSCriterionEvaluator() {
        this.context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(className -> false)  // Security
            .build();
    }

    public void loadCriteria(Path jsFile) throws IOException {
        String script = Files.readString(jsFile);
        specification = context.eval("js", script);
    }

    public EvaluationState evaluate(String criterionId, Map<String, Object> document) {
        Value criteria = specification.getMember("criteria");
        Value criterion = criteria.getMember(criterionId);

        if (criterion == null || !criterion.canExecute()) {
            return EvaluationState.UNDETERMINED;
        }

        try {
            // Convert Java Map to JS object
            Value jsDoc = context.eval("js", "(" + toJson(document) + ")");
            Value result = criterion.execute(jsDoc);

            return result.asBoolean()
                ? EvaluationState.MATCHED
                : EvaluationState.NOT_MATCHED;
        } catch (Exception e) {
            return EvaluationState.UNDETERMINED;
        }
    }

    public void close() {
        context.close();
    }
}
```

**Alternative: Expression-Only Approach**:

```java
// Simple expression evaluation
public EvaluationState evaluateExpression(String expression, Map<String, Object> doc) {
    String script = String.format("(doc) => %s", expression);
    Value fn = context.eval("js", script);
    Value jsDoc = context.eval("js", "(" + toJson(doc) + ")");
    return fn.execute(jsDoc).asBoolean()
        ? EvaluationState.MATCHED
        : EvaluationState.NOT_MATCHED;
}
```

**Pros**:
- Familiar JavaScript syntax
- ECMAScript 2024 features (latest)
- Excellent array/object manipulation
- Native regex support
- Good performance with GraalVM
- Large developer familiarity

**Cons**:
- Requires GraalVM for best performance
- Larger dependency footprint
- Context creation overhead
- Memory usage concerns
- Polyglot complexity

**Best For**: Teams familiar with JavaScript, or when modern JS features (async, destructuring) are valuable.

---

## 5. Comparison Matrix

### 5.1 Feature Comparison

| Feature | ANTLR | MVEL | SpEL | JEXL | Groovy | GraalJS |
|---------|-------|------|------|------|--------|---------|
| Custom Syntax | Full | No | Limited | Limited | DSL Support | No |
| Learning Curve | High | Low | Low | Low | Medium | Low |
| Runtime Reload | Yes | Yes | Yes | Yes | Yes | Yes |
| Compilation | Lexer/Parser | Bytecode | Optional | JIT | Bytecode | JIT |
| Type Safety | Grammar | Runtime | Runtime | Runtime | Optional | Runtime |
| Sandboxing | N/A | No | Limited | Yes | Yes | Yes |
| IDE Support | Plugin | None | Spring Tools | None | Excellent | Good |

### 5.2 Performance Comparison

| Option | Startup | Execution | Memory | Caching |
|--------|---------|-----------|--------|---------|
| ANTLR | Medium | Fast | Low | Manual |
| MVEL | Fast | Very Fast | Low | Built-in |
| SpEL | Fast | Fast | Medium | Compiled mode |
| JEXL | Fast | Fast | Low | Built-in |
| Groovy | Slow | Fast | High | Script cache |
| GraalJS | Slow | Medium-Fast | High | JIT warm-up |

### 5.3 Suitability for JSpec

| Requirement | Best Options |
|-------------|--------------|
| Clean custom syntax | ANTLR, Groovy DSL |
| Quick implementation | MVEL, JEXL, SpEL |
| Best performance | MVEL |
| Best documentation | SpEL |
| Most familiar syntax | GraalJS, Groovy |
| Smallest footprint | MVEL, JEXL |
| Best security | JEXL, GraalJS |

---

## 6. Recommended Approach

### 6.1 Primary Recommendation: JEXL

**Why JEXL**:

1. **Clean syntax** - Dot notation works naturally: `applicant.age >= 18`
2. **Built-in operators** - `=~` for regex and collection membership
3. **No heavy dependencies** - Apache Commons only
4. **Good sandboxing** - Permission controls available
5. **Reasonable performance** - Expression caching built-in
6. **Easy implementation** - Minimal code to integrate

**Example Configuration Format**:

```yaml
# jspec-criteria.yaml
id: loan-eligibility

criteria:
  age-minimum:
    expression: "applicant.age >= 18"

  credit-good:
    expression: "financial.creditScore > 650"

  approved-states:
    expression: "applicant.address.state =~ ['CA', 'NY', 'TX', 'FL']"

  income-range:
    expression: "financial.annualIncome >= 50000 && financial.annualIncome <= 500000"

  email-valid:
    expression: "applicant.contact.email =~ /^[\\w.]+@[\\w.]+\\.[a-z]+$/"

  income-diverse:
    expression: "financial.incomeSources.containsAll(['salary', 'investments'])"

composites:
  basic-eligibility:
    junction: AND
    criteria:
      - age-minimum
      - credit-good
      - approved-states

  overall-eligibility:
    junction: AND
    criteria:
      - basic-eligibility
      - income-range
```

### 6.2 Alternative: ANTLR Custom DSL

**When to choose ANTLR**:

- Need complete control over syntax
- Want MongoDB-style operator consistency (`$gte` instead of `>=`)
- Plan to generate parsers for multiple languages
- Have resources for grammar development

**Example Custom Syntax**:

```
specification loan-eligibility {

    criterion age-minimum {
        applicant.age $gte 18
    }

    criterion approved-states {
        applicant.address.state $in [CA, NY, TX, FL]
    }

    composite basic-eligibility {
        and {
            +age-minimum
            +approved-states
        }
    }
}
```

### 6.3 Alternative: Groovy DSL

**When to choose Groovy**:

- Complex business logic requiring full programming
- DSL aesthetics are important
- Team has Groovy experience
- Need hot-reloading of complex scripts

---

## 7. Implementation Roadmap

### Phase 1: JEXL Integration (2-3 weeks)

1. Add JEXL dependency
2. Create `JexlCriterionEvaluator` class
3. Define YAML schema for JEXL expressions
4. Implement expression compilation and caching
5. Add sandboxing configuration
6. Write unit tests
7. Document syntax mapping

### Phase 2: Hot Reload Support (1 week)

1. Implement file watching
2. Add reload API
3. Handle compilation errors gracefully
4. Add metrics/logging for reloads

### Phase 3: Advanced Features (2 weeks)

1. Custom function registration
2. Type coercion helpers
3. Performance optimization
4. Integration with existing formatters

### Optional Phase: ANTLR Custom DSL (4-6 weeks)

1. Design grammar specification
2. Implement ANTLR grammar
3. Create AST visitor for Specification building
4. Add syntax error handling
5. Create IDE support (syntax highlighting)
6. Write comprehensive documentation

---

## 8. Conclusion

For runtime-configurable specifications without rebuild, **JEXL** provides the best balance of:

- Familiar Java-like syntax
- Easy implementation
- Reasonable performance
- Good security options
- Minimal dependencies

The existing YAML format handles structure (specification, composites, references), while JEXL handles the criterion expressions. This hybrid approach maintains backwards compatibility while adding powerful runtime evaluation.

For teams wanting complete syntax control, **ANTLR** enables a truly custom DSL that can mirror the existing MongoDB-style operators exactly, but requires significant upfront investment.

### Decision Matrix

| If you need... | Choose... |
|----------------|-----------|
| Quick implementation | JEXL |
| Best performance | MVEL |
| Spring ecosystem | SpEL |
| Custom syntax | ANTLR |
| Full programming | Groovy |
| JavaScript familiarity | GraalJS |

---

*Document Version: 1.0*
*Created: 2025-11-19*
*Status: Proposal*
