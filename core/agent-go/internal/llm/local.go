package llm

import "fmt"

type LocalProvider struct {
	name string
}

func NewLocalProvider(name string) *LocalProvider {
	return &LocalProvider{name: name}
}

func (p *LocalProvider) Name() string { return p.name }

func (p *LocalProvider) Complete(req ChatRequest) (*ChatResponse, error) {
	// Local model inference is handled by Android's JNI (LocalModelBridge).
	// This provider exists so the router can route "llama/" and "mnn/" models.
	// The actual inference call happens in PhoneClawViewModel.sendChat()
	// which detects local models and calls LocalModelBridge.generateAsync().
	return nil, fmt.Errorf("本地模型(%s)通过 Android JNI 推理，请使用本地模型接口", p.name)
}

func (p *LocalProvider) Stream(req ChatRequest) (<-chan StreamEvent, error) {
	return nil, fmt.Errorf("本地模型(%s)不支持流式", p.name)
}
