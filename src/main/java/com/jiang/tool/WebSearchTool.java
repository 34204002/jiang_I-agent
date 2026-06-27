package com.jiang.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 网页搜索工具 — DuckDuckGo Lite 抓取。
 * 免费、无需 API Key，返回搜索结果标题+摘要+URL。
 */
@Component
public class WebSearchTool {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(name = "search_web",
          description = "联网搜索关键词，返回摘要和URL。训练数据过时或不确定最新信息时必用。拿到搜索结果后可以再用 read_web_page 深入访问具体链接获取详情。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "query": {"type": "string", "description": "搜索关键词（中文自然语言即可，不需要特殊格式）"},
                      "limit": {"type": "integer", "description": "返回结果数，默认5，最大10"}
                  },
                  "required": ["query"]
              }
              """)
    public String searchWeb(String query, Integer limit) {
        int max = Math.min(limit != null ? limit : 5, 10);
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://lite.duckduckgo.com/lite/?q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 Jiang-I-Agent/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "搜索请求失败: HTTP " + resp.statusCode();
            }

            Document doc = Jsoup.parse(resp.body());
            var rows = doc.select("table[cellpadding] tr");
            if (rows.isEmpty()) {
                return "未找到搜索结果，可能是 DuckDuckGo 暂时不可用。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索 \"").append(query).append("\" 的结果：\n\n");
            int count = 0;

            for (Element row : rows) {
                if (count >= max) break;
                // DDG Lite 格式: <tr> 含一个链接 <a> 和一个摘要 <td>
                var linkEl = row.selectFirst("a.result-link");
                var snippetEl = row.selectFirst("td.result-snippet");
                if (linkEl == null) continue;

                String title = linkEl.text().trim();
                String href = linkEl.attr("href");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                if (title.isEmpty()) continue;
                count++;
                sb.append(count).append(". **").append(title).append("**\n");
                sb.append("   ").append(href).append("\n");
                if (!snippet.isEmpty()) sb.append("   ").append(snippet).append("\n");
                sb.append("\n");
            }

            if (count == 0) return "未找到搜索结果。请尝试换个关键词。";
            sb.append("---\n如需深入查看某条结果，用 read_web_page 访问对应 URL。");
            return sb.toString();

        } catch (Exception e) {
            return "搜索失败: " + e.getMessage() + "。请稍后重试或换个关键词。";
        }
    }
}
