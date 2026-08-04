package com.jiang;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首字延迟（Time-To-First-Token）实测工具 — 简历"首字响应"数字的唯一可复现来源。
 *
 * <p>测量 DeepSeek 流式接口首个 content chunk 到达时间。这是整个链路里决定首字延迟的主导项，
 * 应用侧真实首字 = 本测量值 + RAG 检索 + 请求构建 + 网络往返（本地部署一般再多几十 ms）。</p>
 *
 * <p>不硬编码密钥：优先读环境变量 DEEPSEEK_API_KEY，否则从 application-dev.yml 自动读取
 * （${ENV:default} 占位符也会展开）。模型与 base-url 同样取自 dev yml，测的就是线上配置。</p>
 *
 * <p>运行（消耗少量 token，每次调用成本可忽略）：</p>
 * <pre>
 *   mvn -q test-compile exec:java -Dexec.mainClass=com.jiang.FirstTokenMeasure
 *   # 或在 IDE 里直接运行 main
 * </pre>
 */
public class FirstTokenMeasure {

    static final int RUNS = 5;

    public static void main(String[] args) throws Exception {
        String[] cfg = resolveConfig();
        String key = cfg[0];
        String baseUrl = cfg[1];
        String model = cfg[2];
        System.out.println("target base-url=" + baseUrl + " model=" + model);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        long[] firstContent = new long[RUNS];
        long[] total = new long[RUNS];

        for (int i = 0; i < RUNS; i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", "你好，请用一句话回复。")));
            body.put("stream", true);
            String json = new ObjectMapper().writeValueAsString(body);

            long t0 = System.nanoTime();
            AtomicReference<Long> tFirstContent = new AtomicReference<>();
            AtomicLong contentChars = new AtomicLong();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .timeout(Duration.ofMinutes(1))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            var resp = client.sendAsync(req, HttpResponse.BodyHandlers.ofLines()).get();
            if (resp.statusCode() != 200) {
                System.out.println("run " + i + " FAIL http=" + resp.statusCode()
                        + " " + resp.body().findFirst().orElse(""));
                continue;
            }
            resp.body()
                    .filter(l -> l.startsWith("data: "))
                    .map(l -> l.substring(6))
                    .takeWhile(d -> !"[DONE]".equals(d))
                    .forEach(d -> {
                        long now = System.nanoTime();
                        if (d.contains("\"content\"")) {
                            tFirstContent.compareAndSet(null, now);
                        }
                        contentChars.addAndGet(d.length());
                    });
            long tEnd = System.nanoTime();

            firstContent[i] = ms(t0, tFirstContent.get() != null ? tFirstContent.get() : tEnd);
            total[i] = ms(t0, tEnd);
            System.out.printf("run %d: first_content=%dms  total=%dms  chunksBytes=%d%n",
                    i, firstContent[i], total[i], contentChars.get());
        }

        System.out.println("\n=== summary (ms) ===");
        System.out.printf("first_content(首字): avg=%.0f  min=%d  max=%d%n",
                avg(firstContent), min(firstContent), max(firstContent));
    }

    private static long ms(long t0, long t1) {
        return (t1 - t0) / 1_000_000L;
    }

    private static double avg(long[] a) {
        long s = 0;
        for (long v : a) s += v;
        return (double) s / a.length;
    }

    private static long min(long[] a) {
        long m = Long.MAX_VALUE;
        for (long v : a) if (v > 0 && v < m) m = v;
        return m == Long.MAX_VALUE ? -1 : m;
    }

    private static long max(long[] a) {
        long m = 0;
        for (long v : a) if (v > m) m = v;
        return m;
    }

    /** 返回 [apiKey, baseUrl, model]，全部来自 application-dev.yml / 环境变量，不写死。 */
    private static String[] resolveConfig() throws Exception {
        String env = System.getenv("DEEPSEEK_API_KEY");
        Path yml = Path.of("src", "main", "resources", "application-dev.yml");
        String text = Files.readString(yml, StandardCharsets.UTF_8);

        String key = env;
        String baseUrl = null;
        String model = null;

        if (key == null) {
            key = unwrapPlaceholder(grep(text, "^\\s*api-key:\\s*(\\S+)"));
        }
        baseUrl = grep(text, "^\\s*base-url:\\s*(\\S+)");
        model = grep(text, "^\\s*model:\\s*(\\S+)");

        if (key == null || baseUrl == null || model == null) {
            throw new IllegalStateException(
                    "无法从 application-dev.yml 解析 api-key/base-url/model，或请设置环境变量 DEEPSEEK_API_KEY");
        }
        return new String[]{key, baseUrl, model};
    }

    private static String grep(String text, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 把 ${ENV:default} 占位符展开为 default（环境变量已在前面优先处理） */
    private static String unwrapPlaceholder(String v) {
        if (v == null || !v.startsWith("${")) return v;
        int idx = v.indexOf(':');
        if (idx < 0) return null; // ${ENV} 无默认值，交给环境变量
        return v.substring(idx + 1, v.length() - 1);
    }
}
