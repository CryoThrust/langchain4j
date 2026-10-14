package dev.langchain4j.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InheritedToolOrderTest {
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
        @Tool(name = "zeta")
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
    void classAndObjectDiscoveryUseMethodOrderWithInheritedAnnotations() {
        assertThat(ToolSpecifications.toolSpecificationsFrom(Tools.class))
                .extracting(ToolSpecification::name)
                .containsExactly("zeta", "middle", "beta", "alpha");
        assertThat(ToolSpecifications.toolSpecificationsFrom(new Tools()))
                .extracting(ToolSpecification::name)
                .containsExactly("zeta", "middle", "beta", "alpha");
    }
}
