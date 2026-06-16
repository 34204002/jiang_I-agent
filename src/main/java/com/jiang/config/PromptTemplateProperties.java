package com.jiang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prompt 模板配置
 * <p>
 * 将所有 Prompt 模板集中管理在 YAML 配置文件中，便于：
 * - 非代码修改（调整 prompt 不需要重新编译）
 * - 版本管理（Git 追踪 prompt 变更历史）
 * - A/B 测试（不同环境使用不同 prompt）
 * </p>
 * <p>
 * 模板中使用 {variableName} 作为占位符，运行时通过 MessageFormat 或
 * String.format 替换。Spring AI 的 PromptTemplate 原生支持这种语法。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.prompts")
public class PromptTemplateProperties {

    /** 系统级 Prompt，定义 Agent 的角色和行为边界 */
    private String system = """
            You are a helpful AI assistant with expertise in programming and technology.
            You have access to a personal knowledge base and can help with questions.
            Always be precise, provide code examples when relevant, and cite sources.
            """;

    /** RAG 问答 Prompt，注入检索到的知识片段 */
    private String ragQuery = """
            Based on the following retrieved context, answer the user's question.
            If the context doesn't contain relevant information, say so honestly.

            Context:
            {context}

            Question: {question}
            """;

    /** 代码审查 Prompt */
    private String codeReview = """
            Review the following code carefully. Identify:
            1. Potential bugs or logical errors
            2. Performance issues
            3. Style and best practice violations
            4. Security concerns

            Code:
            {code}

            Provide specific suggestions for improvement with code examples.
            """;

    /** 知识库问答 Prompt（含图谱关联结果） */
    private String knowledgeQa = """
            You are answering a question based on a personal knowledge base.

            Relevant documents (semantic search):
            {semanticResults}

            Related concepts (knowledge graph):
            {graphResults}

            Question: {question}

            Provide a comprehensive answer that integrates both sources.
            If the sources disagree, note the contradiction.
            """;

    /** 待办管理 Prompt */
    private String todoManagement = """
            Based on the user's request, determine the appropriate action for task management.
            Available actions: CREATE, UPDATE, DELETE, LIST, COMPLETE.

            User request: {request}

            Current tasks: {currentTodos}

            Return the action and parameters in the specified format.
            """;

    /** 对话总结 Prompt */
    private String conversationSummary = """
            Summarize the following conversation in a concise manner.
            Focus on key topics discussed, decisions made, and action items.

            Conversation:
            {conversation}

            Summary:
            """;
}
