package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDiscoveryCompatibilityTest {
    public interface NamedTool {
        @Tool(name = "middle")
        String fromInterface();
    }

    public static class ParentTools {
        @Tool(name = "beta")
        public String fromParent() {
            return "parent";
        }
    }

    public static class Tools extends ParentTools implements NamedTool {
        @Tool(name = "zeta", returnBehavior = ReturnBehavior.IMMEDIATE)
        public String first() {
            return "first";
        }

        @Tool(name = "alpha")
        public String last() {
            return "last";
        }

        @Override
        public String fromInterface() {
            return "interface";
        }

        @Override
        public String fromParent() {
            return "override";
        }
    }

    @Test
    void discoveredSpecificationsAndExecutorsKeepTheSameMethodOrder() {
        Tools object = new Tools();
        List<AiServiceTool> tools = ToolService.findTools(object);
        assertThat(tools).extracting(AiServiceTool::name).containsExactly("zeta", "middle", "beta", "alpha");
        assertThat(ToolSpecifications.toolSpecificationsFrom(object))
                .containsExactlyElementsOf(
                        tools.stream().map(AiServiceTool::toolSpecification).toList());
        assertThat(tools)
                .extracting(tool -> tool.toolExecutor()
                        .execute(
                                ToolExecutionRequest.builder()
                                        .name(tool.name())
                                        .arguments("{}")
                                        .build(),
                                null))
                .containsExactly("first", "interface", "override", "last");
        assertThat(tools)
                .extracting(AiServiceTool::returnBehavior)
                .containsExactly(
                        ReturnBehavior.IMMEDIATE, ReturnBehavior.TO_LLM, ReturnBehavior.TO_LLM, ReturnBehavior.TO_LLM);
    }

    @Test
    void registeringSeparateObjectsPreservesTheirOrder() {
        ToolService service = new ToolService();
        service.tools(List.<Object>of(new ToolServiceTest.DateTool(), new Tools()));
        assertThat(service.toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("getCurrentDate", "zeta", "middle", "beta", "alpha");
    }

    private static AiServiceTool explicit(String name) {
        return AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder().name(name).build())
                .toolExecutor((request, memoryId) -> name)
                .build();
    }

    @Test
    void explicitListOrderIsPreserved() {
        ToolService service = new ToolService();
        List<AiServiceTool> tools = List.of(explicit("zeta"), explicit("alpha"));
        service.tools(tools);
        assertThat(service.toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("zeta", "alpha");
    }

    @Test
    void explicitMapOrderIsPreserved() {
        ToolService service = new ToolService();
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        for (AiServiceTool tool : List.of(explicit("zeta"), explicit("alpha"))) {
            tools.put(tool.toolSpecification(), tool.toolExecutor());
        }
        service.tools(tools);
        assertThat(service.toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("zeta", "alpha");
    }

    @Test
    void providerOrderIsPreserved() {
        ToolService service = new ToolService();
        service.toolProvider(request -> ToolProviderResult.builder()
                .add(explicit("zeta"))
                .add(explicit("alpha"))
                .build());
        UserMessage message = UserMessage.from("hello");
        ToolServiceContext context =
                service.createContext(InvocationContext.builder().build(), message, List.of(message));
        assertThat(context.effectiveTools()).extracting(ToolSpecification::name).containsExactly("zeta", "alpha");
    }
}
