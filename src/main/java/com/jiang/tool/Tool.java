package com.jiang.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 Agent 可调用的工具。
 * 被标注的方法所在类必须是 Spring Bean。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /** 工具名称（唯一标识，LLM 用此名称调用），如 create_todo */
    String name();

    /** 工具描述（LLM 用来判断何时调用） */
    String description();

    /** JSON Schema 格式的参数定义（OpenAI function calling 格式） */
    String parameters() default "{}";
}
