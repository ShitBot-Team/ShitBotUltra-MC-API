// Package bridge is the versioned Minecraft wire contract, independent of any
// Minecraft platform, transport implementation or Core internal package.
package bridge

import (
	"encoding/json"
	"time"
)

const Version = 1
const MaxSequence = uint64(1<<53 - 1)

type Envelope struct {
	ProtocolVersion int             `json:"protocol_version"`
	Kind            string          `json:"kind"`
	Type            string          `json:"type"`
	MessageID       string          `json:"message_id"`
	RequestID       string          `json:"request_id,omitempty"`
	NodeID          string          `json:"node_id"`
	BootID          string          `json:"boot_id"`
	Sequence        uint64          `json:"sequence"`
	OccurredAt      time.Time       `json:"occurred_at"`
	Deadline        *time.Time      `json:"deadline,omitempty"`
	TimeoutMS       int64           `json:"timeout_ms,omitempty"`
	IdempotencyKey  string          `json:"idempotency_key,omitempty"`
	Payload         json.RawMessage `json:"payload,omitempty"`
	Error           *Error          `json:"error,omitempty"`
}

type Error struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func (e *Error) Error() string { return e.Code + ": " + e.Message }

type Hello struct {
	ProtocolVersion  int      `json:"protocol_version"`
	AdapterVersion   string   `json:"adapter_version"`
	NodeID           string   `json:"node_id"`
	NetworkID        string   `json:"network_id"`
	ServerID         string   `json:"server_id"`
	Platform         string   `json:"platform"`
	MinecraftVersion string   `json:"minecraft_version"`
	BootID           string   `json:"boot_id"`
	Capabilities     []string `json:"capabilities"`
}

type Welcome struct {
	ProtocolVersion  int       `json:"protocol_version"`
	SessionID        string    `json:"session_id"`
	ServerTime       time.Time `json:"server_time"`
	HeartbeatSeconds int       `json:"heartbeat_seconds"`
	MaxMessageBytes  int       `json:"max_message_bytes"`
	ResumeSequence   uint64    `json:"resume_sequence"`
	SnapshotRequired bool      `json:"snapshot_required"`
}

type Player struct {
	Name         string `json:"name"`
	UUID         string `json:"uuid,omitempty"`
	IdentityMode string `json:"identity_mode"` // online, forwarded, offline
}

type LoginRequest struct {
	Player Player `json:"player"`
}

type LoginDecision struct {
	Allowed   bool       `json:"allowed"`
	Reason    string     `json:"reason,omitempty"`
	Code      string     `json:"code,omitempty"`
	ExpiresAt *time.Time `json:"expires_at,omitempty"`
}

type Chat struct {
	Player   Player `json:"player"`
	Text     string `json:"text"`
	OriginID string `json:"origin_id,omitempty"`
}

type PlayerEvent struct {
	Player Player `json:"player"`
}
type OnlineSnapshot struct {
	Players []Player `json:"players"`
}

type InventorySnapshot struct {
	Player        Player          `json:"player"`
	CapturedAt    time.Time       `json:"captured_at"`
	FormatVersion int             `json:"format_version"`
	Data          json.RawMessage `json:"data"`
}

type Broadcast struct {
	Text     string `json:"text"`
	OriginID string `json:"origin_id"`
}

type Kick struct {
	Player Player `json:"player"`
	Reason string `json:"reason"`
}

// Command is a configured game-console command, never a system shell command.
type Command struct {
	Command string `json:"command"`
	ActorID string `json:"actor_id"`
}

// ServerPerformance contains metrics sampled by the Minecraft platform adapter.
// TPS is Minecraft ticks per second, never Core throughput or request rate.
// A nil TPS means the game platform cannot provide a TPS measurement.
type ServerPerformance struct {
	MeasuredAt      time.Time `json:"measured_at"`
	TPS             *float64  `json:"tps"`
	TPS5Minutes     *float64  `json:"tps_5m,omitempty"`
	TPS15Minutes    *float64  `json:"tps_15m,omitempty"`
	MSPT            *float64  `json:"mspt,omitempty"`
	OnlinePlayers   int       `json:"online_players"`
	MaxPlayers      int       `json:"max_players"`
	UsedMemoryBytes int64     `json:"used_memory_bytes,omitempty"`
	MaxMemoryBytes  int64     `json:"max_memory_bytes,omitempty"`
}
