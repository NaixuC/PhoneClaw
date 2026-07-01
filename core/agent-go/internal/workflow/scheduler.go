package workflow

import (
	"log"
	"math/rand"
	"phoneclaw/agent-go/internal/agent"
	"phoneclaw/agent-go/internal/storage"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Scheduler struct {
	store       *storage.Store
	engine      *Engine
	toolRunner  agent.ToolRunner
	jobs        map[string]*cronJob
	mu          sync.Mutex
	stopCh      chan struct{}
}

type cronJob struct {
	ID       string
	Workflow Workflow
	Expr     string
	NextRun  time.Time
	ticker   *time.Ticker
}

func NewScheduler(s *storage.Store, e *Engine, tr agent.ToolRunner) *Scheduler {
	return &Scheduler{
		store: s, engine: e, toolRunner: tr,
		jobs: make(map[string]*cronJob),
		stopCh: make(chan struct{}),
	}
}

func (s *Scheduler) Start() {
	s.mu.Lock()
	defer s.mu.Unlock()

	// Load workflows with schedules
	wfs, _ := s.store.List("workflows")
	for _, id := range wfs {
		var wf Workflow
		if s.store.Read("workflows", id, &wf) != nil { continue }
		if wf.Enabled && wf.Schedule != "" {
			s.scheduleWorkflow(wf)
		}
	}

	go s.runLoop()
	log.Printf("workflow scheduler started with %d jobs", len(s.jobs))
}

func (s *Scheduler) Stop() {
	close(s.stopCh)
}

func (s *Scheduler) scheduleWorkflow(wf Workflow) {
	next := parseCronNext(wf.Schedule)
	if next.IsZero() { return }

	s.jobs[wf.ID] = &cronJob{
		ID: wf.ID, Workflow: wf,
		Expr: wf.Schedule, NextRun: next,
	}
}

func (s *Scheduler) runLoop() {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-s.stopCh:
			return
		case <-ticker.C:
			s.checkJobs()
		}
	}
}

func (s *Scheduler) checkJobs() {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	for id, job := range s.jobs {
		if now.After(job.NextRun) {
			go s.executeJob(id, job)
			next := parseCronNext(job.Expr)
			if !next.IsZero() { job.NextRun = next }
		}
	}
}

func (s *Scheduler) executeJob(id string, job *cronJob) {
	log.Printf("executing scheduled workflow: %s", job.Workflow.Name)
	params := make(map[string]any)
	for _, step := range job.Workflow.Steps {
		s.toolRunner.Execute(agent.ToolRequest{
			Tool: step.Tool, Action: step.Action,
			Input: step.Input, Params: params,
		})
	}
}

// Standard cron expression parser
// Format: "minute hour day month weekday"
// Supports: *, */N, N, N-M, N,M,O
func parseCronNext(expr string) time.Time {
	parts := strings.Fields(expr)
	if len(parts) != 5 { return time.Time{} }

	now := time.Now()
	minutes := parseCronSet(parts[0], 0, 59)
	hours := parseCronSet(parts[1], 0, 23)
	days := parseCronSet(parts[2], 1, 31)
	months := parseCronSet(parts[3], 1, 12)
	weekdays := parseCronSet(parts[4], 0, 6)

	// Search for next match within next 366 days
	for d := 0; d <= 366; d++ {
		t := now.AddDate(0, 0, d)
		if !containsInt(months, int(t.Month())) { continue }
		if !containsInt(weekdays, int(t.Weekday())) { continue }
		if !containsInt(days, t.Day()) { continue }
		if d == 0 {
			// Same day: find next matching hour:minute
			for _, h := range hours {
				if h < t.Hour() { continue }
				for _, m := range minutes {
					if h == t.Hour() && m <= t.Minute() { continue }
					return time.Date(t.Year(), t.Month(), t.Day(), h, m, 0, 0, t.Location())
				}
			}
		} else {
			// Future day: use first matching hour:minute
			if len(hours) > 0 && len(minutes) > 0 {
				return time.Date(t.Year(), t.Month(), t.Day(), hours[0], minutes[0], 0, 0, t.Location())
			}
		}
	}
	return time.Time{}
}

func parseCronSet(part string, min, max int) []int {
	if part == "*" { return rangeInt(min, max) }
	if strings.HasPrefix(part, "*/") {
		step, err := strconv.Atoi(part[2:])
		if err != nil || step <= 0 { return []int{min} }
		var result []int
		for i := min; i <= max; i += step { result = append(result, i) }
		return result
	}
	if strings.Contains(part, ",") {
		var result []int
		for _, p := range strings.Split(part, ",") {
			vals := parseCronSingle(p, min, max)
			result = append(result, vals...)
		}
		return result
	}
	return parseCronSingle(part, min, max)
}

func parseCronSingle(part string, min, max int) []int {
	if strings.Contains(part, "-") {
		parts := strings.SplitN(part, "-", 2)
		start, err1 := strconv.Atoi(parts[0])
		end, err2 := strconv.Atoi(parts[1])
		if err1 == nil && err2 == nil && start >= min && end <= max {
			return rangeInt(start, end)
		}
	}
	val, err := strconv.Atoi(part)
	if err != nil || val < min || val > max { return []int{min} }
	return []int{val}
}

func rangeInt(min, max int) []int {
	var r []int
	for i := min; i <= max; i++ { r = append(r, i) }
	return r
}

func containsInt(slice []int, val int) bool {
	for _, s := range slice { if s == val { return true } }
	return false
}

func init() {
	rand.Seed(time.Now().UnixNano())
}
