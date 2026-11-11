// DevPilot Main Application Logic (js/app.js)

const state = {
    repos: [],
    currentRepo: null,
    sessions: [],
    currentSessionId: null,
    messages: [],
    streaming: false,
    abortController: null,
    pollTimer: null
};

// Shorthand helper to get an element by ID
const el = (id) => document.getElementById(id);

function showView(viewId) {
    ["loading-view", "login-view", "dashboard-view", "chat-view"].forEach((id) => {
        el(id).classList.toggle("hidden", id !== viewId);
    });
}

function showAlert(message) {
    el("alert-text").textContent = message;
    el("alert-banner").classList.remove("hidden");
}

function stopPolling() {
    if (state.pollTimer) {
        clearInterval(state.pollTimer);
        state.pollTimer = null;
    }
}

// ============================================================================
// 1. Authentication & User Profile
// ============================================================================

function showLogin(errorMsg = "") {
    stopPolling();
    el("user-nav").classList.add("hidden");
    el("login-error").textContent = errorMsg;
    el("login-error").classList.toggle("hidden", !errorMsg);
    showView("login-view");
}

function renderUserHeader(user) {
    el("user-name").textContent = `${user.displayName || user.githubUsername} (@${user.githubUsername})`;
    if (user.avatarUrl) {
        el("user-avatar").src = user.avatarUrl;
        el("user-avatar").classList.remove("hidden");
    }
    el("user-nav").classList.remove("hidden");
}

// ============================================================================
// 2. Repository Dashboard
// ============================================================================

async function loadDashboard(forceSync = false) {
    stopPolling();
    state.currentRepo = null;
    showView("dashboard-view");

    el("sync-btn").disabled = true;
    el("sync-btn").textContent = forceSync ? "Syncing..." : "Sync from GitHub";
    el("repos-state").classList.remove("hidden");
    el("repos-state-text").textContent = forceSync ? "Syncing repositories from GitHub..." : "Loading repositories...";
    el("retry-repos-btn").classList.add("hidden");
    el("repos-grid").innerHTML = "";

    try {
        let repos = await Api.listRepos(forceSync);
        // On very first login, if DB is empty, automatically sync from GitHub once
        if (!forceSync && repos.length === 0) {
            el("repos-state-text").textContent = "Fetching your repositories from GitHub...";
            repos = await Api.listRepos(true);
        }
        state.repos = repos || [];
        el("repos-state").classList.add("hidden");
        renderRepos();
    } catch (err) {
        if (err.status === 401) return showLogin("Session expired. Please sign in again.");
        el("repos-state-text").textContent = `Could not load repositories: ${err.message}`;
        el("retry-repos-btn").classList.remove("hidden");
    } finally {
        el("sync-btn").disabled = false;
        el("sync-btn").textContent = "Sync from GitHub";
    }
}

function renderRepos() {
    const query = el("search-input").value.trim().toLowerCase();
    const statusFilter = el("status-filter").value;

    const readyCount = state.repos.filter((r) => r.indexStatus === "READY").length;
    el("repo-stats").textContent = `${state.repos.length} connected · ${readyCount} ready to chat`;

    const filtered = state.repos.filter((r) => {
        if (statusFilter !== "ALL" && r.indexStatus !== statusFilter) return false;
        if (!query) return true;
        return (
            r.fullName.toLowerCase().includes(query) ||
            (r.description || "").toLowerCase().includes(query) ||
            (r.language || "").toLowerCase().includes(query)
        );
    });

    const grid = el("repos-grid");
    grid.innerHTML = "";

    if (filtered.length === 0) {
        el("repos-state").classList.remove("hidden");
        el("repos-state-text").textContent = "No repositories match your filter.";
        return;
    }
    el("repos-state").classList.add("hidden");

    filtered.forEach((repo) => {
        const card = document.createElement("article");
        card.className = "card repo-card";

        const top = document.createElement("div");
        top.className = "repo-card-top";
        const title = document.createElement("strong");
        title.textContent = repo.fullName;
        const badge = document.createElement("span");
        badge.className = `badge badge-${repo.indexStatus}`;
        badge.textContent = repo.indexStatus;
        top.append(title, badge);

        const desc = document.createElement("p");
        desc.className = "muted small";
        desc.textContent = repo.description || "No description provided.";

        const meta = document.createElement("p");
        meta.className = "muted small";
        meta.textContent = [
            repo.isPrivate ? "Private" : "Public",
            `Branch: ${repo.defaultBranch}`,
            repo.language,
            repo.chunkCount > 0 ? `${repo.chunkCount} chunks` : null
        ].filter(Boolean).join(" · ");

        const footer = document.createElement("div");
        footer.className = "repo-card-footer";

        const ghLink = document.createElement("a");
        ghLink.href = repo.htmlUrl || "#";
        ghLink.target = "_blank";
        ghLink.rel = "noopener noreferrer";
        ghLink.className = "btn btn-outline btn-sm";
        ghLink.textContent = "GitHub ↗";

        const actionBtn = document.createElement("button");
        actionBtn.className = "btn btn-primary btn-sm";
        if (repo.indexStatus === "READY") {
            actionBtn.textContent = "Open Chat →";
            actionBtn.onclick = () => openRepo(repo.id);
        } else if (repo.indexStatus === "INDEXING") {
            actionBtn.textContent = "View Progress";
            actionBtn.onclick = () => openRepo(repo.id);
        } else {
            actionBtn.textContent = repo.indexStatus === "FAILED" ? "Retry Index" : "Index Repo";
            actionBtn.onclick = () => triggerIndexing(repo.id);
        }

        footer.append(ghLink, actionBtn);
        card.append(top, desc, meta, footer);
        grid.appendChild(card);
    });
}

// ============================================================================
// 3. Repository Indexing & Chat Workspace
// ============================================================================

async function openRepo(repoId) {
    stopPolling();
    showView("chat-view");
    el("chat-error").classList.add("hidden");

    try {
        const repo = await Api.getRepo(repoId);
        state.currentRepo = repo;
        renderRepoView(repo);
    } catch (err) {
        showAlert(`Failed to open repository: ${err.message}`);
        loadDashboard(false);
    }
}

async function triggerIndexing(repoId) {
    try {
        const updated = await Api.startIndex(repoId);
        state.repos = state.repos.map((r) => (r.id === updated.id ? updated : r));
        state.currentRepo = updated;
        showView("chat-view");
        renderRepoView(updated);
    } catch (err) {
        showAlert(`Could not start indexing: ${err.message}`);
    }
}

async function renderRepoView(repo) {
    el("chat-repo-title").textContent = repo.fullName;
    el("chat-repo-badge").className = `badge badge-${repo.indexStatus}`;
    el("chat-repo-badge").textContent = repo.indexStatus;
    el("chat-repo-meta").textContent = `Branch: ${repo.defaultBranch} · ${repo.chunkCount || 0} chunks indexed`;
    el("chat-github-link").href = repo.htmlUrl || "#";
    el("reindex-btn").disabled = repo.indexStatus === "INDEXING";

    if (repo.indexStatus === "READY") {
        el("indexing-panel").classList.add("hidden");
        el("chat-workspace").classList.remove("hidden");
        await loadSessions(repo.id);
        return;
    }

    // Show Indexing / Pending / Failed panel
    el("chat-workspace").classList.add("hidden");
    el("indexing-panel").classList.remove("hidden");
    el("indexing-error").classList.toggle("hidden", !repo.errorMessage);
    el("indexing-error").textContent = repo.errorMessage || "";

    if (repo.indexStatus === "INDEXING") {
        el("indexing-title").textContent = `Indexing ${repo.fullName}...`;
        const pct = repo.filesTotal > 0 ? Math.round((repo.filesProcessed / repo.filesTotal) * 100) : 10;
        el("indexing-desc").textContent = `${repo.filesProcessed} of ${repo.filesTotal || "?"} files processed (${repo.chunkCount} chunks)`;
        el("progress-wrap").classList.remove("hidden");
        el("progress-bar").style.width = `${pct}%`;
        el("progress-text").textContent = `${pct}%`;
        el("start-index-btn").disabled = true;
        el("start-index-btn").textContent = "Indexing...";
        startStatusPolling(repo.id);
    } else {
        el("indexing-title").textContent = repo.indexStatus === "FAILED" ? "Indexing Failed" : "Repository Not Indexed";
        el("indexing-desc").textContent = "Index this repository into PostgreSQL + pgvector to unlock AI chat.";
        el("progress-wrap").classList.add("hidden");
        el("start-index-btn").disabled = false;
        el("start-index-btn").textContent = repo.indexStatus === "FAILED" ? "Retry Indexing" : "Start Indexing";
    }
}

function startStatusPolling(repoId) {
    stopPolling();
    state.pollTimer = setInterval(async () => {
        try {
            const st = await Api.getRepoStatus(repoId);
            Object.assign(state.currentRepo, st);
            state.repos = state.repos.map((r) => (r.id === repoId ? state.currentRepo : r));
            if (st.indexStatus !== "INDEXING") {
                stopPolling();
            }
            renderRepoView(state.currentRepo);
        } catch {
            stopPolling();
        }
    }, CONFIG.POLL_INTERVAL_MS);
}

// ============================================================================
// 4. Chat Sessions & SSE Streaming Messages
// ============================================================================

async function loadSessions(repoId) {
    let sessions = await Api.listSessions(repoId);
    if (sessions.length === 0) {
        const created = await Api.createSession(repoId, "New chat");
        sessions = [created];
    }
    state.sessions = sessions;
    await selectSession(sessions[0].id);
}

async function selectSession(sessionId) {
    state.currentSessionId = sessionId;
    renderSessionList();
    state.messages = await Api.getMessages(sessionId);
    renderMessages();
}

function renderSessionList() {
    const list = el("session-list");
    list.innerHTML = "";
    state.sessions.forEach((s) => {
        const li = document.createElement("li");
        const btn = document.createElement("button");
        btn.className = `session-btn ${s.id === state.currentSessionId ? "active" : ""}`;
        btn.textContent = s.title || "New chat";
        btn.onclick = () => selectSession(s.id);
        li.appendChild(btn);
        list.appendChild(li);
    });
}

function renderMessages(streamingText = "") {
    const box = el("messages-list");
    box.innerHTML = "";

    if (state.messages.length === 0 && !streamingText) {
        box.innerHTML = `<p class="muted small" style="margin:auto;">Ask a question about ${state.currentRepo.fullName} to start chatting.</p>`;
        return;
    }

    const all = streamingText
        ? [...state.messages, { role: "ASSISTANT", content: streamingText + " ▍", citations: [] }]
        : state.messages;

    all.forEach((m) => {
        const div = document.createElement("div");
        div.className = `msg msg-${m.role}`;

        const bubble = document.createElement("div");
        bubble.className = "msg-bubble";
        bubble.textContent = m.content;
        div.appendChild(bubble);

        if (m.role === "ASSISTANT" && m.citations && m.citations.length > 0) {
            const cites = document.createElement("div");
            cites.className = "citations";
            m.citations.forEach((c) => {
                if (!c.filePath) return;
                const a = document.createElement("a");
                a.className = "citation-link";
                const line = c.startLine ? `#L${c.startLine}` : "";
                a.href = `https://github.com/${state.currentRepo.fullName}/blob/${state.currentRepo.defaultBranch}/${c.filePath}${line}`;
                a.target = "_blank";
                a.rel = "noopener noreferrer";
                a.textContent = `${c.filePath}${c.startLine ? ":" + c.startLine : ""} ↗`;
                cites.appendChild(a);
            });
            div.appendChild(cites);
        }
        box.appendChild(div);
    });

    box.scrollTop = box.scrollHeight;
}

async function sendChatMessage(e) {
    e.preventDefault();
    const content = el("chat-input").value.trim();
    if (!content || state.streaming || !state.currentSessionId) return;

    el("chat-input").value = "";
    el("chat-error").classList.add("hidden");

    const tempId = "temp-" + Date.now();
    state.messages.push({ id: tempId, role: "USER", content });
    let streamText = "";
    renderMessages("Thinking...");

    state.streaming = true;
    state.abortController = new AbortController();
    el("send-btn").disabled = true;
    el("stop-btn").classList.remove("hidden");

    try {
        await Api.streamMessage(state.currentSessionId, content, {
            signal: state.abortController.signal,
            onUserMessage: (msg) => {
                state.messages = state.messages.map((m) => (m.id === tempId ? msg : m));
                renderMessages(streamText || "Thinking...");
            },
            onToken: (token) => {
                streamText += token;
                renderMessages(streamText);
            },
            onAssistantMessage: (msg) => {
                streamText = "";
                state.messages.push(msg);
                renderMessages();
            }
        });
    } catch (err) {
        if (err.name !== "AbortError") {
            state.messages = state.messages.filter((m) => m.id !== tempId);
            el("chat-input").value = content;
            el("chat-error").textContent = `Message failed: ${err.message}`;
            el("chat-error").classList.remove("hidden");
        }
        renderMessages();
    } finally {
        state.streaming = false;
        state.abortController = null;
        el("send-btn").disabled = false;
        el("stop-btn").classList.add("hidden");
    }
}

// ============================================================================
// 5. Event Listeners & Startup
// ============================================================================

document.addEventListener("DOMContentLoaded", async () => {
    el("brand-link").onclick = (e) => { e.preventDefault(); if (Auth.user) loadDashboard(false); };
    el("alert-close").onclick = () => el("alert-banner").classList.add("hidden");
    el("login-btn").onclick = () => Auth.loginWithGithub();
    el("logout-btn").onclick = async () => { await Auth.logout(); showLogin(); };

    el("sync-btn").onclick = () => loadDashboard(true);
    el("retry-repos-btn").onclick = () => loadDashboard(false);
    el("search-input").oninput = renderRepos;
    el("status-filter").onchange = renderRepos;

    el("back-btn").onclick = () => { loadDashboard(false); };
    el("start-index-btn").onclick = () => triggerIndexing(state.currentRepo.id);
    el("reindex-btn").onclick = () => triggerIndexing(state.currentRepo.id);
    el("new-session-btn").onclick = async () => {
        const s = await Api.createSession(state.currentRepo.id, "New chat");
        state.sessions.unshift(s);
        selectSession(s.id);
    };

    el("chat-form").onsubmit = sendChatMessage;
    el("chat-input").onkeydown = (e) => {
        if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendChatMessage(e); }
    };
    el("stop-btn").onclick = () => state.abortController?.abort();

    // Check OAuth URL params and active session
    const { error, isCallback } = Auth.checkUrlParams();
    if (error) return showLogin("GitHub sign-in failed. Check your OAuth credentials.");
    if (isCallback) el("loading-text").textContent = "Finishing GitHub sign-in...";

    try {
        const user = await Auth.fetchCurrentUser();
        if (!user) return showLogin();
        renderUserHeader(user);
        await loadDashboard(false);
    } catch (err) {
        showLogin(`Could not connect to backend: ${err.message}`);
    }
});
