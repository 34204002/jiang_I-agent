package com.jiang.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek DSML 格式解析器——从 content 流中提取函数调用信息。
 * <p>
 * DeepSeek V3.2 不支持 OpenAI 标准 {@code delta.tool_calls}，
 * 改为在 {@code delta.content} 中输出 DSML 格式的函数调用：
 * <pre>{@code
 * <DSML|function_calls>
 * <DSML|invoke name="get_current_time">
 * </DSML|invoke>
 * </DSML|function_calls>
 * }</pre>
 * 全角竖线 {@code ｜}（U+FF5C）和半角 {@code |}（U+007C）均兼容。
 */
@Slf4j
public class DsmlParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern INVOKE = Pattern.compile("DSML[^<]*invoke name=\"([^\"]+)\"");
    private static final Pattern PARAM  = Pattern.compile(
            "DSML[^<]*parameter name=\"([^\"]+)\"[^>]*>([^<]*)</DSML[^<]*parameter>");

    /** 在文本中搜索 DSML 起始位置，-1 表示未找到 */
    public static int indexOf(String text) {
        int idx = text.indexOf("<DSML|");
        if (idx >= 0) return idx;
        return text.indexOf("<DSML｜");
    }

    /**
     * 检查字符串末尾是否残留了 DSML 起始标签的前缀（如 &lt;、&lt;D、&lt;DSML）。
     * 返回残留部分；如果没有，返回空字符串。
     */
    public static String trailingPrefix(String s) {
        String[] starts = {"<DSML｜", "<DSML|"};
        for (String tag : starts) {
            for (int k = tag.length() - 1; k >= 1; k--) {
                if (s.endsWith(tag.substring(0, k))) {
                    return s.substring(s.length() - k);
                }
            }
        }
        return "";
    }

    /**
     * 从 DSML 块中提取工具名和参数 JSON。
     * @param dsml  完整的 DSML 文本（含 &lt;DSML...&gt; 到 &lt;/DSML...&gt;）
     * @param nameOut  [0] 接收函数名，未匹配到则保持原值
     * @param argsOut  接收参数 JSON 字符串（无参数时填 "{}"）
     */
    public static void parse(String dsml, String[] nameOut, StringBuilder argsOut) {
        log.debug("DSML 原始: {}", dsml);
        Matcher m = INVOKE.matcher(dsml);
        if (m.find()) {
            nameOut[0] = m.group(1);
            log.info("DSML 工具调用: name={}", nameOut[0]);
        } else {
            log.warn("DSML invoke name 未匹配到! dsml 长度={}", dsml.length());
        }

        Matcher pm = PARAM.matcher(dsml);
        Map<String, String> params = new LinkedHashMap<>();
        while (pm.find()) {
            params.put(pm.group(1), pm.group(2));
        }
        if (!params.isEmpty()) {
            try {
                argsOut.append(MAPPER.writeValueAsString(params));
            } catch (Exception e) {
                argsOut.append("{}");
            }
        } else {
            argsOut.setLength(0);
            argsOut.append("{}");
        }
        log.info("DSML 参数: {}", argsOut);
    }
}
