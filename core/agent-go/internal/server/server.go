package server

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"sync"
)

type Handler func(params json.RawMessage) (any, error)
type Notifier func(clientID string, method string, params any)

type Server struct {
	socketPath string
	listener   net.Listener
	handlers   map[string]Handler
	clients    map[string]net.Conn
	mu         sync.RWMutex
	wg         sync.WaitGroup
	stopCh     chan struct{}
	connIDSeq  int
}

func NewServer(socketPath string) *Server {
	return &Server{
		socketPath: socketPath,
		handlers:   make(map[string]Handler),
		clients:    make(map[string]net.Conn),
		stopCh:     make(chan struct{}),
	}
}

func (s *Server) Handle(method string, handler Handler) {
	s.mu.Lock()
	s.handlers[method] = handler
	s.mu.Unlock()
}

func (s *Server) Notify(method string, params any) {
	data, err := json.Marshal(map[string]any{
		"jsonrpc": "2.0",
		"method":  method,
		"params":  params,
	})
	if err != nil {
		return
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, conn := range s.clients {
		conn.Write(data)
		fmt.Fprintf(conn, "\n")
	}
}

func (s *Server) Start() error {
	os.Remove(s.socketPath)
	listener, err := net.Listen("unix", s.socketPath)
	if err != nil {
		return fmt.Errorf("listen unix: %w", err)
	}
	s.listener = listener
	go s.acceptLoop()
	return nil
}

func (s *Server) Stop() {
	close(s.stopCh)
	if s.listener != nil {
		s.listener.Close()
	}
	s.wg.Wait()
	os.Remove(s.socketPath)
}

func (s *Server) acceptLoop() {
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			select {
			case <-s.stopCh:
				return
			default:
				continue
			}
		}
		s.mu.Lock()
		s.connIDSeq++
		clientID := fmt.Sprintf("c%d", s.connIDSeq)
		s.clients[clientID] = conn
		s.mu.Unlock()
		s.wg.Add(1)
		go s.handleConnection(clientID, conn)
	}
}

type rpcMessage struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id,omitempty"`
	Method  string          `json:"method,omitempty"`
	Params  json.RawMessage `json:"params,omitempty"`
	Result  json.RawMessage `json:"result,omitempty"`
	Error   *rpcError       `json:"error,omitempty"`
}

type rpcError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (s *Server) handleConnection(clientID string, conn net.Conn) {
	defer s.wg.Done()
	defer conn.Close()
	defer func() {
		s.mu.Lock()
		delete(s.clients, clientID)
		s.mu.Unlock()
	}()

	dec := json.NewDecoder(conn)
	enc := json.NewEncoder(conn)

	for {
		var msg rpcMessage
		if err := dec.Decode(&msg); err != nil {
			if err == io.EOF {
				return
			}
			continue
		}

		if msg.ID == nil {
			continue
		}

		s.mu.RLock()
		handler, ok := s.handlers[msg.Method]
		s.mu.RUnlock()

		resp := rpcMessage{JSONRPC: "2.0", ID: msg.ID}
		if !ok {
			resp.Error = &rpcError{Code: -32601, Message: fmt.Sprintf("unknown method: %s", msg.Method)}
		} else {
			result, err := handler(msg.Params)
			if err != nil {
				resp.Error = &rpcError{Code: -32000, Message: err.Error()}
			} else {
				data, _ := json.Marshal(result)
				resp.Result = data
			}
		}
		if err := enc.Encode(resp); err != nil {
			return
		}
	}
}
