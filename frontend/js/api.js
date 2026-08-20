// Shared fetch wrapper - no framework, plain JS, no arrow functions (cohort syntax rule).
// Change this if your backend runs on a different host/port.
const API_BASE = "http://localhost:8080";

async function apiGet(path) {
    const res = await fetch(API_BASE + path);
    if (!res.ok) {
        let errorMessage = res.statusText;
        try {
            const err = await res.json();
            if (err.error) {
                errorMessage = err.error;
            }
        } catch (parseError) {
            // response wasn't JSON - keep the default statusText message
        }
        throw new Error(errorMessage);
    }
    return res.json();
}

async function apiPost(path, body) {
    const res = await fetch(API_BASE + path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
    if (!res.ok) {
        let errorMessage = res.statusText;
        try {
            const err = await res.json();
            if (err.error) {
                errorMessage = err.error;
            }
        } catch (parseError) {
            // response wasn't JSON - keep the default statusText message
        }
        throw new Error(errorMessage);
    }
    return res.json();
}

async function apiDelete(path) {
    const res = await fetch(API_BASE + path, { method: "DELETE" });
    if (!res.ok && res.status !== 204) {
        let errorMessage = res.statusText;
        try {
            const err = await res.json();
            if (err.error) {
                errorMessage = err.error;
            }
        } catch (parseError) {
            // response wasn't JSON - keep the default statusText message
        }
        throw new Error(errorMessage);
    }
}

function formatMoney(value) {
    const n = Number(value);
    if (n >= 0) {
        return "£" + n.toFixed(2);
    }
    return "-£" + Math.abs(n).toFixed(2);
}

function formatPercent(value) {
    const n = Number(value);
    if (n >= 0) {
        return "+" + n.toFixed(2) + "%";
    }
    return n.toFixed(2) + "%";
}
