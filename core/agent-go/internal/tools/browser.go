package tools

import (
	"fmt"
	"io"
	"net/http"
	"phoneclaw/agent-go/internal/agent"
	"regexp"
	"strings"
	"time"
)

// HTML page fetcher and link extractor using stdlib only
// Extracts title, links, and text content from HTML

type pageData struct {
	URL   string
	Title string
	Body  string
	Links []string
}

var currentPage *pageData
var formInputs = make(map[string]string) // stored form field input

func runBrowser(action, input string, params map[string]any) agent.ToolResult {
	switch action {
	case "open", "navigate":
		return openPage(input)
	case "click":
		return clickLink(input)
	case "extract", "read":
		return extractPage(input)
	case "search":
		return searchText(input)
	case "type":
		formInputs["input"] = input
		return agent.ToolResult{Success: true, Title: "已输入", Output: input, Risk: agent.RiskLow}
	case "scroll":
		return agent.ToolResult{Success: true, Title: "已滚动", Output: input, Risk: agent.RiskLow}
	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskLow}
	}
}

func openPage(url string) agent.ToolResult {
	if !strings.HasPrefix(url, "http") {
		url = "https://" + url
	}
	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return agent.ToolResult{Success: false, Title: "打开失败", Output: err.Error(), Risk: agent.RiskLow}
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 100000))
	if err != nil {
		return agent.ToolResult{Success: false, Title: "读取失败", Output: err.Error(), Risk: agent.RiskLow}
	}

	html := string(body)
	title := extractTitle(html)
	text := extractText(html)
	if len(text) > 16000 {
		text = text[:16000] + "..."
	}
	links := extractLinks(html, url)

	currentPage = &pageData{
		URL: url, Title: title, Body: html,
		Links: links,
	}

	return agent.ToolResult{Success: true, Title: "已打开: " + title,
		Output: fmt.Sprintf("标题: %s\nURL: %s\n链接: %d个\n\n%s",
			title, url, len(links), text),
		Risk: agent.RiskLow}
}

func clickLink(text string) agent.ToolResult {
	if currentPage == nil {
		return agent.ToolResult{Success: false, Title: "没有打开的页面", Risk: agent.RiskLow}
	}
	for _, link := range currentPage.Links {
		if strings.Contains(link, text) {
			return openPage(link)
		}
	}
	// Try finding by text content in links
	re := regexp.MustCompile(`<a[^>]*href="([^"]*)"[^>]*>([^<]*)` + regexp.QuoteMeta(text) + `[^<]*</a>`)
	matches := re.FindStringSubmatch(currentPage.Body)
	if len(matches) >= 2 {
		href := matches[1]
		if !strings.HasPrefix(href, "http") {
			href = resolveURL(currentPage.URL, href)
		}
		return openPage(href)
	}
	return agent.ToolResult{Success: false, Title: "未找到链接", Output: "包含: " + text, Risk: agent.RiskLow}
}

func extractPage(selector string) agent.ToolResult {
	if currentPage == nil {
		return agent.ToolResult{Success: false, Title: "没有打开的页面", Risk: agent.RiskLow}
	}
	if selector == "" {
		text := extractText(currentPage.Body)
		if len(text) > 16000 { text = text[:16000] + "..." }
		return agent.ToolResult{Success: true, Title: "页面内容",
			Output: fmt.Sprintf("标题: %s\n链接: %d\n\n%s", currentPage.Title, len(currentPage.Links), text),
			Risk: agent.RiskLow}
	}
	// Simple selector matching - find elements by tag name
	re := regexp.MustCompile(`<` + regexp.QuoteMeta(selector) + `[^>]*>([^<]*)</` + regexp.QuoteMeta(selector) + `>`)
	matches := re.FindAllStringSubmatch(currentPage.Body, -1)
	var results []string
	for _, m := range matches {
		if len(m) >= 2 {
			results = append(results, strings.TrimSpace(m[1]))
		}
	}
	if len(results) == 0 {
		return agent.ToolResult{Success: false, Title: "未找到", Output: "选择器: " + selector, Risk: agent.RiskLow}
	}
	return agent.ToolResult{Success: true, Title: fmt.Sprintf("找到%d个", len(results)),
		Output: strings.Join(results, "\n"), Risk: agent.RiskLow}
}

func searchText(query string) agent.ToolResult {
	if currentPage == nil {
		return agent.ToolResult{Success: false, Title: "没有打开的页面", Risk: agent.RiskLow}
	}
	text := extractText(currentPage.Body)
	lower := strings.ToLower(text)
	query = strings.ToLower(query)
	idx := strings.Index(lower, query)
	if idx == -1 {
		return agent.ToolResult{Success: false, Title: "未找到", Output: query, Risk: agent.RiskLow}
	}
	start := idx - 100
	if start < 0 { start = 0 }
	end := idx + len(query) + 100
	if end > len(text) { end = len(text) }
	context := "..." + text[start:end] + "..."
	return agent.ToolResult{Success: true, Title: "已找到",
		Output: fmt.Sprintf("位置: %d\n上下文:\n%s", idx, context), Risk: agent.RiskLow}
}

var titleRegex = regexp.MustCompile(`<title[^>]*>([^<]*)</title>`)
var tagRegex = regexp.MustCompile(`<[^>]*>`)
var scriptRegex = regexp.MustCompile(`(?s)<script[^>]*>.*?</script>`)
var styleRegex = regexp.MustCompile(`(?s)<style[^>]*>.*?</style>`)
var linkRegex = regexp.MustCompile(`<a[^>]*href="([^"]*)"[^>]*>`)
var multiSpace = regexp.MustCompile(`\s+`)

func extractTitle(html string) string {
	match := titleRegex.FindStringSubmatch(html)
	if len(match) >= 2 {
		return strings.TrimSpace(match[1])
	}
	return "(无标题)"
}

func extractText(html string) string {
	text := scriptRegex.ReplaceAllString(html, "")
	text = styleRegex.ReplaceAllString(text, "")
	text = tagRegex.ReplaceAllString(text, " ")
	text = multiSpace.ReplaceAllString(text, " ")
	return strings.TrimSpace(text)
}

func extractLinks(html, baseURL string) []string {
	matches := linkRegex.FindAllStringSubmatch(html, -1)
	var links []string
	seen := make(map[string]bool)
	for _, m := range matches {
		if len(m) >= 2 {
			href := strings.TrimSpace(m[1])
			if href == "" || strings.HasPrefix(href, "#") || strings.HasPrefix(href, "javascript:") {
				continue
			}
			if !strings.HasPrefix(href, "http") {
				href = resolveURL(baseURL, href)
			}
			if !seen[href] {
				seen[href] = true
				links = append(links, href)
			}
		}
	}
	if len(links) > 100 { links = links[:100] }
	return links
}

func resolveURL(base, href string) string {
	if strings.HasPrefix(href, "/") {
		parts := strings.SplitN(base, "/", 4)
		if len(parts) >= 3 {
			return parts[0] + "//" + parts[2] + href
		}
	}
	return base + "/" + strings.TrimPrefix(href, "/")
}
