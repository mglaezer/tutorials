# Advanced Prompt Templating with JTE for Java LLM Services including LangChain4J

## 1. Introduction

In this tutorial, we'll build a templating system for LLM prompts using Java Template Engine (JTE).
We'll create a universal solution that will work with any LLM service, including LangChain4J.

### 1.1. The Challenge of Prompt Engineering

When developing applications with Large Language Models (LLMs), prompt engineering becomes an important part of our
work. As our applications grow, we face several challenges:

1. Prompts become increasingly complex
2. Data structures for LLM communication get more sophisticated
3. We need to reuse prompt components across different use cases

Without a good templating system, managing these elements directly in Java code quickly becomes difficult to maintain.

### 1.2. Benefits of Templating for LLM Applications

Templates offer three key benefits for LLM applications:

First, they let us build modular, reusable components. This reduces code duplication and helps maintain consistent
patterns across our application.

Second, templates provide advanced control flows like loops and conditionals. This means we can create more
sophisticated prompts without making our code complex.

Third, since templates are stored as files, we can track their changes in version control just like regular code. This
gives us a clear history of how our prompts evolve over time.

These benefits help us create LLM applications that are easier to maintain and adapt as requirements change.

### 1.3. Current Limitations in LangChain4J

At the time of writing, the primary templating solution in LangChain4J is PromptTemplate. While adequate for simple
prompts, it has significant
shortcomings for advanced prompt engineering:

Currently, LangChain4J's PromptTemplate offers basic variable substitution, but lacks features needed for advanced
prompt engineering:

- No inline logic like conditionals or loops
- Limited IDE support without syntax highlighting or type validation
- No simple way to create reusable components through includes

### 1.4. JTE as a Solution

JTE addresses these limitations comprehensively:

JTE provides rich templating features including conditional logic and loops. It supports template composition with
includes. For development efficiency, JTE offers IDE plugins with syntax highlighting and type validation.

In this tutorial, we'll explore how to use these JTE features to build a robust templating system for LLM applications.

## 2. Step-by-Step Development Plan

In this section, we'll outline our approach to building a templating system for LLM applications that works
with any LLM library.

### 2.1. System Architecture Overview

Our templating system will use four main components:

- Annotated service interfaces define the LLM interactions
- External JTE templates contain the prompt structures
- A factory creates dynamic implementations at runtime
- A universal adapter function connects to any LLM implementation

While LangChain4j has its own annotation-driven system, our approach will follow a similar pattern to offer more
powerful templating features.

### 2.2. Service Interface Definition

Developers will define service interfaces with:

- Methods annotated to specify template files
- Parameters annotated to map values to named template variables

This annotation-based approach creates a clean separation between the service definition and templating details.

### 2.3. Dynamic Proxy Generation

At runtime the framework will work by:

- Creating implementations of service interfaces using Java's dynamic proxy mechanism
- Identifying the correct template file from annotations
- Mapping method parameters to template variables based on annotation metadata
- Rendering the JTE template with the provided parameters
- Processing the call by passing the resolved prompts to underlying services

## 3. Creating Annotations

In this section, we'll create the two annotations needed for our templating system.

An example of usage is shown below:

```java
public interface DocumentAnalysisService {

    @PromptTemplate(fileName = "analyze_document.jte")
    AnalysisReport analyzeLegalDocument(
            @PromptParam("document") LegalDocument document,
            @PromptParam("analysisOptions") AnalysisOptions options
    );
}
```

### 3.1. The PromptTemplate Annotation

First, we need an annotation to link methods with template files:

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

This annotation marks methods that should use template-based prompt generation. The fileName() specifies which template
to use for the annotated method.

### 3.2. The PromptParam Annotation

Next, we need a way to map method parameters to template variables:

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

This annotation maps method parameters to template variables. When called, annotated parameters are passed to the
template using the specified name in value().

## 4. Designing the Factory Class

Next, we'll design a factory class to bridge annotated interfaces with JTE templates and LLM services.

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

The factory design has three key design elements:

### 4.1. Generic Response Type

The `<ResponseType>` parameter defines the LLM service's return type. For services returning simple text, use `String`
instead of complex structures.

### 4.2. Constructor Parameters

The factory requires:

1. LLM Service Function (`Function<String, ResponseType>`):
    - The underlying LLM service
    - Input: rendered prompt string
    - Output: response of specified type
    - Compatible with any LLM library, including LangChain4j

2. Template Source (JTE's `CodeResolver`):
    - Locates template files
    - Supports file system or resource paths
    - Supports placing templates near Java files for development

### 4.3. Service Creation Method

The `create()` method:

- Takes: service interface class
- Returns: working implementation that handles template loading and parameter mapping

## 5. Implementing Dynamic Proxy Generation

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

### 5.1. Dynamic Proxy Creation

The factory creates service implementations at runtime using Java's proxy mechanism. It:

- Validates the service interface for correct annotations and template parameters
- Uses `Proxy.newProxyInstance()` to generate a concrete implementation
- Connects the proxy to a custom invocation handler
- Returns a working implementation of the requested interface

### 5.2. Custom Invocation Handler

The invocation handler processes all method calls and is implemented as an inner class:

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

### 5.3. Method Interception Flow

The handler processes each method call as follows:

- Intercepts all method calls to the proxy instance
- Handles Object methods (`equals()`, `toString()`) separately
- Renders templates and forwards prompts to the LLM
- Returns responses in the method's declared type

### 6. Template Processing Logic

After setting up the proxy mechanism, we implement the template processing logic in the `preparePrompt` method:

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

### 6.1. Template Path Resolution

The implementation resolves template paths by:

- Converting package names to file paths (org.example → org/example)
- Getting the template filename from `@PromptTemplate`
- Combining these to form the complete template path

Templates can be stored alongside service interfaces, in resources folder, or externally.

We will leave additional variants of template path resolution as an exercise for the reader.
Those can include placing templates in the root directory or even in the database.
If some flexibility is required, it is better to create a template path resolution strategy
using the Strategy design pattern. If even more flexibility is required, the reader
can use the Strategy design pattern not only for template path resolution, but also for template rendering.

### 6.2. Parameter Mapping

The method maps Java parameters to template variables by:

- Extracting variable names from `@PromptParam` annotations
- Creating a map of template variable names to argument values

### 6.3. Template Rendering

The final step renders the template:

- Creates JTE's `StringOutput` for rendered content
- Passes template path and parameter map to JTE
- Returns the complete rendered prompt as a string

This rendering process ensures all required template parameters are provided.

## 7. Error Handling and Validation

The system performs several validation checks when creating services:

Each service interface method must have a `@PromptTemplate` annotation to specify its template file.

All method parameters must have `@PromptParam` annotations to:

- Ensure explicit parameter mapping
- Prevent parameters from being silently ignored

JTE's type checking provides additional validation during template rendering.

The reader can find the complete implementation in
the [JteTemplateLlmServiceFactory](src/main/java/org/mglaezer/jtellmservice/JteTemplateLlmServiceFactory.java) class.

## 8. Complete Example: Poem Composing Service

Let's explore a complete example of using LLM to compose poems with our template-based approach.

### 8.1. Domain Model

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

These record classes provide a structured way to:

- Define poem generation parameters with `PoemInstructions`
- Specify individual stanza details with `StanzaInstructions`
- Represent the generated output using the `Poem` class

### 8.2. Template Design

The system combines two JTE template files to construct the complete prompt:

#### 8.2.1. Main Template (compose_poem_prompt.jte)

```jte
@param org.mglaezer.jtellmservice.Tutorial.PoemInstructions instructions

You are a skilled poet. Please compose a poem with the following specifications:

THEME: ${instructions.theme()}
STYLE: ${instructions.style()}
RHYME SCHEME: ${instructions.rhymeScheme()}

STANZA INSTRUCTIONS:
@for(var stanzaInstructions : instructions.stanzaInstructions())
@template.org.mglaezer.jtellmservice.compose_stanza_instructions(stanzaInstructions = stanzaInstructions)
@endfor

Please return your response in the following JSON format:
{
  "title": "Your creative title for the poem",
  "content": "The full text of your poem with proper formatting"
}
```

This main template demonstrates several key features:

- Type-safe parameter declaration with full package path
- Direct access to object properties via method calls (`instructions.theme()`)
- Control flow with a `@for` loop to iterate through stanza instructions
- Template composition by including the stanza instructions sub-template
- Structured output format instructions for consistent JSON responses (while output structuring is a complex topic
  beyond
  this tutorial's scope, this simple instruction suffices for our example)

#### 8.2.2. Sub-Template (compose_stanza_instructions.jte)

```jte
@param org.mglaezer.jtellmservice.Tutorial.StanzaInstructions stanzaInstructions

- Stanza idea: ${stanzaInstructions.stanzaIdea()}
  @if(stanzaInstructions.okToDeviate())
  (You may creatively adapt this idea if it improves the poem)
  @else
  (Please adhere closely to this idea)
  @endif
```

This sub-template shows:

- Modular reusability through template composition
- Conditional rendering with `@if/@else` statements
- Property access from nested model objects
- Clean separation of concerns with focused sub-templates

The template system creates a clean separation between:

1. The structure of the prompt (in templates)
2. The data model (Java records)
3. The service definition (interface)
4. The LLM integration (factory and invocation handler)

### 8.3. Service Interfaces

Our implementation defines two distinct service interfaces: an underlying LangChain4j interface for direct LLM
interaction, and a template-based interface for structured prompt generation introduced in this article:

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

The key differences between these interfaces:

- `LangChain4jPoemService` takes a resolved string prompt
- `TemplatedPoemService` uses a structured `PoemInstructions` object and connects to a JTE template, providing better
  organization and type safety.

### 8.4. Putting It All Together

Now we connect everything in the application's main method:

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

One of the possible outputs:

```
=== Coffee and Code ===

Java flows like water, clean and bright,
With objects built to last through night and day.
Its structure gives our programs strength and light,
A language where the best minds love to play.

Python slithers slow, a beginner's choice,
With spaces that confuse the careful eye.
While Java stands with power in its voice,
Python's simple tricks make real coders sigh.

```

This implementation demonstrates:

1. Creating the underlying OpenAI model
2. Building the LangChain4j service using its factory (this can be any LLM library)
3. Wrapping the service as a Function compatible with our factory
4. Setting up the template resolver (likely done once in the application)
5. Creating our templated service factory
6. Instantiating the service interface
7. Using structured instructions data to generate the poem
8. Displaying the result

By using the templated approach, we gain strong type safety, explicit parameter mapping, and the ability to leverage
JTE's powerful templating features for complex prompt engineering.

Since we provided extra flexibility in choosing an LLM library, we had to wrap the LangChain4j service in a
`Function<String, Poem>` to make it factory-compatible. For LangChain4j-exclusive implementations, we could simplify
both the factory and its usage: we'd need just one templating interface and optionally provide a way to add
RAG, memory, tools, etc. to the LangChain4j service that we will build right in the factory. 
This would involve dynamically creating an interface with a single method: `ReturnType generate(String prompt);`
using a library like ByteBuddy or Java's dynamic proxy system.
We leave this optimization as an exercise for the reader. 

## 6. Conclusion

The approach demonstrated in this tutorial provides a solid foundation for building maintainable, type-safe LLM
prompting in Java. By leveraging JTE's powerful templating capabilities and Java's reflection system, we've created a
framework that brings software engineering best practices to prompt engineering.