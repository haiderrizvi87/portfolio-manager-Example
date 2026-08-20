// FR4 - browse portfolio (aggregated per-asset view, spec Section 3.3). No arrow functions (cohort syntax rule).

async function loadPortfolio() {
    var errorEl = document.getElementById("loadError");
    var table = document.getElementById("holdingsTable");
    var body = document.getElementById("holdingsBody");
    errorEl.textContent = "";

    try {
        var holdings = await apiGet("/holdings");
        if (holdings.length === 0) {
            errorEl.textContent = "No holdings recorded yet. Search for an asset to get started.";
            return;
        }

        var html = "";
        for (var i = 0; i < holdings.length; i = i + 1) {
            var h = holdings[i];
            var plClass = "negative";
            if (h.profitLoss >= 0) {
                plClass = "positive";
            }
            html = html + "<tr>";
            html = html + "<td><a href=\"performance.html?assetId=" + h.assetId + "&ticker=" + h.ticker + "\">" + h.ticker + "</a></td>";
            html = html + "<td>" + h.assetType + "</td>";
            html = html + "<td>" + h.totalQuantity + "</td>";
            html = html + "<td>" + formatMoney(h.avgPricePaid) + "</td>";
            html = html + "<td>" + formatMoney(h.currentPrice) + "</td>";
            html = html + "<td>" + formatMoney(h.currentValue) + "</td>";
            html = html + "<td class=\"" + plClass + "\">" + formatMoney(h.profitLoss) + " (" + formatPercent(h.profitLossPercent) + ")</td>";
            html = html + "<td><a href=\"performance.html?assetId=" + h.assetId + "&ticker=" + h.ticker + "\">Manage / Remove</a></td>";
            html = html + "</tr>";
        }
        body.innerHTML = html;
        table.style.display = "table";
    } catch (e) {
        errorEl.textContent = "Failed to load portfolio: " + e.message;
    }
}
// FR5 - deleting individual acquisitions happens on the Asset Performance page (performance.html),
// since a single asset row here can represent multiple underlying holdings (Section 3.3) -
// each with its own Holding id needed for DELETE /holdings/{id}.

loadPortfolio();
