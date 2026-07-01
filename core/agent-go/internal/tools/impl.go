package tools

import (
	"context"
	"fmt"
	"io"
	"math"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"phoneclaw/agent-go/internal/agent"
)

func runFilesystem(action, input string, params map[string]any) agent.ToolResult {
	clean := filepath.Clean(input)
	if strings.Contains(clean, "..") { return agent.ToolResult{Success: false, Title: "拒绝路径穿越", Output: clean, Risk: agent.RiskMedium} }
	switch action {
	case "list":
		entries, err := os.ReadDir(clean)
		if err != nil { return toolErr("列出失败", err, agent.RiskMedium) }
		var lines []string
		for _, e := range entries { s := e.Name(); if e.IsDir() { s += "/" }; lines = append(lines, s) }
		return agent.ToolResult{Success: true, Title: "目录列表", Output: limit(strings.Join(lines, "\n"), 32000), Risk: agent.RiskMedium}
	case "read":
		data, err := os.ReadFile(clean)
		if err != nil { return toolErr("读取失败", err, agent.RiskMedium) }
		return agent.ToolResult{Success: true, Title: "文件内容", Output: limit(string(data), 32000), Risk: agent.RiskMedium}
	case "write":
		c := ""; if params != nil { if v, ok := params["content"]; ok { c, _ = v.(string) } }
		if err := os.MkdirAll(filepath.Dir(clean), 0755); err != nil { return toolErr("创建目录失败", err, agent.RiskMedium) }
		if err := os.WriteFile(clean, []byte(c), 0644); err != nil { return toolErr("写入失败", err, agent.RiskMedium) }
		return agent.ToolResult{Success: true, Title: "写入成功", Output: fmt.Sprintf("已写入 %d 字节", len(c)), Risk: agent.RiskMedium}
	case "delete":
		if err := os.RemoveAll(clean); err != nil { return toolErr("删除失败", err, agent.RiskMedium) }
		return agent.ToolResult{Success: true, Title: "删除成功", Output: clean, Risk: agent.RiskMedium}
	case "copy":
		dst, _ := params["dest"].(string)
		if dst == "" { return agent.ToolResult{Success: false, Title: "需要目标路径", Output: "请指定 dest 参数", Risk: agent.RiskMedium} }
		d, err := os.ReadFile(clean)
		if err != nil { return toolErr("复制失败", err, agent.RiskMedium) }
		if err := os.WriteFile(filepath.Clean(dst), d, 0644); err != nil { return toolErr("复制失败", err, agent.RiskMedium) }
		return agent.ToolResult{Success: true, Title: "复制成功", Output: fmt.Sprintf("%s -> %s", clean, dst), Risk: agent.RiskMedium}
	case "move":
		dst, _ := params["dest"].(string)
		if dst == "" { return agent.ToolResult{Success: false, Title: "需要目标路径", Output: "请指定 dest 参数", Risk: agent.RiskMedium} }
		if err := os.Rename(clean, filepath.Clean(dst)); err != nil { return toolErr("移动失败", err, agent.RiskMedium) }
		return agent.ToolResult{Success: true, Title: "移动成功", Output: fmt.Sprintf("%s -> %s", clean, dst), Risk: agent.RiskMedium}
	case "search":
		p := input; if p == "" { p = "*" }
		matches, err := filepath.Glob(p)
		if err != nil { return toolErr("搜索失败", err, agent.RiskMedium) }
		if len(matches) == 0 { return agent.ToolResult{Success: true, Title: "搜索完成", Output: "未找到", Risk: agent.RiskMedium} }
		return agent.ToolResult{Success: true, Title: "搜索完成", Output: limit(strings.Join(matches, "\n"), 32000), Risk: agent.RiskMedium}
	case "info":
		info, err := os.Stat(clean)
		if err != nil { return toolErr("获取信息失败", err, agent.RiskMedium) }
		return agent.ToolResult{Success: true, Title: "文件信息", Output: fmt.Sprintf("名称: %s\n大小: %d\n目录: %v\n权限: %s\n修改时间: %s", info.Name(), info.Size(), info.IsDir(), info.Mode(), info.ModTime()), Risk: agent.RiskMedium}
	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskMedium}
	}
}

func runShell(action, input string, params map[string]any) agent.ToolResult {
	if input == "" { return agent.ToolResult{Success: false, Title: "空命令", Risk: agent.RiskHigh} }
	t := 30 * time.Second
	if params != nil { if v, ok := params["timeout"]; ok { if sec, ok := v.(float64); ok && sec > 0 { t = time.Duration(sec) * time.Second } } }
	ctx, cancel := context.WithTimeout(context.Background(), t)
	defer cancel()
	o, err := exec.CommandContext(ctx, "sh", "-c", input).CombinedOutput()
	if ctx.Err() == context.DeadlineExceeded { return agent.ToolResult{Success: false, Title: "命令超时", Output: limit(string(o), 16000), Risk: agent.RiskHigh} }
	if err != nil { return agent.ToolResult{Success: false, Title: "命令失败", Output: limit(string(o)+"\n"+err.Error(), 16000), Risk: agent.RiskHigh} }
	return agent.ToolResult{Success: true, Title: "命令输出", Output: limit(string(o), 64000), Risk: agent.RiskHigh}
}

func runNetwork(action, input string, params map[string]any) agent.ToolResult {
	c := &http.Client{Timeout: 15 * time.Second}
	switch action {
	case "get", "download", "head":
		if !strings.HasPrefix(input, "http") { input = "https://" + input }
		r, err := c.Get(input)
		if err != nil { return toolErr("请求失败", err, agent.RiskLow) }
		defer r.Body.Close()
		b, err := io.ReadAll(io.LimitReader(r.Body, 50000))
		if err != nil { return toolErr("读取响应失败", err, agent.RiskLow) }
		return agent.ToolResult{Success: true, Title: fmt.Sprintf("HTTP %d", r.StatusCode), Output: limit(string(b), 32000), Risk: agent.RiskLow}
	case "post":
		bs := ""; if params != nil { if v, ok := params["body"]; ok { bs, _ = v.(string) } }
		r, err := c.Post(input, "application/json", strings.NewReader(bs))
		if err != nil { return toolErr("POST失败", err, agent.RiskLow) }
		defer r.Body.Close()
		b, _ := io.ReadAll(io.LimitReader(r.Body, 50000))
		return agent.ToolResult{Success: true, Title: fmt.Sprintf("HTTP %d", r.StatusCode), Output: limit(string(b), 32000), Risk: agent.RiskLow}
	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskLow}
	}
}

func runFFmpeg(action, input string, params map[string]any) agent.ToolResult {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		return agent.ToolResult{Success: false, Title: "ffmpeg 未安装", Output: "请安装 ffmpeg", Risk: agent.RiskLow}
	}
	return runFFmpegCmd(action, input, params)
}

func runFFmpegCmd(action, input string, params map[string]any) agent.ToolResult {
	switch action {
	case "info":
		o, _ := exec.Command("ffmpeg", "-i", input, "-hide_banner").CombinedOutput()
		return agent.ToolResult{Success: true, Title: "媒体信息", Output: limit(string(o), 32000), Risk: agent.RiskLow}
	case "convert":
		f := "mp4"; if p, ok := params["format"]; ok { f, _ = p.(string) }
		o := "output." + f; if p, ok := params["output"]; ok { o, _ = p.(string) }
		if _, err := exec.Command("ffmpeg", "-i", input, "-preset", "fast", o, "-y", "-hide_banner").CombinedOutput(); err != nil {
			return agent.ToolResult{Success: false, Title: "转换失败", Output: err.Error(), Risk: agent.RiskLow}
		}
		return agent.ToolResult{Success: true, Title: "转换完成", Output: input + " -> " + o, Risk: agent.RiskLow}
	case "compress":
		crf := "28"; if p, ok := params["crf"]; ok { crf, _ = p.(string) }
		o := "compressed_" + filepath.Base(input); if p, ok := params["output"]; ok { o, _ = p.(string) }
		if _, err := exec.Command("ffmpeg", "-i", input, "-crf", crf, "-preset", "medium", o, "-y", "-hide_banner").CombinedOutput(); err != nil {
			return agent.ToolResult{Success: false, Title: "压缩失败", Output: err.Error(), Risk: agent.RiskLow}
		}
		return agent.ToolResult{Success: true, Title: "压缩完成", Output: input + " -> " + o, Risk: agent.RiskLow}
	case "extract_audio":
		ext := filepath.Ext(input)
		o := strings.TrimSuffix(filepath.Base(input), ext) + ".mp3"
		if p, ok := params["output"]; ok { o, _ = p.(string) }
		if _, err := exec.Command("ffmpeg", "-i", input, "-vn", "-acodec", "libmp3lame", o, "-y", "-hide_banner").CombinedOutput(); err != nil {
			return agent.ToolResult{Success: false, Title: "提取失败", Output: err.Error(), Risk: agent.RiskLow}
		}
		return agent.ToolResult{Success: true, Title: "音频已提取", Output: o, Risk: agent.RiskLow}
	case "resize":
		s := "1280:720"; if p, ok := params["scale"]; ok { s, _ = p.(string) }
		o := "resized_" + filepath.Base(input); if p, ok := params["output"]; ok { o, _ = p.(string) }
		if _, err := exec.Command("ffmpeg", "-i", input, "-vf", "scale="+s, o, "-y", "-hide_banner").CombinedOutput(); err != nil {
			return agent.ToolResult{Success: false, Title: "缩放失败", Output: err.Error(), Risk: agent.RiskLow}
		}
		return agent.ToolResult{Success: true, Title: "缩放完成", Output: input + " -> " + o, Risk: agent.RiskLow}
	case "trim":
		start := "00:00:00"; if p, ok := params["start"]; ok { start, _ = p.(string) }
		dur := "10"; if p, ok := params["duration"]; ok { dur, _ = p.(string) }
		o := "trimmed_" + filepath.Base(input); if p, ok := params["output"]; ok { o, _ = p.(string) }
		if _, err := exec.Command("ffmpeg", "-i", input, "-ss", start, "-t", dur, "-c", "copy", o, "-y", "-hide_banner").CombinedOutput(); err != nil {
			return agent.ToolResult{Success: false, Title: "裁剪失败", Output: err.Error(), Risk: agent.RiskLow}
		}
		return agent.ToolResult{Success: true, Title: "裁剪完成", Output: input + " " + start + " " + dur + "s -> " + o, Risk: agent.RiskLow}
	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskLow}
	}
}

func runCalc(action, input string, params map[string]any) agent.ToolResult {
	if action != "eval" { return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskLow} }
	r, err := evalMath(input)
	if err != nil { return agent.ToolResult{Success: false, Title: "计算错误", Output: err.Error(), Risk: agent.RiskLow} }
	return agent.ToolResult{Success: true, Title: "计算结果", Output: fmt.Sprintf("%v = %g", input, r), Risk: agent.RiskLow}
}

func runDevice(action, input string, params map[string]any) agent.ToolResult {
	switch action {
	case "", "summary":
		return agent.ToolResult{Success: true, Title: "设备摘要", Output: fmt.Sprintf("系统: android/arm64\nCPU: %d 核\nGo: %s\nGoroutines: %d", runtime.NumCPU(), runtime.Version(), runtime.NumGoroutine()), Risk: agent.RiskLow}
	case "env":
		keys := []string{"HOME", "PATH", "TMPDIR", "ANDROID_DATA", "ANDROID_ROOT"}
		var l []string
		for _, k := range keys { v := os.Getenv(k); if v == "" { v = "(空)" }; l = append(l, k+"="+v) }
		return agent.ToolResult{Success: true, Title: "环境变量", Output: strings.Join(l, "\n"), Risk: agent.RiskLow}
	case "cpu":
		return agent.ToolResult{Success: true, Title: "CPU", Output: fmt.Sprintf("逻辑 CPU 数: %d\nGOMAXPROCS: %d", runtime.NumCPU(), runtime.GOMAXPROCS(0)), Risk: agent.RiskLow}
	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskLow}
	}
}

func evalMath(expr string) (float64, error) {
	expr = strings.TrimSpace(expr)
	p := &mathParser{input: expr}
	r := p.parseExpr()
	if p.err != nil { return 0, p.err }
	return r, nil
}

type mathParser struct{ input string; pos int; err error }
func (p *mathParser) peek() byte { if p.pos >= len(p.input) { return 0 }; return p.input[p.pos] }
func (p *mathParser) next() byte { if p.pos >= len(p.input) { return 0 }; c := p.input[p.pos]; p.pos++; return c }
func (p *mathParser) skipSpace() { for p.pos < len(p.input) && p.input[p.pos] == ' ' { p.pos++ } }

func (p *mathParser) parseExpr() float64 {
	p.skipSpace(); r := p.parseTerm()
	for { p.skipSpace(); op := p.peek(); if op == '+' { p.next(); r += p.parseTerm(); continue }; if op == '-' { p.next(); r -= p.parseTerm(); continue }; break }
	return r
}

func (p *mathParser) parseTerm() float64 {
	p.skipSpace(); r := p.parseFactor()
	for { p.skipSpace(); op := p.peek(); if op == '*' { p.next(); r *= p.parseFactor(); continue }; if op == '/' { p.next(); d := p.parseFactor(); if d == 0 { p.err = fmt.Errorf("除以零"); return 0 }; r /= d; continue }; break }
	return r
}

func (p *mathParser) parseFactor() float64 {
	p.skipSpace(); c := p.peek()
	if c == '(' { p.next(); r := p.parseExpr(); p.skipSpace(); if p.peek() == ')' { p.next() }; return r }
	if (c >= '0' && c <= '9') || c == '.' || c == '-' {
		start := p.pos; if c == '-' { p.next(); p.skipSpace() }
		for p.pos < len(p.input) && ((p.input[p.pos] >= '0' && p.input[p.pos] <= '9') || p.input[p.pos] == '.') { p.pos++ }
		v, err := strconv.ParseFloat(p.input[start:p.pos], 64)
		if err != nil { p.err = err; return 0 }; return v
	}
	funcs := map[string]func(float64) float64{"sin": math.Sin, "cos": math.Cos, "tan": math.Tan, "sqrt": math.Sqrt, "log": math.Log, "ceil": math.Ceil, "floor": math.Floor, "abs": func(x float64) float64 { if x < 0 { return -x }; return x }}
	for name, fn := range funcs {
		if strings.HasPrefix(p.input[p.pos:], name) && len(p.input) > p.pos+len(name) && p.input[p.pos+len(name)] == '(' {
			p.pos += len(name); p.skipSpace(); if p.peek() == '(' { p.next() }; arg := p.parseExpr(); p.skipSpace(); if p.peek() == ')' { p.next() }; return fn(arg)
		}
	}
	p.err = fmt.Errorf("不支持的字符: %c", c); return 0
}

func limit(s string, max int) string { if len(s) <= max { return s }; return s[:max] + "\n...[截断]" }
func toolErr(title string, err error, risk agent.RiskLevel) agent.ToolResult { return agent.ToolResult{Success: false, Title: title, Output: err.Error(), Risk: risk} }
