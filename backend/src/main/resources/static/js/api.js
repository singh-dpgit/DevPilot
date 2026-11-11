// DevPilot Fetch API Helper (js/api.js)

class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

async function apiRequest(path, options = {}) {
    const headers = { Accept: "application/json", ...(options.headers || {}) };
    if (options.body && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
    }

    const res = await fetch(CONFIG.API_BASE_URL + path, {
        ...options,
        credentials: "include",
        headers
    });

    if (!res.ok) {
        let msg = res.statusText || "Request failed";
        try {
            const text = await res.text();
            const json = JSON.parse(text);
            msg = json.message || json.error || msg;
        } catch {
            // Keep fallback msg if body wasn't JSON
        }
        throw new ApiError(res.status, msg);
    }

    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

const Api = {
    getLoginUrl: () => apiRequest("/api/auth/login-url"),
    getMe: () => apiRequest("/api/auth/me"),
    logout: () => apiRequest("/api/auth/logout", { method: "POST" }),

    listRepos: (refresh = false) => apiRequest(`/api/repos?refresh=${refresh}`),
    getRepo: (id) => apiRequest(`/api/repos/${id}`),
    getRepoStatus: (id) => apiRequest(`/api/repos/${id}/status`),
    startIndex: (id) => apiRequest(`/api/repos/${id}/index`, { method: "POST" }),

    listSessions: (repoId) => apiRequest(`/api/chat/sessions?repositoryId=${encodeURIComponent(repoId)}`),
    createSession: (repositoryId, title = "New chat") =>
        apiRequest("/api/chat/sessions", {
            method: "POST",
            body: JSON.stringify({ repositoryId, title })
        }),
    getMessages: (sessionId) => apiRequest(`/api/chat/sessions/${sessionId}`),

    // Reads Server-Sent Events (SSE) streamed from POST /api/chat/sessions/{id}/messages
    async streamMessage(sessionId, content, handlers = {}) {
        const res = await fetch(`${CONFIG.API_BASE_URL}/api/chat/sessions/${sessionId}/messages`, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
            body: JSON.stringify({ content }),
            signal: handlers.signal
        });

        if (!res.ok) {
            const errText = await res.text().catch(() => "");
            throw new ApiError(res.status, errText || res.statusText);
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() || "";

            for (const block of events) {
                let eventName = "message";
                const dataLines = [];
                for (const line of block.split(/\r?\n/)) {
                    if (line.startsWith("event:")) eventName = line.slice(6).trim();
                    else if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
                }
                const data = dataLines.join("\n");
                if (!data || eventName === "done") continue;

                try {
                    const parsed = JSON.parse(data);
                    if (eventName === "token") handlers.onToken?.(String(parsed));
                    else if (eventName === "user_message") handlers.onUserMessage?.(parsed);
                    else if (eventName === "assistant_message") handlers.onAssistantMessage?.(parsed);
                } catch {
                    if (eventName === "token") handlers.onToken?.(data);
                }
            }
        }
    }
};
