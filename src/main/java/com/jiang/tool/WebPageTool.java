package com.jiang.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 网页抓取工具 — 让 Agent 读取网页内容。
 */
@Slf4j
@Component
public class WebPageTool {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(name = "read_web_page",
          description = "读取网页内容并提取纯文本。用户没给完整URL时，你应该根据上下文主动拼接构造URL去尝试访问，不要直接说「没有链接」。常见URL模式：B站用户空间 https://space.bilibili.com/{uid}、GitHub主页 https://github.com/{user}、API https://api.{domain}/...。失败了换格式重试，返回404/403时如实告知用户。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "url": {"type": "string", "description": "要读取的网页 URL。需完整 https:// 开头。如果不知道确切URL，根据用户意图和常见模式自行构造——成功就赚了，失败再告诉用户"}
                  },
                  "required": ["url"]
              }
              """)
    public String readWebPage(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 Jiang-I-Agent/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "网页请求失败: HTTP " + resp.statusCode() + " — 请检查 URL 是否正确: " + url;
            }

            String html = resp.body();
            String text = stripHtml(html);
            String trimmed = text.length() > 3000 ? text.substring(0, 3000) + "\n...(内容过长，已截断)" : text;

            log.info("网页抓取完成: url={}, 提取 {} 字符", url, trimmed.length());
            return "网页内容 (来自 " + url + "):\n\n" + trimmed;

        } catch (Exception e) {
            log.error("网页抓取失败: url={}", url, e);
            return "无法读取网页: " + e.getMessage() + " — 请检查 URL 是否能正常访问";
        }
    }

    /** 简单 HTML → 纯文本 */
    private String stripHtml(String html) {
        return html
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#\\d+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
