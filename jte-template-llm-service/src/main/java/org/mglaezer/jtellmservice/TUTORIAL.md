# Creating Type-Safe Prompt Templates with JTE for Java LLM Services

## 1. Introduction

- Benefits of template-based approach for prompt engineering

A template-based approach to prompt engineering offers significant advantages for LLM-powered applications. Type safety
prevents runtime errors through compile-time validation, ensuring all required parameters are present before execution.
This approach creates a clear separation of concerns, isolating prompt design from business logic for better
maintainability.

Templates enable modular composition of prompts through reusable components, reducing duplication and establishing
consistent patterns across an application. They can be version-controlled like regular code, making prompt evolution
trackable with clear change history. JTE's precompilation strategy delivers performance benefits by eliminating runtime
parsing overhead, generating optimized Java classes instead. Together, these advantages create more robust,
maintainable, and efficient LLM applications that can evolve with changing requirements.

## Shortcomings of LangChain4J's PromptTemplate

The current at the time of writing templating solution in LangChain4J is PromptTemplate.

While adequate for simple prompts,
LangChain4J's PromptTemplate has significant shortcomings for advanced prompt engineering:

- **Limited syntax**: No conditionals, loops, or template inheritance
- **Restricted expression language**: Only basic variable substitution without inline logic
- **No type safety**: Potential runtime errors from missing parameters
- **Runtime parsing**: Performance bottlenecks without precompilation
- **Lack of modularization**: No includes or reusable components
- **Missing context-aware escaping**: Potential security risks
- **No IDE integration**: Absence of debugging or real-time validation

## Benefits of using JTE for prompt management

JTE addresses these limitations with:

- **Rich templating features**: Template inheritance, conditional logic, and loops
- **Type-safe includes**: Compile-time validation of template parameters
- **Precompilation**: Optimized performance with generated Java classes
- **Native Java expressions**: Full access to Java/Kotlin in templates
- **Modular design**: Reusable components and template composition
- **IDE support**: Syntax highlighting and debugging capabilities
- **Security features**: Context-aware escaping and output handling

In this tutorial, we'll explore how to create a type-safe prompt template system using JTE and Java's dynamic proxies.

## Step-by-Step Development Plan

How JteTemplateLlmServiceFactory Will Work
The JteTemplateLlmServiceFactory will implement a type-safe approach to LLM prompt templating using JTE (Java Template
Engine). Here's how it will work:

Service Interface Definition

Developers will define service interfaces with methods annotated with @PromptTemplate to specify template files
Method parameters will use @PromptParam to map values to named template parameters
Dynamic Proxy Generation

The factory will create a runtime implementation of the service interface
This proxy will intercept method calls to process templates and communicate with the LLM
Template Processing

When a method is called, the proxy will:
Identify the correct template file from annotations
Map method parameters to template variables based on annotation metadata
Render the JTE template with the provided parameters
LLM Integration

The rendered template will become a complete prompt string
The prompt will be passed to the LLM service function (from LangChain4j or any other library)
The LLM response will be automatically converted to the method's return type
Response Handling

The conversion from LLM response to Java objects will be handled by the underlying LLM library
For structured data like our Poem record, JSON responses will be deserialized automatically
If structured response handling isn't supported, the factory will work with the returned String

Now let's build the factory step-by-step:

# Creating Annotations

In this section, we'll create the two annotations needed for our templating system:

PromptTemplate Annotation
First, let's create the @PromptTemplate annotation that will connect methods to JTE template files:

```java

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PromptTemplate {
    /**
     * Path to the JTE template file, relative to the root specified in the factory.
     */
    String fileName();
}
```

The @PromptTemplate annotation will be applied to methods in our service interfaces. The key features are:
fileName() - Specifies which JTE template file to use for rendering

PromptParam Annotation
Next, let's create the @PromptParam annotation to map method parameters to template variables:

```java

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PromptParam {
    /**
     * The name of the parameter as it will appear in the JTE template.
     * If not specified, the parameter name from the compiled code will be used.
     */
    String value();
}
```

The @PromptParam annotation will be applied to parameters in our service interface methods:

@Retention(RetentionPolicy.RUNTIME) - Makes it available at runtime
@Target(ElementType.PARAMETER) - Restricts it to method parameters
value() - Defines the name that will be used in the template to reference this parameter

# Designing the Factory Class Structure

The JteTemplateLlmServiceFactory will be the core class that connects our annotated interfaces with JTE templates and
the LLM. Here's how we'll structure it:

```java
public class JteTemplateLlmServiceFactory<ResponseType> {
    private final Function<String, ResponseType> llmResponseProvider;
    private final CodeResolver codeResolver;

    public JteTemplateLlmServiceFactory(Function<String, ResponseType> llmResponseProvider, CodeResolver codeResolver) {
        this.llmResponseProvider = llmResponseProvider;
        this.codeResolver = codeResolver;
    }

    public <S> S create(Class<S> serviceInterface) {
        // We'll implement this method in the next section
        return null;
    }
}
```

Key design elements:

Generic Type Parameter <ResponseType>

Represents the return type from the LLM service
Makes the factory flexible to work with different response types.

Constructor Parameters
Function<String, ResponseType> llmResponseProvider - Takes a rendered template string and returns an object of type
ResponseType
we expect this implementation from langchain4j or any other LLM library. In case some llm library does not support
structured data, we can use String as the ResponseType.
CodeResolver codeResolver - standard JTE interface to specify the root of the templates.

Service Creation Method

```create(Class<S> serviceInterface)``` - Will generate a dynamic implementation of the interface
Returns type S, which is the service interface type the client wants to use
Will use Java's reflection and proxies to create the implementation
This factory will act as a bridge between:

Template files managed by JTE
Service interfaces defined by the developer
The LLM function (from langchain4j or any other library) that processes prompts
The generic structure allows for different LLM backends while maintaining type safety throughout the process.

# Implement Dynamic Proxy Generation

The core of our implementation uses Java's dynamic proxy mechanism to create runtime implementations of service
interfaces. Here's how we implement the proxy generation:

```java

public <T> T create(Class<T> serviceInterface) {
    validateServiceInterface(serviceInterface);
    return (T) Proxy.newProxyInstance(
            serviceInterface.getClassLoader(),
            new Class<?>[]{serviceInterface},
            new JteTemplateLlmInvocationHandler(serviceInterface));
}
```

This method:

Validates the service interface - Checks that all methods have proper annotations and template parameters match
Creates a dynamic proxy - Uses Java's built-in Proxy class to generate an implementation
Configures the proxy with:
The interface's class loader
The interface to implement
A custom invocation handler
The invocation handler is implemented as an inner class:

```java
private class JteTemplateLlmInvocationHandler implements InvocationHandler {
    private final Class<?> serviceInterface;

    private JteTemplateLlmInvocationHandler(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        String prompt = preparePrompt(method, args, serviceInterface.getPackage());
        return llmResponseProvider.apply(prompt);
    }
}

```

The handler:

Stores a reference to the service interface class
Handles method calls from the proxy
Special-cases Object methods like equals() and toString()
For service methods, processes templates and calls the LLM function
This dynamic proxy approach eliminates the need to write concrete implementations for each service interface, with the
proxy intercepting method calls at runtime and applying our template processing logic.

# Build Template Processing Logic

Now that we've implemented the dynamic proxy mechanism, we need to focus on the core template processing logic. The
preparePrompt method handles this critical functionality:

```java
private String preparePrompt(Method method, Object[] args, Package pkg) {
    String packagePath = pkg.getName().replace('.', '/');
    PromptTemplate promptAnnotation = method.getAnnotation(PromptTemplate.class);
    String templatePath = packagePath + "/" + promptAnnotation.fileName();

    Map<String, Object> params = new HashMap<>();
    Parameter[] parameters = method.getParameters();

    for (int i = 0; i < parameters.length; i++) {
        PromptParam paramAnnotation = parameters[i].getAnnotation(PromptParam.class);
        if (paramAnnotation != null) {
            params.put(paramAnnotation.value(), args[i]);
        }
    }

    StringOutput output = new StringOutput();
    templateEngine.render(templatePath, params, output);
    return output.toString();
}
```

This implementation performs three key operations:

Extract template paths from annotations

Converts the Java package name to a file path format (org.example → org/example)
Retrieves the template filename from the @PromptTemplate annotation
Combines these to form the complete template path
Map method parameters to template variables

Processes each method parameter with its corresponding argument
For parameters annotated with @PromptParam, extracts the variable name
Creates a parameter map where keys are template variable names and values are the actual arguments
Implement JTE template rendering

Creates a StringOutput to capture the rendered content
Passes the template path and parameter map to JTE's template engine
Renders the template with all variables substituted
Returns the complete rendered prompt as a string
The template processing logic maintains type safety by using JTE's capabilities to validate templates at compile time,
ensuring that all required template parameters are provided and correctly typed.

# Add Method Invocation Handling

The core of our dynamic proxy implementation is the invocation handler, which intercepts method calls and processes them
using our templating system. Let's examine how this works:

```java
private class JteTemplateLlmInvocationHandler implements InvocationHandler {
    private final Class<?> serviceInterface;

    private JteTemplateLlmInvocationHandler(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        String prompt = preparePrompt(method, args, serviceInterface.getPackage());
        return llmResponseProvider.apply(prompt);
    }
}
```

The invocation handler performs these key operations:

Method interception

Implements Java's InvocationHandler interface to catch all method calls on the proxy
Stores a reference to the service interface to maintain context for template resolution
Receives the proxy instance, called method, and argument values at runtime
Special method handling

Checks if the method belongs to Object.class (like toString(), equals(), etc.)
For Object methods, delegates to the handler's own implementation using method.invoke(this, args)
This prevents LLM calls for basic Object functionality
Template processing

For service interface methods, calls preparePrompt() to process templates
Uses method reflection data to locate template files and extract parameters
Renders the template with the provided arguments
LLM integration

Takes the fully rendered prompt string and passes it to the llmResponseProvider
The provider function handles the actual communication with the LLM
Returns the LLM response directly, which will be cast to the method's return type
This invocation handler design keeps the proxy implementation clean and focused, with clear separation between
intercepting method calls, processing templates, and communicating with the LLM service.

# Implement Response Processing

Response processing is streamlined by the factory's generic type design. Let's examine how responses from the LLM are
handled and converted to the expected return types:

```java
public class JteTemplateLlmServiceFactory<ResponseType> {
    private final Function<String, ResponseType> llmResponseProvider;

    // Constructor and other methods...

    // Inside JteTemplateLlmInvocationHandler.invoke()
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // ... template processing code ...
        String prompt = preparePrompt(method, args, serviceInterface.getPackage());
        return llmResponseProvider.apply(prompt);
    }
}
```

The response processing system works through:

Generic Type Declaration

The factory uses a type parameter <ResponseType> to define what all methods will return
This ensures type consistency across the service interface
Function-based Integration

The Function<String, ResponseType> provides a clean abstraction for any LLM service
It accepts a rendered prompt string and produces the typed response
This allows integration with any LLM library that can be adapted to this functional interface
Direct Return Type Handling

The proxy returns the result of llmResponseProvider.apply(prompt) directly
Java's dynamic proxy mechanism automatically casts this to the method's declared return type
No manual conversion code is needed for each method
Integration Examples

With LangChain4j: Function<String, Poem> aiService = langChain4jPoemService::compose;
With other LLM libraries: Wrap their API calls in a function that handles deserialization
This approach provides complete flexibility in how responses are processed. The underlying LLM library (like
LangChain4j) typically handles the conversion from raw LLM text to structured Java objects through mechanisms like JSON
deserialization.

# Add Error Handling and Validation

A robust templating system requires thorough validation to catch configuration errors early. Without proper validation,
issues like missing parameters or incorrect template paths would only surface at runtime, potentially causing production
failures.

Our implementation includes several validation checks performed at service creation time:

Method Annotation Validation

Each service interface method must be annotated with @PromptTemplate
This ensures every method has an associated template file
Early validation prevents runtime errors when calling methods
Parameter Annotation Validation

All method parameters must be annotated with @PromptParam
Prevents parameters from being silently ignored during template rendering
Makes the parameter mapping explicit and intentional
Template Existence Verification

Template paths are resolved using the interface package structure
The system verifies templates exist before any method is called
Fails fast if templates are missing or incorrectly referenced
Parameter Consistency Validation

Compares template parameter requirements with method parameters
Detects missing parameters needed by the template
Identifies extra parameters provided by methods but not used in templates
Ensures bidirectional consistency between code and templates
Detailed Error Reporting

Error messages clearly identify which method or parameter has issues
Includes specific information about missing or extra parameters
References template paths to help quickly locate problems
This comprehensive validation approach creates a strong contract between service interfaces and templates. By validating
everything at service creation time rather than method invocation, the system catches configuration errors immediately
during application startup or testing.

The validation system enforces type safety and parameter consistency, ensuring that when a method is called, all
necessary template parameters will be available and correctly mapped. This "fail fast" philosophy prevents subtle errors
from propagating to production environments.

The complete implementation of these validation checks is available in the GitHub project referenced at the end of this
tutorial.

## 5. Complete Example: Poem Generation Service

Let's explore a practical example of our template-based approach with a poem generation service. This section walks
through creating a complete application that generates poems according to structured instructions.

Domain Model
First, we define a domain model to represent poems and generation instructions:

```java
public record PoemInstructions(
        String theme,
        String style,
        String rhymeScheme,
        List<StanzaInstructions> stanzaInstructions) {
}

public record StanzaInstructions(String stanzaIdea, boolean okToDeviate) {
}

public record Poem(String title, String content) {
}

```

TBD: need to explain the purpose of the above.

Service Interfaces
We define two service interfaces: one for direct LLM interaction (via langchain4j) and another for our template-based
approach:

```java
// Plain functional interface for converting a prompt String into a Poem
// It will be created by LangChain4j via AiServices
public interface LangChain4jPoemService {
    Poem compose(String prompt);
}

// Templated service interface. Will be created by JteTemplateLlmServiceFactory
public interface TemplatedPoemService {
    @PromptTemplate(fileName = "compose_poem_prompt.jte")
    Poem composePoem(@PromptParam("instructions") PoemInstructions instructions);
}
```

The key differences are:

LangChain4jPoemService takes a raw string prompt
TemplatedPoemService uses a structured PoemInstructions object and connects to a JTE template

Putting It All Together
Now we connect everything in one method for convenience:

```java
public static void main(String[] args) {
    // 1. Create the underlying LLM model
    OpenAiChatModel model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4o")
            .maxTokens(4 * 1024)
            .build();

    // 2. Create the LangChain4jPoemService via AiServices
    // You can add rag, memory, tools, etc. here
    LangChain4jPoemService langChain4jPoemService = AiServices.builder(LangChain4jPoemService.class)
            .chatLanguageModel(model)
            .build();

    // 3. Wrap the LangChain4jPoemService as a Function<String, Poem>
    Function<String, Poem> aiService = langChain4jPoemService::compose;

    // 4. Create a CodeResolver for JTE templates
    CodeResolver codeResolver = new DirectoryCodeResolver(Path.of("src/main/java"));

    // 5. Create a factory for our templated service
    JteTemplateLlmServiceFactory<Poem> factory =
            new JteTemplateLlmServiceFactory<>(aiService, codeResolver);

    // 6. Create an instance of the templated service
    TemplatedPoemService service = factory.create(TemplatedPoemService.class);

    // 7. Create poem instructions with structured data
    Poem poem = service.composePoem(new PoemInstructions(
            "Java is the best language",
            "Simple contemporary style so that even programmers can read it.",
            "ABAB",
            List.of(
                    new StanzaInstructions("The beauty of Java", true),
                    new StanzaInstructions("Mention it's rival Python in a condescending tone", false))));

    // 8. Display the generated poem
    System.out.println(poem);
}

```

## 6. Conclusion

The approach demonstrated in this tutorial provides a solid foundation for building maintainable, type-safe LLM
prompting in Java. By leveraging JTE's powerful templating capabilities and Java's reflection system, we've created a
framework that brings software engineering best practices to prompt engineering.