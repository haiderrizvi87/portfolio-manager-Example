// FR7 - combined portfolio performance: bar chart, one bar per asset (spec Section 3.3, Section 6 rationale).
// Reuses GET /holdings (same aggregated data as the FR4 browse view - spec Section 3.4).
// No arrow functions (cohort syntax rule).

async function loadSummary() {
    var errorEl = document.getElementById("summaryError");
    try {
        var holdings = await apiGet("/holdings");
        if (!holdings || holdings.length === 0) {
            errorEl.textContent = "No holdings recorded yet.";
            return;
        }

        var labels = [];
        var values = [];
        for (var i = 0; i < holdings.length; i = i + 1) {
            labels.push(holdings[i].ticker);
            values.push(Number(holdings[i].profitLoss));
        }
        drawBarChart(document.getElementById("summaryChart"), labels, values);
    } catch (e) {
        errorEl.textContent = "Failed to load summary: " + e.message;
    }
}

loadSummary();
