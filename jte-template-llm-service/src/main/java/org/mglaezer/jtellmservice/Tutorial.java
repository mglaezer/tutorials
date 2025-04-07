package org.mglaezer.jtellmservice;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import gg.jte.CodeResolver;
import gg.jte.resolve.DirectoryCodeResolver;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class Tutorial {

    public record PoemInstructions(
            String theme, String style, String rhymeScheme, List<StanzaInstructions> stanzaInstructions) {}

    public record StanzaInstructions(String stanzaIdea, boolean okToDeviate) {}

    public record Poem(String title, String content) {
        @Override
        public String toString() {
            return """
                    === %s ===

                    %s
                    """
                    .formatted(title, content);
        }
    }

    // Plain functional interface for converting a prompt String into a Poem
    // It will be created by LangChain4j via AiServices
    //    @FunctionalInterface
    public interface LangChain4jPoemService {
        Poem compose(String prompt);
    }

    // Templated service interface. Will be created by JteTemplateLlmServiceFactory
    public interface TemplatedPoemService {
        @PromptTemplate(fileName = "compose_poem_prompt.jte")
        Poem composePoem(@PromptParam("instructions") PoemInstructions instructions);
    }

    public static void main(String[] args) {

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o")
                .maxTokens(4 * 1024)
                .build();

        // Create the LangChain4jPoemService via AiServices.
        // You can add rag, memory, tools, etc. here.
        LangChain4jPoemService langChain4jPoemService = AiServices.builder(LangChain4jPoemService.class)
                .chatLanguageModel(model)
                .build();

        // Wrap the LangChain4jPoemService as a Function<String, Poem> accepted by the factory.
        // Alternatively you can use any other LLM provider besides LangChain4j and wrap it in a Function<String, Poem>.
        Function<String, Poem> aiService = langChain4jPoemService::compose;

        // Use a CodeResolver for JTE templates (e.g., templates under src/test/java).
        // Use ResourceCodeResolver instead of DirectoryCodeResolver in production.
        CodeResolver codeResolver = new DirectoryCodeResolver(Path.of("src/main/java"));

        // Create a factory that will wrap the LangChain4jPoemService in
        JteTemplateLlmServiceFactory<Poem> factory = new JteTemplateLlmServiceFactory<>(aiService, codeResolver);

        // Create an instance of the ExamplePoemService from the factory.
        TemplatedPoemService service = factory.create(TemplatedPoemService.class);

        // Call the service method; the factory stamps the prompt and returns a Poem.
        Poem poem = service.composePoem(new PoemInstructions(
                "Java is the best language",
                "Simple so that even programmers can read it, contemporary language.",
                "ABAB",
                List.of(
                        new StanzaInstructions("The beauty of Java", true),
                        new StanzaInstructions("Mention it's rival Python in a condescending tone", false))));

        System.out.println(poem);
    }
}
